package io.github.ygrip.testara.ui.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.ygrip.testara.ui.factory.EngineFactory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * <p>DriverMetadata class.</p>
 *
 * @author yunaz.ramadhan on 4/9/2021
 * @version $Id: $Id
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DriverMetadata {
  @NotNull @Pattern(regexp = "\\S+", message = "Driver name should not have whitespace") String name();

  @NotNull DeviceType[] platforms() default {DeviceType.DEFAULT};

  String browserName() default "";

  @NotNull Class<? extends EngineFactory<?>>  engine();
}
