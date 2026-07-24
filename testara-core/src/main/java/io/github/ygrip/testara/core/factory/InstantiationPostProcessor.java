package io.github.ygrip.testara.core.factory;

import java.lang.reflect.Constructor;

/**
 * SPI for changing the concrete class that will be instantiated.
 *
 * <p>Unlike {@link InstancePostProcessor}, this extension point runs before the
 * constructor is invoked. It is intended for subclass-based proxies which must
 * receive the same constructor dependencies as the requested component.</p>
 */
public interface InstantiationPostProcessor extends InstancePostProcessor {

  /**
   * Return the concrete class to instantiate for {@code requestedType}.
   * The returned class must extend the requested type and expose a constructor
   * with the same parameter types as {@code selectedConstructor}.
   */
  <T> Class<? extends T> processType(
    Class<T> requestedType,
    Constructor<?> selectedConstructor
  );
}

