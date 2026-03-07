package io.github.ygrip.testara.engine.extension;

public interface TestaraExtension {
  default void beforeAll(TestaraExtensionContext context) throws Exception {}

  default void beforeEach(TestaraExtensionContext context) throws Exception {}

  default void afterEach(TestaraExtensionContext context) throws Exception {}

  default void afterAll(TestaraExtensionContext context) throws Exception {}
}
