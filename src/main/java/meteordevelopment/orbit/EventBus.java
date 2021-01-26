package meteordevelopment.orbit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages event listeners.
 */
public class EventBus {
    private final Map<Class<?>, List<Listener>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Listener>> listenerMap = new ConcurrentHashMap<>();

    /**
     * Posts an event to all subscribed event listeners.
     * @param event Event to post
     * @return Event passed in
     */
    public <T> T post(T event) {
        List<Listener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            for (Listener listener : listeners) listener.call(event);
        }

        return event;
    }

    /**
     * Posts a cancellable event to all subscribed event listeners. Stops after the event was cancelled.
     * @param event Event to post
     * @return Event passed in
     */
    public <T extends ICancellable> T post(T event) {
        List<Listener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            event.setCancelled(false);

            for (Listener listener : listeners) {
                listener.call(event);
                if (event.isCancelled()) break;
            }
        }

        return event;
    }

    /**
     * Finds all correct (static and non-static) methods with {@link EventHandler} annotation and subscribes them.
     * @param object The object to scan for methods
     */
    public void subscribe(Object object) {
        subscribe(getListeners(object.getClass(), object), false);
    }

    /**
     * Finds all correct (static only) methods with {@link EventHandler} annotation and subscribes them.
     * @param klass The class to scan for methods
     */
    public void subscribe(Class<?> klass) {
        subscribe(getListeners(klass, null), true);
    }

    private void subscribe(List<Listener> listeners, boolean onlyStatic) {
        for (Listener listener : listeners) {
            if (onlyStatic) {
                if (listener.isStatic) insert(listenerMap.computeIfAbsent(listener.target, aClass -> new ArrayList<>()), listener);
            }
            else {
                insert(listenerMap.computeIfAbsent(listener.target, aClass -> new ArrayList<>()), listener);
            }
        }
    }

    private synchronized void insert(List<Listener> listeners, Listener listener) {
        int i = 0;
        for (; i < listeners.size(); i++) {
            if (listener.priority > listeners.get(i).priority) break;
        }

        listeners.add(i, listener);
    }

    /**
     * Finds all correct (static and non-static) methods with {@link EventHandler} annotation and unsubscribes them.
     * @param object The object to scan for methods
     */
    public void unsubscribe(Object object) {
        unsubscribe(getListeners(object.getClass(), object), false);
    }

    /**
     * Finds all correct (static only) methods with {@link EventHandler} annotation and unsubscribes them.
     * @param klass The class to scan for methods
     */
    public void unsubscribe(Class<?> klass) {
        unsubscribe(getListeners(klass, null), true);
    }

    private synchronized void unsubscribe(List<Listener> listeners, boolean staticOnly) {
        for (Listener listener : listeners) {
            List<Listener> l = listenerMap.get(listener.target);

            if (l != null) {
                if (staticOnly) {
                    if (listener.isStatic) l.remove(listener);
                }
                else l.remove(listener);
            }
        }
    }

    private List<Listener> getListeners(Class<?> klass, Object object) {
        return listenerCache.computeIfAbsent(klass, aClass -> {
            List<Listener> listeners = new ArrayList<>();

            for (Method method : klass.getDeclaredMethods()) {
                if (isValid(method)) {
                    listeners.add(new Listener(klass, object, method));
                }
            }

            return listeners;
        });
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventHandler.class)) return false;
        if (method.getReturnType() != void.class) return false;
        if (method.getParameterCount() != 1) return false;

        return !method.getParameters()[0].getType().isPrimitive();
    }
}
