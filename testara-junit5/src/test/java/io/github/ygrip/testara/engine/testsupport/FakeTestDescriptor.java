package io.github.ygrip.testara.engine.testsupport;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;

/**
 * Minimal {@link TestDescriptor} test double, just enough to construct a
 * {@code TestaraExtensionContext} in tests without a real JUnit5 discovery/launcher session.
 */
public final class FakeTestDescriptor extends AbstractTestDescriptor {

  public FakeTestDescriptor(String uniqueId, String displayName) {
    super(UniqueId.forEngine("testara-fake").append("scenario", uniqueId), displayName);
  }

  @Override
  public TestDescriptor.Type getType() {
    return TestDescriptor.Type.TEST;
  }
}
