package io.github.ygrip.testara.testenv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry that lazily creates and caches {@link EnvironmentModule} instances.
 * Containers start only when first requested, are shared across test classes,
 * and are torn down when the JVM (or JUnit root context) shuts down.
 */
public final class TestEnvironment {

    private static final Map<Class<? extends EnvironmentModule>, EnvironmentModule> modules =
        new ConcurrentHashMap<>();

    private TestEnvironment() {}

    @SuppressWarnings("unchecked")
    public static <T extends EnvironmentModule> T getModule(Class<T> type) {
        return (T) modules.computeIfAbsent(type, TestEnvironment::createAndStart);
    }

    public static void shutdownAll() {
        modules.values().forEach(module -> {
            try {
                module.stop();
            } catch (Exception e) {
                System.err.println("[TestEnvironment] Failed to stop " + module.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        modules.clear();
    }

    private static EnvironmentModule createAndStart(Class<? extends EnvironmentModule> type) {
        try {
            EnvironmentModule module = type.getDeclaredConstructor().newInstance();
            module.start();
            return module;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start module: " + type.getSimpleName(), e);
        }
    }
}
