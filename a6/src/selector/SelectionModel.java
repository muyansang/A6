package selector;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.event.SwingPropertyChangeSupport;

/**
 * Represents the process of selecting a region of an image by adding control points that are used
 * to extend a selection path.  Supports removing points (and any segments they defined) in the
 * opposite order that they were added and moving points after the selection has been completed.
 */
public abstract class SelectionModel {

    /**
     * Declares capabilities that an object representing the "state" of selection progress must be
     * able to support.
     */
    public interface SelectionState {

        /**
         * Return true if we are at the start of the selection process and no control points have
         * been defined.
         */
        boolean isEmpty();

        /**
         * Return true if the selection has been completed, in which case no more points can be
         * added, but the existing points can be moved.
         */
        boolean isFinished();

        /**
         * Return whether it is legal to invoke `undo()` in this state in order to remove the
         * control point that was most recently added.
         */
        boolean canUndo();

        /**
         * Return whether it is legal to invoke `addPoint()` in this state in order to extend the
         * selection.
         */
        boolean canAddPoint();

        /**
         * Return whether it is legal to invoke `finishSelection()` in this state in order to close
         * the selection by connecting it back to its start.
         */
        boolean canFinish();

        /**
         * Return whether it is legal to invoke `movePoint()` in this state in order to modify the
         * location of an existing control point.
         */
        boolean canEdit();

        /**
         * Return whether this is a transient state that is performing work in the background.
         * Typically the only legal operations in such a state are `cancelProcessing()` and
         * `reset()`.
         */
        boolean isProcessing();
    }

    /**
     * The sequence of segments representing the current selection path.  The start point of each
     * segment must equal the end point of the previous segment (ensuring continuity).  If our state
     * is "finished," then this list must be non-empty, and the end point of the last segment must
     * equal the start point of the first segment.  Whenever the contents of this list change, a
     * "selection" property change event must be fired.
     */
    protected LinkedList<PolyLine> segments;

    /**
     * The sequence of control points used to define this selection, in the order in which they were
     * added.  All points are "semantically distinct"; for example, if the selection is finished,
     * the end point will NOT be included in this list if it is the same as the starting point
     * (which is usually the case).
     */
    protected LinkedList<Point> controlPoints;

    /**
     * The image we are selecting from (may be null, in which case no operations should be attempted
     * until the image has been set).
     */
    protected BufferedImage img;

    /**
     * Helper object for managing property change notifications.
     */
    protected SwingPropertyChangeSupport propSupport;


    /**
     * If `notifyOnEdt` is true, property change listeners will be notified on Swing's Event
     * Dispatch thread, regardless of which thread the event was fired from.  It should generally be
     * set to "true" when this model will be used with a GUI, and "false" when unit testing.  The
     * image will initially be set to null.
     */
    protected SelectionModel(boolean notifyOnEdt) {
        //state = NO_SELECTION;
        segments = new LinkedList<>();
        controlPoints = new LinkedList<>();
        propSupport = new SwingPropertyChangeSupport(this, notifyOnEdt);
    }

    /**
     * Initialize this model with the same `segments`, `controlPoints`, and image as `copy`.  Does
     * NOT copy any listeners from `copy`.  This constructor must only be invoked on Swing's Event
     * Dispatch Thread, since `copy`'s state may be shared with a background processing thread.
     * <p>
     * It may not be possible for two different models to represent the same selection, in which
     * case a subclass constructor may reset its selection state.
     */
    protected SelectionModel(SelectionModel copy) {
        segments = new LinkedList<>(copy.segments);
        controlPoints = new LinkedList<>(copy.controlPoints);
        img = copy.img;
        propSupport = new SwingPropertyChangeSupport(this, copy.propSupport.isNotifyOnEDT());
    }

    /* Client interface */

    /**
     * Return the status of this model's current selection.
     */
    public abstract SelectionState state();

