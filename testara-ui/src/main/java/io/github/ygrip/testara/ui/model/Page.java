package io.github.ygrip.testara.ui.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.constraints.NotNull;

/**
 * <p>Page class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Page {
  @NotNull String name();

  String url() default "";

  @NotNull DeviceType[] platforms() default {DeviceType.DEFAULT};
}
