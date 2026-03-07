package io.github.ygrip.testara.core.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>TestComponent class.</p>
 *
 * @author yunaz.ramadhan on 4/1/2026
 * @version $Id: $Id
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
}
