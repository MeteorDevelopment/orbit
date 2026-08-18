package meteordevelopment.orbit.listeners;

import meteordevelopment.orbit.EventHandler;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Default implementation of a {@link IListener} that creates a lambda at runtime to call the target method.
 */
public class LambdaListener implements IListener {
    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private final Consumer<Object> executor;

    /**
     * Creates a new lambda listener, can be used for both static and non-static methods.
     *
     * @param lambdaFactory The factory from which the lambda is created
     * @param object        Object, null if static
     * @param method        Method to create lambda for
     */
    @SuppressWarnings("unchecked")
    public LambdaListener(MethodHandle lambdaFactory, Object object, Method method) throws Throwable {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invokeExact();
        else this.executor = (Consumer<Object>) lambdaFactory.invokeExact(object);
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

    /**
     * @return Whether the method associated with this listener is static
     */
    public boolean isStatic() {
        return isStatic;
    }
}
