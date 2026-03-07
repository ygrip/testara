package io.github.ygrip.testara.core.registry;

import java.util.ServiceLoader;

public final class ScopeContextLoader {
  private ScopeContextLoader() {

  }

  public static ScopeContext load() {
    return ServiceLoader.load(ScopeContext.class).findFirst().orElse(new SingletonScopeContext());
  }
}