    /**
     * Return the sequence of poly-line segments forming the current selection path.  The returned
     * list is not modifiable, but it will reflect subsequent changes made to this model.
     */
    public List<PolyLine> selection() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * Return the sequence of control points used to define the current selection.  The returned
     * list is not modifiable, but it will reflect subsequent changes made to this model.  Clients
     * must not mutate the constituent Points.
     */
    public List<Point> controlPoints() {
        // Note rep exposure due to mutable elements - clients assume responsibility for not
        //  modifying them.
        return Collections.unmodifiableList(controlPoints);
    }

    /**
     * Return the image we are currently selecting from.
     */
    public BufferedImage image() {
        return img;
    }

    /**
     * Select from `newImg` instead of any previous set image.  Resets the selection.  Notifies
     * listeners that the "image" property has changed.
     */
    public void setImage(BufferedImage newImg) {
        BufferedImage oldImg = img;
        img = newImg;
        reset();
        propSupport.firePropertyChange("image", oldImg, img);
    }

    /**
     * If no selection has been started, start selecting from `p`.  Otherwise, if a selection is in
     * progress, append a segment from its last point to point `p`.  Subclasses determine the path
     * of the new segment.  Listeners will be notified if the "state" or "selection" properties are
     * changed.
     */
    public void addPoint(Point p) {
        if (state().isEmpty()) {
            startSelection(p);
        } else if (state().canAddPoint()) {
            // Defer to our subclass to append a segment ending at `p` to our selection.
            appendToSelection(p);

            // Notify observers that the selection has changed.  There is no reason to include an
            //  old value, but we do include an unmodifiable copy of the current selection as the
            //  new value.
            propSupport.firePropertyChange("selection", null, selection());
        } else {
            throw new IllegalStateException("Cannot add point in state " + state());
        }
    }

    /**
     * Return the last control point added to the current selection.  If no segments have been added
     * to the selection yet, or if the selection has finished, this will be the starting point.
     * Throws an `IllegalStateException` if our state is empty.
     */
    public Point lastPoint() {
        if (state().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot query last point when not selection has been started");
        }

        Point returnPoint;

        if (state().isFinished()){
            returnPoint = new Point(controlPoints.peekFirst());
        } else {
            returnPoint = new Point(controlPoints.peekLast());
        }
        return returnPoint;
    }

    /**
     * Return a path representing a preview of how the selection would be extended if `p` were to be
     * added with `addPoint()`.  In many cases, this will be a path connecting our last point to
     * `p`, but this may not make sense in all cases (for example, `p` might be added as a "control
     * point" that does not lie on the path, in which case the "live wire" may be a straight segment
     * to the control point, or it may be a preview of how the path will be affected by the control
     * point).  Subclasses should refine this specification.
     */
    public abstract PolyLine liveWire(Point p);

    /**
     * If we are still processing the most recently added point, cancel that operation.  Otherwise,
     * remove the last segment from the selection path.  If the selection path does not contain any
     * segments, reset the selection to clear our starting point.  Listeners will be notified if the
     * "state" or "selection" properties are changed.  Removal of a point other than the start may
     * require asynchronous processing.
     */
    public void undo() {
        if (state().isProcessing()) {
            cancelProcessing();
        } else {
            undoPoint();
        }
    }

    /**
     * Close the current selection path by connecting the last segment to the starting point and
     * transitioning to a "finished" state.  If no segments have been added yet, reset this
     * selection instead.  Listeners will be notified if the "state" or "selection" properties are
     * changed.  Throws an `IllegalStateException` if the selection is already finished or cannot be
     * finished.
     */
    public abstract void finishSelection();

    /**
     * Clear the current selection path and any starting point.  Listeners will be notified if the
     * "state" or "selection" properties are changed.  Subclasses should override this method in
     * order to transition to an empty state.
     */
    public void reset() {
        controlPoints.clear();
        segments.clear();
        propSupport.firePropertyChange("selection", null, selection());
    }

