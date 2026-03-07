package io.github.ygrip.testara.core.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RetryableMethod {
    /**
     * Description for debugging and logging purposes
     */
    String description() default "";
}
