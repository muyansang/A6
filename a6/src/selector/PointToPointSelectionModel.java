package selector;

import javax.swing.event.ChangeListener;
import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Models a selection tool that connects each added point with a straight line.
 */
public class PointToPointSelectionModel extends SelectionModel {

    /**
     * Declares capabilities that an object representing the "state" of selection progress must be
     * able to support.
     */
    enum PointToPointState implements SelectionState {
        /**
         * No selection is currently in progress (no starting point has been selected).
         */
        NO_SELECTION,

        /**
         * Currently assembling a selection.  A starting point has been selected, and the selection
         * path may contain a sequence of segments, which can be appended to by adding points.
         */
        SELECTING,

        /**
         * The selection path represents a closed selection that start and ends at the same point.
         * Points may be moved, but no additional points may be added.  The selected region of the
         * image may be extracted and saved from this state.
         */
        SELECTED;

        @Override
        public boolean isEmpty() {
            return this == NO_SELECTION;
        }

        @Override
        public boolean isFinished() {
            return this == SELECTED;
        }

        @Override
        public boolean canUndo() {
            return this == SELECTED || this == SELECTING;
        }

        @Override
        public boolean canAddPoint() {
            return this == NO_SELECTION || this == SELECTING;
        }

        @Override
        public boolean canFinish() {
            return this == SELECTING;
        }

        @Override
        public boolean canEdit() {
            return this == SELECTED;
        }

        @Override
        public boolean isProcessing() {
            return false;
        }
    }

    /**
     * The current state of this selection model.
     */
    private PointToPointState state;

    /**
     * Create a model instance with no selection and no image.  If `notifyOnEdt` is true, property
     * change listeners will be notified on Swing's Event Dispatch thread, regardless of which
     * thread the event was fired from.
     */
    public PointToPointSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
        state = PointToPointState.NO_SELECTION;
    }

    /**
     * Create a model instance with the same image and event notification policy as `copy`, and
     * attempt to preserve `copy`'s selection if it can be represented without violating the
     * invariants of this class.
     */
    public PointToPointSelectionModel(SelectionModel copy) {
        super(copy);
        if (copy instanceof PointToPointSelectionModel) {
            state = ((PointToPointSelectionModel) copy).state;
        } else {
            if (copy.state().isEmpty()) {
                assert segments.isEmpty() && controlPoints.isEmpty();
                state = PointToPointState.NO_SELECTION;
            } else if (!copy.state().isFinished() && controlPoints.size() == segments.size() + 1) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTING;
            } else if (copy.state().isFinished() && controlPoints.size() == segments.size()) {
                // Assumes segments start and end at control points
                state = PointToPointState.SELECTED;
            } else {
                reset();
            }
        }
    }

    @Override
    public SelectionState state() {
        return state;
    }

    /**
     * Change our selection state to `newState` (internal operation).  This should only be used to
     * perform valid state transitions.  Notifies listeners that the "state" property has changed.
     */
    private void setState(PointToPointState newState) {
        PointToPointState oldState = state;
        state = newState;
        propSupport.firePropertyChange("state", oldState, newState);
    }

    /**
     * Return a straight line segment from our last point to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        Point lastPoint = new Point(controlPoints.peekLast());
        PolyLine newLine = new PolyLine(lastPoint, new Point(p));
        return newLine;
    }

    /**
     * Add `p` as the next control point of our selection, extending our selection with a straight
     * line segment from the end of the current selection path to `p`.
     */
    @Override
    protected void appendToSelection(Point p) {
        if (controlPoints.isEmpty()){
            throw new IllegalStateException("There's no starting point in the selection path.");
        }

        if (state.canAddPoint()) {
            Point lastPoint = new Point(controlPoints.peekLast());
            PolyLine newLine = new PolyLine(lastPoint, new Point(p));
            segments.add(newLine);
            controlPoints.add(new Point(p));
        }
    }

    /**
     * Move the control point with index `index` to `newPos`.  The segment that previously
     * terminated at the point should be replaced with a straight line connecting the previous point
     * to `newPos`, and the segment that previously started from the point should be replaced with a
     * straight line connecting `newPos` to the next point (where "next" and "previous" wrap around
     * as necessary). Notify listeners that the "selection" property has changed.
     */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (!state().canEdit()) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= controlPoints.size()) {
            throw new IllegalArgumentException("Invalid point index " + index);
        }

        //Copy of the client-provided point
        Point newPoint = new Point(newPos.x, newPos.y);

        controlPoints.set(index, newPoint);

        int startSegmentIndex = (index == 0)? segments.size() - 1: index - 1;
        int endSegmentIndex = index;
        propSupport.firePropertyChange("selection", null,selection());

        PolyLine line1 = segments.get(startSegmentIndex);
        PolyLine line2 = segments.get(endSegmentIndex);
        segments.set(startSegmentIndex, new PolyLine(line1.start(), newPoint));
        segments.set(endSegmentIndex, new PolyLine(newPoint, line2.end()));
    }

    public void finishSelection() {
        if (!state.canFinish()) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (segments.isEmpty()) {
            reset();
        } else {
            addPoint(controlPoints.getFirst());
            // Don't double-add the starting point
            controlPoints.removeLast();
            setState(PointToPointState.SELECTED);
        }
    }

    @Override
    public void reset() {
        super.reset();
        setState(PointToPointState.NO_SELECTION);
    }

    @Override
    protected void startSelection(Point start) {
        super.startSelection(start);
        setState(PointToPointState.SELECTING);
    }

    /**
     * Remove the last control point from our selection.  Listeners will be notified if the "state"
     * or "selection" properties are changed.
     */
    @Override
    protected void undoPoint() {
        if (segments.isEmpty()) {
            // Reset to remove the starting point
            reset();
        } else {
            segments.removeLast();

            if (state().isFinished()){
                setState(PointToPointState.SELECTING);

            }else {
                if (!controlPoints.isEmpty()) {
                    controlPoints.removeLast();
                }
            }
            propSupport.firePropertyChange("selection", null, selection());
        }
    }
}
