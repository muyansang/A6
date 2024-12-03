package selector;

import javax.swing.event.ChangeListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * Models a selection tool that creats a circled from a selected center point with a client-defined radius
 * through the second control points
 */
public class CircleSelectionModel extends SelectionModel {

    /**
     * Implementing the selection state for CircleSelectionModel from the Interface SelectionState
     */
    enum CircleSelectionState implements SelectionState {
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

        /**
         * Check whether if selection path is empty
         */
        @Override
        public boolean isEmpty() {
            return this == NO_SELECTION;
        }

        /**
         * Check whether if selection path is done selection
         */
        @Override
        public boolean isFinished() {
            return this == SELECTED;
        }

        /**
         * Check whether if selection path can be undone; which either in the state of selecting or selected
         */
        @Override
        public boolean canUndo() {
            return this == SELECTED || this == SELECTING;
        }

        /**
         * Check whether if selection path can add more points
         */
        @Override
        public boolean canAddPoint() {
            return this == NO_SELECTION || this == SELECTING;
        }

        /**
         * Check whether if selection path can be finished
         */
        @Override
        public boolean canFinish() {
            return this == SELECTING;
        }

        /**
         * Check whether if selection path can be edited
         */
        @Override
        public boolean canEdit() {
            return this == SELECTED;
        }

        /**
         * Check whether if selection path can be processed
         */
        @Override
        public boolean isProcessing() {
            return false;
        }
    }

    /**
     * The current state of this selection model.
     */
    private CircleSelectionState state;

    /**
     * The number of segements that construct the circle
     */
    protected static final int smoothLevel = 1000;

