package meteordevelopment.orbit;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

public class Listener {
    private static Constructor<MethodHandles.Lookup> lookupConstructor;

    public final Class<?> target;
    public final boolean isStatic;
    public final int priority;

    private Consumer<Object> executor;

    @SuppressWarnings("unchecked")
    public Listener(Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        try {
            String name = method.getName();

            boolean a = lookupConstructor.isAccessible();
            lookupConstructor.setAccessible(true);
            MethodHandles.Lookup lookup = lookupConstructor.newInstance(klass);
            lookupConstructor.setAccessible(a);

            MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType());

            MethodHandle methodHandle;
            MethodType invokedType;

            if (isStatic) {
                methodHandle = lookup.findStatic(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class);
            }
            else {
                methodHandle = lookup.findVirtual(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class, klass);
            }

            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    invokedType,
                    MethodType.methodType(void.class, Object.class),
                    methodHandle,
                    methodType
            ).getTarget();

            if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invoke();
            else this.executor = (Consumer<Object>) lambdaFactory.invoke(object);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public void call(Object event) {
        executor.accept(event);
    }

    static {
        try {
            lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}
