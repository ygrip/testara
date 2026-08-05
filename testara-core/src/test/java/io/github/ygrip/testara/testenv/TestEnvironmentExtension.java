package io.github.ygrip.testara.testenv;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that reads {@link WithModules} from the test class
 * and lazily boots the required {@link EnvironmentModule} instances via
 * {@link TestEnvironment}.
 * <p>
 * Containers are started once and shared across all test classes in the
 * same JVM. Cleanup is handled via an {@link AutoCloseable} registered
 * in JUnit's root store, so containers are stopped after the last test
 * class finishes.
 */
public class TestEnvironmentExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot()
            .getStore(ExtensionContext.Namespace.GLOBAL)
            .getOrComputeIfAbsent(
                TestEnvironment.class.getName(),
                key -> (AutoCloseable) TestEnvironment::shutdownAll
            );

        Class<?> testClass = context.getRequiredTestClass();
        WithModules annotation = testClass.getAnnotation(WithModules.class);
        if (annotation != null) {
            for (Class<? extends EnvironmentModule> moduleClass : annotation.value()) {
                TestEnvironment.getModule(moduleClass);
            }
        }
    }
}
