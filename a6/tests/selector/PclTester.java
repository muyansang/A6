package selector;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helper class for testing whether a class-under-test properly notifies its observers of
 * PropertyChangeEvents.  Add an instance of this class as a listener to the object-under-test,
 * invoke that object's behavior, then use this class's methods to check for the expected event(s).
 */
public class PclTester implements PropertyChangeListener {

    /**
     * List of events that we have observed.
     */
    List<PropertyChangeEvent> observedEvents = new LinkedList<>();

    /**
     * Append `evt` to our list of observed events.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        observedEvents.add(evt);
    }

    /**
     * Assert that we have not been notified of any property change events.
     */
    public void assertNoChanges() {
        assertTrue(observedEvents.isEmpty());
    }

    /**
     * Assert that a property change event was received for the property named `propertyName`.
     */
    public void assertChanged(String propertyName) {
        // This is a more advanced used of lambda expressions.  It's okay if you don't understand
        //  this implementation (you only need to understand the spec to make use of this helper);
        //  you can learn more about this style of coding in CS 3110.
        assertTrue(observedEvents.stream().anyMatch(e -> propertyName.equals(e.getPropertyName())));
    }

    /**
     * Assert that a property change event was received for the property named `propertyName` and
     * that its new value was `newValue`.
     */
    public void assertChangedTo(String propertyName, Object newValue) {
        assertTrue(observedEvents.stream().anyMatch(
                e -> propertyName.equals(e.getPropertyName()) && newValue.equals(e.getNewValue())));
    }

    /**
     * Assert that a property change event was not received for the property named `propertyName`.
     */
    public void assertNotChanged(String propertyName) {
        assertFalse(
                observedEvents.stream().anyMatch(e -> propertyName.equals(e.getPropertyName())));
    }
}