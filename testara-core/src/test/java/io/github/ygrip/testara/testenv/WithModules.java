package io.github.ygrip.testara.testenv;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which {@link EnvironmentModule} implementations a test class requires.
 * Used together with {@link TestEnvironmentExtension} to lazily start
 * only the containers each test suite actually needs.
 *
 * <pre>{@code
 * @ExtendWith(TestEnvironmentExtension.class)
 * @WithModules({KafkaModule.class})
 * public class MyKafkaTest extends BaseTests { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WithModules {
    Class<? extends EnvironmentModule>[] value();
}
