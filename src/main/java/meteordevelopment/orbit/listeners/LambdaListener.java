package meteordevelopment.orbit.listeners;

import meteordevelopment.orbit.EventHandler;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Default implementation of a {@link IListener} that creates a lambda at runtime to call the target method.
 */
public class LambdaListener implements IListener {
    private static final Map<Method, WeakReference<MethodHandle>> LAMBDA_FACTORY_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private final Consumer<Object> executor;

    /**
     * Creates a new lambda listener, can be used for both static and non-static methods.
     *
     * @param klass  Class of the object
     * @param object Object, null if static
     * @param method Method to create lambda for
     */
    @SuppressWarnings("unchecked")
    public LambdaListener(MethodHandles.Lookup lookup, Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        WeakReference<MethodHandle> lambdaFactoryRef = LAMBDA_FACTORY_CACHE.get(method);
        MethodHandle lambdaFactory;
        try {
            if (lambdaFactoryRef != null && lambdaFactoryRef.get() != null) {
                lambdaFactory = lambdaFactoryRef.get();
            } else {
                MethodHandles.Lookup innerLookup = lookup.in(klass);

                lambdaFactory = LambdaMetafactory.metafactory(
                    innerLookup, "accept",
                    isStatic ? MethodType.methodType(Consumer.class) : MethodType.methodType(Consumer.class, klass),
                    MethodType.methodType(void.class, Object.class),
                    innerLookup.unreflect(method),
                    MethodType.methodType(void.class, method.getParameters()[0].getType())
                ).getTarget();

                if (!isStatic) {
                    lambdaFactory = lambdaFactory.asType(MethodType.methodType(Consumer.class, Object.class));
                }

                LAMBDA_FACTORY_CACHE.put(method, new WeakReference<>(lambdaFactory));
            }

            assert lambdaFactory != null;

            if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invokeExact();
            else this.executor = (Consumer<Object>) lambdaFactory.invokeExact(object);
        } catch (Throwable throwable) {
            String message = String.format(
                "Could not create lambda listener for '%s.%s(%s)'.",
                klass.getSimpleName(), method.getName(), target.getSimpleName()
            );
            throw new IllegalStateException(message, throwable);
        }
    }

    @Override
    public void call(Object event) {
        executor.accept(event);
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public boolean isStatic() {
        return isStatic;
    }
}
