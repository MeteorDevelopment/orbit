package test;

import meteordevelopment.orbit.*;
import meteordevelopment.orbit.listeners.ConsumerListener;

import java.lang.invoke.MethodHandles;

public class Main {
    static class Foo implements ICancellable {
        private boolean cancelled;

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    static class Abc {
        @EventHandler
        private void onString(String str) {
            // This listener will be called because Abc2 extends Abc
            System.out.println("Super class string: " + str);
        }
    }

    static class Abc2 extends Abc { }

    public static void main(String[] args) {
        System.out.println("-- WITHOUT INSTANCE --");
        IEventBus bus = new EventBus();
        bus.registerLambdaFactory("test", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Subscribes only static methods
        bus.subscribe(Main.class);

        // Subscribes listener which takes a Consumer interface
        bus.subscribe(new ConsumerListener<Foo>(Foo.class, EventPriority.HIGHEST, foo -> {
            System.out.println("Consumer listener");
        }));

        // Posts 2 events, int event wont do anything because the listener for it is not static and thus hasn't been subscribed
        bus.post(new Foo());
        bus.post(9);

        // Unsubscribes only static methods
        bus.unsubscribe(Main.class);

        System.out.println();
        System.out.println("-- WITH INSTANCE --");
        new Main();
    }

    public Main() {
        IEventBus bus = new EventBus();
        bus.registerLambdaFactory("test", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Subscribes both static and normal methods
        bus.subscribe(this);

        // Posts 3 events, boolean event wont do anything because there isn't any listener that accepts boolean
        bus.post("Hello");
        bus.post(159);
        bus.post(true);

        // Unsubscribes both static and normal methods
        bus.unsubscribe(this);

        // Nothing will happen because all listeners were unsubscribed
        bus.post("Pog");

        // Abc2 has no listeners but it also subscribes listeners from superclasses
        bus.subscribe(new Abc2());
        bus.post("Superclass");
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void onFoo1(Foo foo) {
        System.out.println("Foo 1");

        // Cancels the event so onFoo2 won't be called
        foo.cancel();
    }

    @EventHandler
    private static void onFoo2(Foo foo) {
        // Won't run
        System.out.println("Foo 2");
    }

    @EventHandler
    private void onString1(String idk) {
        // This listener will run later than onString2 because it has smaller priority
        // Default priority is EventPriority.MEDIUM
        System.out.println("String 1: " + idk);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private static void onString2(String idk) {
        // This listener will run sooner than onString1 because it has bigger priority
        System.out.println("String 2: " + idk);
    }

    @EventHandler
    private void onNumba(Integer a) {
        System.out.println("Numba: " + a);
    }

    @EventHandler
    private void onNumba(int a) {
        // Wont be called because primitives cannot be used as events
        System.out.println("Numba: " + a);
    }
}
