package io.github.ygrip.testara.agent.skill.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

  @TempDir
  Path dir;

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void timeoutKillsTheWholeProcessTree() throws Exception {
    Path pidFile = dir.resolve("child.pid");
    Path script = script("build.sh", """
        sleep 60 &
        echo $! > child.pid
        wait
        """);

    long start = System.currentTimeMillis();
    ProcessRunner.Outcome outcome = ProcessRunner.run(List.of(script.toString()), dir, dir.resolve("run.log"),
        Duration.ofSeconds(1));

    assertTrue(outcome.timedOut());
    assertEquals(-1, outcome.exitCode());
    assertTrue(System.currentTimeMillis() - start < 20_000, "must not wait for the child to finish");
    long childPid = Long.parseLong(Files.readString(pidFile).strip());
    assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false),
        "forked child (test JVM/browser stand-in) must be killed with its parent");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void chattyProcessCannotBlockBecauseOutputGoesToTheLog() throws Exception {
    Path script = script("chatty.sh", """
        i=0
        while [ $i -lt 20000 ]; do echo "line $i with some padding to fill the pipe quickly"; i=$((i+1)); done
        exit 3
        """);

    ProcessRunner.Outcome outcome = ProcessRunner.run(List.of(script.toString()), dir, dir.resolve("logs/run.log"),
        Duration.ofSeconds(30));

    assertFalse(outcome.timedOut());
    assertEquals(3, outcome.exitCode());
    assertEquals(20_000, ProcessRunner.readLogLines(dir.resolve("logs/run.log")).size());
  }

  @Test
  void readsMalformedUtf8LogLeniently() throws IOException {
    Path log = dir.resolve("latin1.log");
    Files.write(log, "Tests run: 1 café\n[ERROR] boom".getBytes(StandardCharsets.ISO_8859_1));

    List<String> lines = ProcessRunner.readLogLines(log);

    assertEquals(2, lines.size());
    assertTrue(lines.get(0).startsWith("Tests run: 1 caf"));
    assertEquals(List.of(), ProcessRunner.readLogLines(dir.resolve("missing.log")));
  }

  @Test
  void resolvesOsSpecificMavenAndGradleLaunchers() throws IOException {
    assertEquals("mvn", ProcessRunner.mavenLauncher(dir, false));
    assertEquals("mvn.cmd", ProcessRunner.mavenLauncher(dir, true));
    assertEquals("gradle", ProcessRunner.gradleLauncher(dir, false));
    assertEquals("gradle.bat", ProcessRunner.gradleLauncher(dir, true));

    Files.writeString(dir.resolve("mvnw"), "");
    Files.writeString(dir.resolve("mvnw.cmd"), "");
    Files.writeString(dir.resolve("gradlew"), "");
    Files.writeString(dir.resolve("gradlew.bat"), "");

    assertEquals(dir.resolve("mvnw").toAbsolutePath().toString(), ProcessRunner.mavenLauncher(dir, false));
    assertEquals(dir.resolve("mvnw.cmd").toAbsolutePath().toString(), ProcessRunner.mavenLauncher(dir, true));
    assertEquals(dir.resolve("gradlew").toAbsolutePath().toString(), ProcessRunner.gradleLauncher(dir, false));
    assertEquals(dir.resolve("gradlew.bat").toAbsolutePath().toString(), ProcessRunner.gradleLauncher(dir, true));
  }

  private Path script(String name, String body) throws IOException {
    Path script = dir.resolve(name);
    Files.writeString(script, "#!/bin/sh\n" + body);
    assertTrue(script.toFile().setExecutable(true));
    return script;
  }
}
