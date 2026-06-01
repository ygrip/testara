package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

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

  @Test
  void featureGuardReportsStepsThatDoNotLinkToKnownGlue() {
    String feature = """
        Scenario: invalid
          Then user should see "complete header" with text "Thank you for your order!"
        """;
    List<FlavorEntry> catalog = List.of(new FlavorEntry("ui", "Then",
        "{actor} should see {string} is {displayedOrNotDisplayed}", "",
        "display assertion", "testara-ui-cucumber", "UIBaseSteps"));

    var violations = GenerationGuard.validateFeature(feature, catalog, List.of());

    assertTrue(violations.stream().anyMatch(v -> v.rule().equals("STEP")));
    assertTrue(violations.stream().anyMatch(v -> v.line().contains("with text")));
  }
}
