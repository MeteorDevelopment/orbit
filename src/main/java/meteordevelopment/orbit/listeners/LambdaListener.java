package meteordevelopment.orbit.listeners;

import meteordevelopment.orbit.EventHandler;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Default implementation of a {@link IListener} that creates a lambda at runtime to call the target method.
 */
public class LambdaListener implements IListener {
    private static final Map<Method, MethodHandle> LAMBDA_FACTORY_CACHE = new ConcurrentHashMap<>();

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private final Consumer<Object> executor;

    /**
     * Creates a new lambda listener, can be used for both static and non-static methods.
     * @param klass Class of the object
     * @param object Object, null if static
     * @param method Method to create lambda for
     */
    @SuppressWarnings("unchecked")
    public LambdaListener(MethodHandles.Lookup lookup, Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        try {
            MethodHandle lambdaFactory = LAMBDA_FACTORY_CACHE.computeIfAbsent(method, innerMethod -> {
                try {
                    MethodHandles.Lookup innerLookup = lookup.in(klass);

                    return LambdaMetafactory.metafactory(
                        innerLookup, "accept",
                        isStatic ? MethodType.methodType(Consumer.class) : MethodType.methodType(Consumer.class, klass),
                        MethodType.methodType(void.class, Object.class),
                        innerLookup.unreflect(innerMethod),
                        MethodType.methodType(void.class, innerMethod.getParameters()[0].getType())
                    ).getTarget();
                } catch (IllegalAccessException | LambdaConversionException e) {
                    throw new RuntimeException(e);
                }
            });

            if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invoke();
            else this.executor = (Consumer<Object>) lambdaFactory.invoke(object);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Error creating lambda listener", throwable);
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
