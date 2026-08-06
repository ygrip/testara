package io.github.ygrip.testara.agent.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraAgentCliTest {
  @Test
  void versionProviderReadsBuildVersion() {
    String version = TestaraAgentCli.VersionProvider.readVersion();

    assertNotEquals("unknown", version);
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(?:[-+].+)?"));
  }
}
