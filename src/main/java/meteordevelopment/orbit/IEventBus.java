package meteordevelopment.orbit;

import meteordevelopment.orbit.listeners.IListener;

import java.lang.invoke.MethodHandles;

/**
 * Manages event listeners.
 */
public interface IEventBus {
    /**
     * Registers a lookup allowing orbit to reflect into private members inside the provided package. You can obtain a
     * lookup instance by calling {@link MethodHandles#lookup()}.
     * @param packagePrefix Package prefix that this factory will be used for, eg "meteordevelopment.orbit"
     * @param lookup The lookup to use.
     */
    void registerLookup(String packagePrefix, MethodHandles.Lookup lookup);

    /**
     * Returns whether at least one event listener is currently registered for this event type.
     * @param eventClass The event type to check for registered listeners.
     * @return whether the event is being listened for
     * @since 0.2.4
     */
    boolean isListening(Class<?> eventClass);

    /**
     * Returns whether the object is currently subscribed to the event bus.
     * @param object The object to query
     * @return whether the object is currently subscribed to the event bus
     * @since 0.3.0
     */
    boolean isSubscribed(Object object);

    /**
     * Returns whether the class is currently subscribed to the event bus.
     * @param klass The class to query
     * @return whether the class is currently subscribed to the event bus
     * @since 0.3.0
     */
    boolean isSubscribed(Class<?> klass);

    /**
     * Returns whether the listener is currently subscribed to the event bus.
     * @param listener The listener to query
     * @return whether the listener is currently subscribed to the event bus
     * @since 0.3.0
     */
    boolean isSubscribed(IListener listener);

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
