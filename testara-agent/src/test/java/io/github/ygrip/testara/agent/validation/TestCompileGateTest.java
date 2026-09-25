package io.github.ygrip.testara.agent.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class TestCompileGateTest {

  @TempDir
  Path projectRoot;

  @Test
  void timeoutFiresEvenWhileTheBuildKeepsItsOutputOpen() throws IOException {
    wrapper("""
        echo "[INFO] compiling"
        sleep 60
        """);

    TestCompileGate.Result result = assertTimeoutPreemptively(Duration.ofSeconds(20),
        () -> new TestCompileGate().run(projectRoot, 1));

    assertFalse(result.passed());
    assertTrue(result.summary().contains("TIMEOUT"), result.summary());
  }

  @Test
  void reportsCompileErrorsFromTheCapturedLog() throws IOException {
    wrapper("""
        echo "[ERROR] /src/test/java/Steps.java:[12,5] cannot find symbol"
        echo "[INFO] BUILD FAILURE"
        exit 1
        """);

    TestCompileGate.Result result = new TestCompileGate().run(projectRoot, 30);

    assertFalse(result.passed());
    assertEquals(1, result.errors().size());
    assertTrue(result.toLine().contains("cannot find symbol"), result.toLine());
  }

  @Test
  void passesOnZeroExit() throws IOException {
    wrapper("exit 0\n");

    assertTrue(new TestCompileGate().run(projectRoot, 30).passed());
  }

  private void wrapper(String body) throws IOException {
    Path mvnw = projectRoot.resolve("mvnw");
    Files.writeString(mvnw, "#!/bin/sh\n" + body);
    assertTrue(mvnw.toFile().setExecutable(true));
  }
}
