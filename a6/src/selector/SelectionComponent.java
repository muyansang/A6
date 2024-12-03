package selector;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import java.awt.Rectangle;
import scissors.ImagePathsSnapshot;

/**
 * A transparent (overlay) component enabling interactive selection (aka "tracing") of an underlying
 * image.  Layout must ensure that our upper-left corner coincides with that of the underlying image
 * view.
 */
public class SelectionComponent extends JComponent implements MouseListener, MouseMotionListener,
        PropertyChangeListener {

    /**
     * The current selection model that we are viewing and controlling.
     */
    private SelectionModel model;

    /* Interaction state */

    /**
     * The index of the selection segment whose starting point is currently being interacted with,
     * or -1 if no control point is currently being manipulated.
     */
    private int selectedIndex;

    /**
     * The last observed position of the mouse pointer over this component, constrained to lie
     * within the image area.  Must not alias a Point from a MouseEvent, as those objects may be
     * reused by future events.
     */
    private Point mouseLocation = new Point();

    /* View parameters */

    /**
     * The radius of a control point, in pixels.  Used both for rendering and for tolerance when
     * selecting points with the mouse.
     */
    private int controlPointRadius = 4;

    /**
     * The color used to draw the current selection path.
     */
    private Color selectionPerimeterColor = Color.BLUE;

    /**
     * The color used to draw proposed segments that connect to the mouse pointer.
     */
    private Color liveWireColor = Color.YELLOW;

    /**
     * The color used to draw control points for a finished selection.
     */
    private Color controlPointColor = Color.CYAN;

    /**
     * Construct a new SelectionComponent that will participate in viewing and controlling the
     * selection modeled by `model`.  View will update upon receiving property change events from
     * `model`.
     */
    public SelectionComponent(SelectionModel model) {
        // Assign and listen to the provided model
        setModel(model);

        // Listen for mouse events that occur over us
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /**
     * Have this component view and control `newModel` instead of whichever model it was using
     * previously.  This component will no longer react to events from the old model.  If a point
     * from the previous selection was being moved, that interaction is discarded.
     */
    public void setModel(SelectionModel newModel) {
        // Implementer's note: this method is safe to call during construction.

        // Stop receiving updates from our current model
        // (null check makes this save to call from the constructor)
        if (model != null) {
            model.removePropertyChangeListener(this);
        }

        // Assign and listen to the new model
        model = newModel;
        model.addPropertyChangeListener(this);

        // Update our preferred size to match the image used by the new model
        if (model.image() != null) {
            setPreferredSize(new Dimension(model.image().getWidth(), model.image().getHeight()));
        }

        // If we were in the process of moving a point, reset that interaction, since the selected
        // index may not be valid in the new model
        selectedIndex = -1;

        // Model state has changed; update our view.
        repaint();
    }

    /**
     * Return the selection model currently being viewed and controlled by this component.
     */
    public SelectionModel getModel() {
        return model;
    }

    /**
     * Record `p` as the most recent mouse pointer location and update the view.  If `p` is outside
     * of our model's image area, clamp `p`'s coordinates to the nearest edge of the image area.
     * This method does not modify or save a reference to `p` (meaning the client is free to mutate
     * it after this method returns, which Swing will do with Points used by MouseEvents).
     */
    private void updateMouseLocation(Point p) {
        // Clamp `p`'s coordinates to be within the image bounds and save them in our field
        mouseLocation.x = Math.clamp(p.x, 0, model.image().getWidth() - 1);
        mouseLocation.y = Math.clamp(p.y, 0, model.image().getHeight() - 1);

        // Update the view to reflect the new mouse location
        repaint();
    }

    /**
     * Return whether we are currently interacting with a control point of a closed selection.
     */
    private boolean isInteractingWithPoint() {
        return model.state().canEdit() && selectedIndex != -1;
    }

    /**
     * Visualize our model's state, as well as our interaction state, by drawing our view using
     * `g`.
     */
    @Override
    public void paintComponent(Graphics g) {
        List<PolyLine> segments = model.selection();

        // Draw perimeter
        paintSelectionPerimeter(g, segments);

        // If dragging a point, draw guidelines
        if (isInteractingWithPoint() && mouseLocation != null) {
            paintMoveGuides(g, model.controlPoints());
        }

        // Draw live wire
        if (!model.state().isEmpty() && model.state().canAddPoint() && mouseLocation != null) {
            paintLiveWire(g);
        }

        // New in A6: Paint processing progress (if we recognize its type)
        if (model.state().isProcessing()) {
            Object progress = model.getProcessingProgress();
            if (progress instanceof ImagePathsSnapshot) {
                paintPathfindingProgress(g, (ImagePathsSnapshot) progress);
            }
        }

        // Draw handles
        paintControlPoints(g, model.controlPoints());
    }

    /**
     * Draw on `g` along the selection path represented by `segments` using our selection perimeter
     * color.
     */
    private void paintSelectionPerimeter(Graphics g, List<PolyLine> segments) {
        g.setColor(selectionPerimeterColor);
        for (PolyLine segment : segments){
            g.drawPolyline(segment.xs(), segment.ys(), segment.size());
        }
    }

    /**
     * Draw on `g` along our model's "live wire" path to our last-known mouse pointer location using
     * our live wire color.
     */
    private void paintLiveWire(Graphics g) {
        g.setColor(liveWireColor);

        PolyLine tempLine = model.liveWire(mouseLocation);

        if (tempLine != null) {
            g.drawPolyline(tempLine.xs(), tempLine.ys(), tempLine.size());
        }
    }

    /**
     * Draw filled circles on `g` centered at the control points in `points` using our control point
     * color. The circles' radius should be our control point radius.
     */
    private void paintControlPoints(Graphics g, List<Point> points) {
        for (Point i: points){
            g.setColor(controlPointColor);
            g.fillOval(i.x - controlPointRadius,
                    i.y - controlPointRadius,
                    controlPointRadius * 2,
                    controlPointRadius * 2);
        }
    }

    /**
     * Draw straight lines on `g` connecting our last-known mouse pointer location to the control
     * points before and after our selected point (indices wrapping around) using our live wire
     * color.  Requires `selectedIndex` is in [0..controlPoints.size()).
     */
    private void paintMoveGuides(Graphics g, List<Point> controlPoints) {
        if (controlPoints.isEmpty() || selectedIndex < 0 || selectedIndex >= controlPoints.size()) {
            throw new IllegalArgumentException();
        }

        g.setColor(liveWireColor);

        // When the selection model is Point-To-Point or Spline
        if (getModel() instanceof PointToPointSelectionModel || getModel() instanceof SplineSelectionModel) {

            int prevIndex = (selectedIndex == 0) ? controlPoints.size() - 1 : selectedIndex - 1;
            int nextIndex = (selectedIndex == controlPoints.size() - 1) ? 0 : selectedIndex + 1;

            // Setting up the previous point
            Point prev = controlPoints.get(prevIndex);
            int x1 = prev.x;
            int y1 = prev.y;
            int x2 = mouseLocation.x;
            int y2 = mouseLocation.y;

            // Setting up the next point of the segment
            Point next = controlPoints.get(nextIndex);
            int x3 = next.x;
            int y3 = next.y;
            int x4 = mouseLocation.x;
            int y4 = mouseLocation.y;

            g.drawLine(x1, y1, x2, y2);
            g.drawLine(x3, y3, x4, y4);
        }
        // When the selection model is circle (Implementation for Challanging extension)
        else if (getModel() instanceof CircleSelectionModel) {
            //When moving the control point on the radius
            if (selectedIndex == 1){
                paintLiveWire(g);
            }
            //When moving the entire circle using the center point
            else if (selectedIndex == 0){

                //Draw the live-radius
                Point prev = controlPoints.getFirst();
                int x1 = prev.x;
                int y1 = prev.y;
                int x2 = mouseLocation.x;
                int y2 = mouseLocation.y;
                g.drawLine(x1, y1, x2, y2);

                //Draw the live-circle
                final Point finalPoint = controlPoints.get(1);
                int radius = CircleSelectionModel.getRadius(finalPoint,controlPoints.getFirst());
                double angle = 2 * Math.PI/ CircleSelectionModel.smoothLevel;

                int[] xs = new int[CircleSelectionModel.smoothLevel];
                int[] ys = new int[CircleSelectionModel.smoothLevel];

                for (int i = 0; i < CircleSelectionModel.smoothLevel; i++) {
                        xs[i] = (int) (mouseLocation.x + radius * Math.cos(i * angle));
                        ys[i] = (int) (mouseLocation.y + radius * Math.sin(i * angle));
                }
                g.drawPolyline(xs,ys,CircleSelectionModel.smoothLevel);
            }
        }
    }

    /* Event listeners */

    /**
     * When mouse button 1 is clicked and a selection has either not yet been started or is still in
     * progress, add the location of the point to the selection.  Note: `mousePressed()` and
     * `mouseReleased()` handle presses of button 1 when the selection is finished.
     * <p>
     * When mouse button 2 is clicked and a selection is in progress, finish the selection.
     * <p>
     * When mouse button 3 is clicked and a selection is either in progress or finished, undo the
     * last point added to the selection.
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        updateMouseLocation(e.getPoint());

        if (e.getButton() == MouseEvent.BUTTON1){
            if (model.state().canAddPoint()){
                model.addPoint(mouseLocation);
            }
        } else if (e.getButton() == MouseEvent.BUTTON2){
            if (model.state().canFinish()){
                model.finishSelection();
            }
        } else if (e.getButton() == MouseEvent.BUTTON3){
            if (model.state().canUndo()){
                model.undoPoint();
            }
        }
        repaint();
    }

    /**
     * When a selection is in progress, update our last-observed mouse location to the location of
     * this event and repaint ourselves to draw a "live wire" to the mouse pointer.
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        if (model.state().canAddPoint()) {
            updateMouseLocation(e.getPoint());
        }
    }

    /**
     * When a selection is in progress, or when we are interacting with a control point, update our
     * last-observed mouse location to the location of this event and repaint ourselves to draw a
     * "live wire" to the mouse pointer.  (Note that mouseMoved events are not sent while dragging,
     * which is why this overlaps with the duties of that handler.)
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        if (model.state().canAddPoint() || isInteractingWithPoint()) {
            updateMouseLocation(e.getPoint());
        }
    }

    /**
     * When mouse button 1 is pressed while our model's selection is complete, search for a control
     * point close to the mouse pointer and, if found, start interacting with that point.  A point
     * is only eligible for interaction if the mouse was pressed within the circle representing it
     * in the view.
     */
    @Override
    public void mousePressed(MouseEvent e) {
        if (model.state().isFinished()){
            Point mousePoint = e.getPoint();

            int closePoint = model.closestPoint(mousePoint,10);
            selectedIndex = closePoint;
        }
    }

    /**
     * When mouse button 1 is released while we are interacting with a control point, move the
     * selected point to the current mouse location.
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && isInteractingWithPoint()) {
            model.movePoint(selectedIndex, mouseLocation);
            // No need to call `repaint()` ourselves, since moving the point will trigger a property
            // change, which will then trigger a repaint when we observe it.

            // Stop interacting with the point
            selectedIndex = -1;
        }
    }

    /**
     * Repaint to update our view in response to any property changes from our model.  Additionally,
     * if the "image" property changed, update our preferred size to match the new image size.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        // If model image changed, update preferred size
        if (e.getPropertyName().equals("image") && e.getNewValue() != null) {
            BufferedImage img = (BufferedImage) e.getNewValue();
            setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
        }

        // If the model's selection changed while we are interacting with a control point, cancel
        // that interaction (since our selected index may no longer be valid).
        if (e.getPropertyName().equals("selection")) {
            selectedIndex = -1;
        }

        // If any property of the model changed, repaint to update view
        repaint();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Ignored
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Ignored
    }

    // New in A6
    /**
     * Shade image pixels according to their current path search status (settled, frontier, or
     * undiscovered).  Do nothing if there is no pending path solution.
     */
    private void paintPathfindingProgress(Graphics g, ImagePathsSnapshot pendingPaths) {
        int settledColor = new Color(192, 192, 96, 128).getRGB();
        int frontierColor = new Color(96, 96, 192, 128).getRGB();

        Rectangle bounds = g.getClipBounds();
        int width = Math.min(bounds.width, model.image().getWidth() - bounds.x);
        int height = Math.min(bounds.height, model.image().getHeight() - bounds.y);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = bounds.x; x < bounds.x + width; ++x) {
            for (int y = bounds.y; y < bounds.y + height; ++y) {
                Point p = new Point(x, y);
                if (pendingPaths.settled(p)) {
                    img.setRGB(x - bounds.x, y - bounds.y, settledColor);
                } else if (pendingPaths.discovered(p)) {
                    img.setRGB(x - bounds.x, y - bounds.y, frontierColor);
                }
            }
        }
        g.drawImage(img, bounds.x, bounds.y, null);
    }
}