    /**
     * Return the index of this model's control point that is the closest to `p`, as long as the
     * square of its distance to `p` is no greater than `maxDistanceSq`.  If no control point along
     * the selection is close enough, return -1.  If multiple points are tied for closest, any of
     * their indices may be returned.  Throws an IllegalStateException if our selection is not yet
     * finished.  Control point indices are zero-based and are consistent with the list returned by
     * `controlPoints()`.
     */
    public int closestPoint(Point p, int maxDistanceSq) {
        if (!state().canEdit()) {
            throw new IllegalStateException(
                    "Cannot query closest point when selection is incomplete");
        }

        for (Point i : controlPoints()){

            double xAxis = i.getX() - p.x;
            double yAxis = i.getY() - p.y;
            double distance = xAxis*xAxis + yAxis*yAxis;

            if (distance <= maxDistanceSq){
                return controlPoints().indexOf(i);
            }
        }
        return -1;
    }

    /**
     * Move the control point with index `index` to `newPos`, updating the path of any segments
     * affected by that control point.  Listeners will be notified that the "selection" property has
     * changed.  Control point indices are zero-based and are consistent with the list returned by
     * `controlPoints()`.
     */
    public abstract void movePoint(int index, Point newPos);

    /**
     * Write a PNG image to `out` containing the pixels from the current selection.  The size of the
     * image matches the bounding box of the selection, and pixels outside of the selection are
     * transparent.  Throws an IOException if the image could not be written.  Throws an
     * IllegalStateException if our selection is not finished.
     */
    public void saveSelection(OutputStream out) throws IOException {
        assert img != null;
        if (!state().canEdit()) {
            throw new IllegalStateException("Must complete selection before saving");
        }
        Polygon clip = PolyLine.makePolygon(segments);
        Rectangle bounds = clip.getBounds();
        clip.translate(-bounds.x, -bounds.y);
        BufferedImage dst = new BufferedImage(bounds.width, bounds.height,
                BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setClip(clip);
        g.drawImage(img, -bounds.x, -bounds.y, null);
        ImageIO.write(dst, "png", out);
    }

    /* Specialization interface */

    /**
     * When no selection has yet been started, set our first control point to `start` and transition
     * to the appropriate state.  Listeners will be notified that the "state" property has changed.
     * Throws an `IllegalStateException` if our state is not empty.
     * <p>
     * The default implementation checks that the state is empty and adds the control point.
     * Subclasses must override this method in order to perform the state transition and notify
     * listeners.
     */
    protected void startSelection(Point start) {
        if (!state().isEmpty()) {
            throw new IllegalStateException("Cannot start selection from state " + state());
        }
        controlPoints.add(new Point(start));
    }

    /**
     * Add `p` as the next control point of our selection, extending our selection with a new
     * segment if appropriate.  This may result in a state change.  Requires that our state allows
     * adding points.  Not responsible for notifying listeners that the selection has changed (this
     * must be handled subsequently by the caller).
     */
    protected abstract void appendToSelection(Point p);

    /**
     * Remove the last control point from our selection.  Listeners will be notified if the "state"
     * or "selection" properties are changed.
     */
    protected abstract void undoPoint();

    /* Observation interface */

    /**
     * Register `listener` to be notified whenever any property of this model is changed.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    /**
     * Register `listener` to be notified whenever the property named `propertyName` of this model
     * is changed.
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Stop notifying `listener` of property changes for this model (assuming it was added no more
     * than once).  Does not affect listeners who were registered with a particular property name.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    /**
     * Stop notifying `listener` of changes to the property named `propertyName` for this model
     * (assuming it was added no more than once).  Does not affect listeners who were not registered
     * with `propertyName`.
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(propertyName, listener);
    }

    /* Methods not used until A6 */

    /**
     * Cancel any asynchronous processing currently being performed on behalf of this model.
     */
    public void cancelProcessing() {
        assert state().isProcessing();
        // Default implementation does nothing
    }

    /**
     * Return an indication of the progress of any asynchronous processing currently being performed
     * on behalf of this model.  The type of object returned will depend on the subclass.  Returns
     * null if no asynchronous processing is currently being performed.
     */
    public Object getProcessingProgress() {
        return null;
    }
}
