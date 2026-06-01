package io.github.ygrip.testara.agent.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationGuardTest {

  @Test
  void propertyGuardUsesSingularCommandAndValidationPackages() {
    var violations = GenerationGuard.validateProperties("class.loader.default-scan-locations=io.github.ygrip.testara");

    String rendered = violations.toString();
    assertTrue(rendered.contains("{basePackage}.command"));
    assertTrue(rendered.contains("{basePackage}.validation"));
  }

  @Test
  void pomGuardRejectsTestScopeForMainJavaDependencies() {
    String pom = """
        <dependencies>
          <dependency>
            <groupId>io.github.ygrip</groupId>
            <artifactId>testara-ui</artifactId>
            <version>${testara.version}</version>
            <scope>test</scope>
          </dependency>
          <dependency>
            <groupId>io.github.ygrip</groupId>
            <artifactId>testara-ui-cucumber</artifactId>
            <version>${testara.version}</version>
          </dependency>
        </dependencies>
        """;

    var violations = GenerationGuard.validatePom(pom);

    assertTrue(violations.stream().anyMatch(v -> v.line().contains("testara-ui has test scope")));
    assertTrue(violations.stream().anyMatch(v -> v.line().contains("testara-ui-cucumber is not test scoped")));
  }
}
