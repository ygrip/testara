package io.github.ygrip.testara.command.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>CommandTag class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandTag {
  String command();

  String[] alias() default {};

  String[] subCommands() default {};

  boolean overwrite() default false;

  boolean cacheable() default false;
}
