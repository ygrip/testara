package io.github.ygrip.testara.core.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>RequestData class.</p>
 *
 * @author yunaz.ramadhan on 12/7/2019
 * @version $Id: $Id
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestData {
  int order() default 2;
}
