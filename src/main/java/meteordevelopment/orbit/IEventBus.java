package meteordevelopment.orbit;

import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.orbit.listeners.LambdaListener;

/**
 * Manages event listeners.
 */
public interface IEventBus {
    /**
     * Registers a lambda factory to use with the specified package.
     * @param packagePrefix Package prefix that this factory will be used for, eg "meteordevelopment.orbit"
     * @param factory The factory to use
     */
    void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory);

    /**
     * Returns whether at least one event listener is currently registered for this event type.
     * @param eventClass The event type to check for registered listeners.
     * @return whether the event is being listened for
     */
    boolean isListening(Class<?> eventClass);

    /**
     * Posts an event to all subscribed event listeners.
     * @param event Event to post
     * @param <T> Type of the event
     * @return Event passed in
     */
    <T> T post(T event);

    /**
     * Posts a cancellable event to all subscribed event listeners. Stops after the event was cancelled.
     * @param event Event to post
     * @param <T> Type of the event
     * @return Event passed in
     */
    <T extends ICancellable> T post(T event);

    /**
     * Finds all correct (static and non-static) methods with {@link EventHandler} annotation and subscribes them.
     * @param object The object to scan for methods
     */
    void subscribe(Object object);

    /**
     * Finds all correct (static only) methods with {@link EventHandler} annotation and subscribes them.
     * @param klass The class to scan for methods
     */
    void subscribe(Class<?> klass);

    /**
     * Subscribes the listener (both static and non-static).
     * @param listener Listener to subscribe
     */
    void subscribe(IListener listener);

    /**
     * Finds all correct (static and non-static) methods with {@link EventHandler} annotation and unsubscribes them.
     * @param object The object to scan for methods
     */
    void unsubscribe(Object object);

    /**
     * Finds all correct (static only) methods with {@link EventHandler} annotation and unsubscribes them.
     * @param klass The class to scan for methods
     */
    void unsubscribe(Class<?> klass);

    /**
     * Unsubscribes the listener (both static and non-static).
     * @param listener Listener to unsubscribe
     */
    void unsubscribe(IListener listener);
}
