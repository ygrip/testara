package io.github.ygrip.testara.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>LoadProperties class.</p>
 *
 * @author yunaz.ramadhan on 4/1/2026
 * @version $Id: $Id
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoadProperties {
  String prefix();
}