    /**
     * Create a model instance with no selection and no image.  If `notifyOnEdt` is true, property
     * change listeners will be notified on Swing's Event Dispatch thread, regardless of which
     * thread the event was fired from.
     */
    public CircleSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
        state = CircleSelectionState.NO_SELECTION;
    }

    /**
     * Create a model instance with the same image and event notification policy as `copy`, and
     * attempt to preserve `copy`'s selection if it can be represented without violating the
     * invariants of this class.
     */
    public CircleSelectionModel(SelectionModel copy) {
        super(copy);
        if (copy instanceof CircleSelectionModel) {
            state = ((CircleSelectionModel) copy).state;
        } else {
            if (copy.state().isEmpty()) {
                assert segments.isEmpty() && controlPoints.isEmpty();
                state = CircleSelectionState.NO_SELECTION;
            } else if (!copy.state().isFinished() && controlPoints.size() == segments.size() + 1) {
                // Assumes segments start and end at control points
                state = CircleSelectionState.SELECTING;
            } else if (copy.state().isFinished() && controlPoints.size() == segments.size()) {
                // Assumes segments start and end at control points
                state = CircleSelectionState.SELECTED;
            } else {
                reset();
            }
        }
    }

    /**
     * return the current state of the selection model
     */
    @Override
    public SelectionState state() {
        return state;
    }

    /**
     * Change our selection state to `newState` (internal operation).  This should only be used to
     * perform valid state transitions.  Notifies listeners that the "state" property has changed.
     */
    private void setState(CircleSelectionState newState) {
        CircleSelectionState oldState = state;
        state = newState;
        propSupport.firePropertyChange("state", oldState, newState);
    }

    /**
     * Return the circle with a center of first control point with a radius of distance between center and point 'p'.
     */
    @Override
    public PolyLine liveWire(Point p) {
        Point center = controlPoints.getFirst();
        if (center == null) {
            throw new IllegalStateException("Cannot draw live wire without center point.");
        }

        // Create the temporary circle
        int radius = (int) center.distance(p);
        double angle = 2 * Math.PI / smoothLevel;

        int[] xs = new int[smoothLevel + 1];

        int[] ys = new int[smoothLevel + 1];

        xs[0] = center.x;
        ys[0] = center.y;

        for (int i = 0; i < smoothLevel + 1; i++) {
            int x = (int) (center.x + radius * Math.cos(i * angle));
            int y = (int) (center.y + radius * Math.sin(i * angle));

            xs[i] = x;
            ys[i] = y;
        }
        return new PolyLine(xs,ys);
    }

    /**
     * Return a list of Polyline that represent a closed circle with a center of 'center'
     * and a radius of distance between 'center' and 'p'. Requires 'center' and 'p' not be null.
     */
    private List<PolyLine> theCircle(Point p,Point center){
        assert (p != null || center != null);

        int radius = getRadius(p, center);
        double angle = 2 * Math.PI/ smoothLevel;

        List<PolyLine> circleSegments = new ArrayList<>();

        for (int i = 0; i < smoothLevel; i++) {
            Point startPoint = new Point(
                    (int) (center.x + radius * Math.cos(i * angle)),
                    (int) (center.y + radius * Math.sin(i * angle))
            );

            Point nextPoint = new Point(
                    (int) (center.x + radius * Math.cos((i + 1) * angle)),
                    (int) (center.y + radius * Math.sin((i + 1) * angle))
            );
            circleSegments.add(new PolyLine(startPoint, nextPoint));
        }
        return circleSegments;
    }

    /**
     * Adds the point `p` as the next control point in the selection path.
     * This may result in a state change. Requires that our state allows adding points.
     * Updates the selection to `SELECTED` if two points are added. Creates
     * a full circle if the second control point was placed by invoking 'theCircle()'
     * */
    protected void appendToSelection(Point p) {
        if (controlPoints.isEmpty()) {
            throw new IllegalStateException("There's no starting point in the selection path.");
        }

        if (controlPoints.size() >= 2) {
            throw new IllegalStateException("Only two control points are allowed.");
        }
        controlPoints.add(new Point(p));

        if (controlPoints.size() == 2 && state.canAddPoint()) {
            List<PolyLine> circleSegments = theCircle(p,controlPoints.getFirst());
            segments.addAll(circleSegments);
            setState(CircleSelectionState.SELECTED);
        }
    }

    /**
     * Returns the distance between point 'p' and 'center'
     * */
    protected static int getRadius(Point p,Point center){
        return (int) center.distance(p);
    }

    /**
     * Moves the control point at 'index' and move it to the position of 'newPos';
     * If moving the center point, the entire circle will be moved with the center point
     * If moving the point that define the radius (second control point), the center remains
     * in the same position and the second control point will move to 'newPos'. Basically modifying
     * constructed radius.
     * */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (!state().canEdit()) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= controlPoints.size()) {
            throw new IllegalArgumentException("Invalid point index " + index);
        }

        Point newPoint = new Point(newPos.x, newPos.y);

        if (index == 0){
            // Get the transformation factor for each x and y
            int xTransformation = newPos.x - controlPoints.getFirst().x;
            int yTransformation = newPos.y - controlPoints.getFirst().y;

            int transformedRadiusX = controlPoints().get(1).x + xTransformation;
            int transformedRadiusY = controlPoints().get(1).y + yTransformation;
            Point transformedRadius = new Point(transformedRadiusX,transformedRadiusY);
            controlPoints.set(1,transformedRadius);

            List<PolyLine> circleSegments = theCircle(new Point(transformedRadiusX,
                            transformedRadiusY), newPos);
            segments.clear();
            segments.addAll(circleSegments);

        } else{
            segments.clear();
            List<PolyLine> circleSegments = theCircle(newPos, controlPoints.getFirst());
            segments.addAll(circleSegments);
        }
        controlPoints.set(index, newPoint);
        propSupport.firePropertyChange("selection", null,selection());
    }

    /**
     * Completes the selection process. Sets the selection state to `SELECTED`
     * if segments are present; otherwise, resets the selection.
     * */
    public void finishSelection() {
        if (!state.canFinish()) {
            throw new IllegalStateException("Cannot finish a selection that is already finished");
        }
        if (segments.isEmpty()) {
            reset();
        } else {
            setState(CircleSelectionState.SELECTED);
        }
    }

    /**
     * Clear the current selection path and any starting point. Listeners will be notified if the
     * "state" or "selection" properties are changed.
     */
    @Override
    public void reset() {
        super.reset();
        setState(CircleSelectionState.NO_SELECTION);
    }

    /**
     * When no selection has yet been started, set our first control point to `start` and transition
     * to the appropriate state.  Listeners will be notified that the "state" property has changed.
     */
    @Override
    protected void startSelection(Point start) {
        super.startSelection(start);
        setState(CircleSelectionState.SELECTING);
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
            if (state().isFinished()){
                setState(CircleSelectionState.SELECTING);
            }
            segments.clear();
            controlPoints.removeLast();

            propSupport.firePropertyChange("selection", null, selection());
        }
    }
}