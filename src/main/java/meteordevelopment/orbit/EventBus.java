package meteordevelopment.orbit;

import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.orbit.listeners.LambdaListener;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Default implementation of {@link IEventBus}.
 */
public class EventBus implements IEventBus {
    private static final Map<Method, WeakReference<MethodHandle>> LAMBDA_FACTORY_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<Object, List<IListener>> listenerCache = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();

    private final List<LookupInfo> lookupInfos = new ArrayList<>();

    @Override
    public void registerLookup(String packagePrefix, MethodHandles.Lookup lookup) {
        synchronized (lookupInfos) {
            // to ensure the lookups are used correctly, they are ordered from longest to shortest
            int i = 0;
            while (i < lookupInfos.size() && lookupInfos.get(i).packagePrefix.length() > packagePrefix.length()) {
                i++;
            }
            lookupInfos.add(i, new LookupInfo(packagePrefix, lookup));
        }
    }

    @Override
    public boolean isListening(Class<?> eventKlass) {
        List<IListener> listeners = listenerMap.get(eventKlass);
        return listeners != null && !listeners.isEmpty();
    }

    @Override
    public boolean isSubscribed(Object object) {
        return listenerCache.containsKey(object);
    }

    @Override
    public boolean isSubscribed(Class<?> klass) {
        return staticListenerCache.containsKey(klass);
    }

    @Override
    public boolean isSubscribed(IListener listener) {
        List<IListener> listeners = listenerMap.get(listener.getTarget());
        return listeners != null && listeners.contains(listener);
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
        subscribe(listenerCache.computeIfAbsent(object, o -> createListeners(o.getClass(), o)));
    }

    @Override
    public void subscribe(Class<?> klass) {
        subscribe(staticListenerCache.computeIfAbsent(klass, k -> createListeners(k, null)));
    }

    private void subscribe(List<IListener> listeners) {
        for (IListener listener : listeners) subscribe(listener);
    }

    @Override
    public void subscribe(IListener listener) {
        insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
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

    private List<IListener> createListeners(Class<?> klass, Object object) {
        List<IListener> listeners = new ArrayList<>();

        while (klass != Object.class) {
            MethodHandles.Lookup lookup = null;

            for (Method method : klass.getDeclaredMethods()) {
                // skip invalid methods
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                if (!isValid(method) || (object == null && !isStatic)) {
                    continue;
                }

                // get or create lambda factory
                WeakReference<MethodHandle> ref = LAMBDA_FACTORY_CACHE.get(method);
                MethodHandle lambdaFactory = ref != null ? ref.get() : null;
                try {
                    if (lambdaFactory == null) {
                        // lazily search for lookup infos
                        if (lookup == null) {
                            lookup = getLookupInfo(klass).in(klass);
                        }

                        lambdaFactory = LambdaMetafactory.metafactory(
                            lookup, "accept",
                            isStatic ? MethodType.methodType(Consumer.class) : MethodType.methodType(Consumer.class, klass),
                            MethodType.methodType(void.class, Object.class),
                            lookup.unreflect(method),
                            MethodType.methodType(void.class, method.getParameters()[0].getType())
                        ).getTarget();

                        if (!isStatic) {
                            lambdaFactory = lambdaFactory.asType(MethodType.methodType(Consumer.class, Object.class));
                        }

                        LAMBDA_FACTORY_CACHE.put(method, new WeakReference<>(lambdaFactory));
                    }

                    listeners.add(new LambdaListener(lambdaFactory, object, method));
                } catch (Throwable throwable) {
                    String message = String.format(
                        "Could not create lambda listener for '%s.%s(%s)'.",
                        klass.getSimpleName(), method.getName(), method.getParameters()[0].getType().getSimpleName()
                    );
                    throw new IllegalStateException(message, throwable);
                }
            }

            klass = klass.getSuperclass();
        }

        return new CopyOnWriteArrayList<>(listeners);
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventHandler.class)) return false;
        if (method.getReturnType() != void.class) return false;
        if (method.getParameterCount() != 1) return false;

        return !method.getParameters()[0].getType().isPrimitive();
    }

    private MethodHandles.Lookup getLookupInfo(Class<?> klass) {
        synchronized (lookupInfos) {
            for (LookupInfo info : lookupInfos) {
                if (klass.getName().startsWith(info.packagePrefix)) return info.lookup;
            }
        }

        throw new NoLambdaFactoryException(klass);
    }
}
