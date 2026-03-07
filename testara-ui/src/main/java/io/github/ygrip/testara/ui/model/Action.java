package io.github.ygrip.testara.ui.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.constraints.NotNull;

/**
 * <p>Action class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Action {
  boolean allowAnonymousCall() default false;

  @NotNull String value();
}
