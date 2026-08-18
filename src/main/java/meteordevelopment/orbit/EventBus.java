package meteordevelopment.orbit;

import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.orbit.listeners.LambdaListener;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Default implementation of {@link IEventBus}.
 */
public class EventBus implements IEventBus {
    private final Map<Object, List<IListener>> listenerCache = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();

    private final List<LookupInfo> lookupInfos = new ArrayList<>();

    @Override
    public void registerLookup(String packagePrefix, MethodHandles.Lookup lookup) {
        synchronized (lookupInfos) {
            lookupInfos.add(new LookupInfo(packagePrefix, lookup));
        }
    }

    @Override
    public boolean isListening(Class<?> eventKlass) {
        List<IListener> listeners = listenerMap.get(eventKlass);
        return listeners != null && !listeners.isEmpty();
    }

    @Override
    public <T> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            for (IListener listener : listeners) listener.call(event);
        }

        return event;
    }

    @Override
    public <T extends ICancellable> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            event.setCancelled(false);

            for (IListener listener : listeners) {
                listener.call(event);
                if (event.isCancelled()) break;
            }
        }

        return event;
    }

    @Override
    public void subscribe(Object object) {
        subscribe(getListeners(object.getClass(), object), false);
    }

    @Override
    public void subscribe(Class<?> klass) {
        subscribe(getListeners(klass, null), true);
    }

    @Override
    public void subscribe(IListener listener) {
        subscribe(listener, false);
    }

    private void subscribe(List<IListener> listeners, boolean onlyStatic) {
        for (IListener listener : listeners) subscribe(listener, onlyStatic);
    }

    private void subscribe(IListener listener, boolean onlyStatic) {
        if (onlyStatic) {
            if (listener instanceof LambdaListener && ((LambdaListener) listener).isStatic())
                insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
        }
        else {
            insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
        }
    }

    private void insert(List<IListener> listeners, IListener listener) {
        int i = 0;
        for (; i < listeners.size(); i++) {
            if (listener.getPriority() > listeners.get(i).getPriority()) break;
        }

        listeners.add(i, listener);
    }

    @Override
    public void unsubscribe(Object object) {
        List<IListener> listeners = listenerCache.remove(object);
        if (listeners != null) unsubscribe(listeners);
        // for backwards-compatibility
        else unsubscribe(object.getClass());
    }

    @Override
    public void unsubscribe(Class<?> klass) {
        List<IListener> staticListeners = staticListenerCache.remove(klass);
        if (staticListeners != null) unsubscribe(staticListeners);
    }

    private void unsubscribe(List<IListener> listeners) {
        for (IListener listener : listeners) unsubscribe(listener);
    }

    @Override
    public void unsubscribe(IListener listener) {
        List<IListener> l = listenerMap.get(listener.getTarget());
        if (l != null) l.remove(listener);
    }

    private List<IListener> getListeners(Class<?> klass, Object object) {
        Function<Object, List<IListener>> func = o -> {
            List<IListener> listeners = new CopyOnWriteArrayList<>();

            getListeners(listeners, klass, object);

            return listeners;
        };

        if (object == null) return staticListenerCache.computeIfAbsent(klass, func);
        else return listenerCache.computeIfAbsent(object, func);
    }

    private void getListeners(List<IListener> listeners, Class<?> klass, Object object) {
        for (Method method : klass.getDeclaredMethods()) {
            if (isValid(method)) {
                listeners.add(new LambdaListener(getLambdaFactory(klass), klass, object, method));
            }
        }

        if (klass.getSuperclass() != null) getListeners(listeners, klass.getSuperclass(), object);
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventHandler.class)) return false;
        if (method.getReturnType() != void.class) return false;
        if (method.getParameterCount() != 1) return false;

        return !method.getParameters()[0].getType().isPrimitive();
    }

    private MethodHandles.Lookup getLambdaFactory(Class<?> klass) {
        synchronized (lookupInfos) {
            for (LookupInfo info : lookupInfos) {
                if (klass.getName().startsWith(info.packagePrefix)) return info.lookup;
            }
        }

        throw new NoLambdaFactoryException(klass);
    }
}
