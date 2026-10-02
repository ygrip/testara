package io.github.ygrip.testara.agent.index;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.ygrip.testara.agent.catalog.StepLinker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for testara-all.4: collectJavaSourceRoots added the project root AND every
 * module directory to the roots list, but scanJavaFiles(root) already recurses into modules -
 * every step definition and driver was indexed twice.
 */
class ProjectIndexerTest {

  @Test
  void doesNotDoubleScanStepDefinitionsAndDriversAcrossModuleRoots(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("pom.xml"), """
        <project>
          <modules>
            <module>my-module</module>
          </modules>
        </project>
        """);

    Path srcMain = projectRoot.resolve("my-module/src/main/java/com/example");
    Files.createDirectories(srcMain);

    Files.writeString(srcMain.resolve("MySteps.java"), """
        package com.example;
        import io.cucumber.java.en.Given;
        public class MySteps {
          @Given("a precondition")
          public void aPrecondition() { }
        }
        """);

    Files.writeString(srcMain.resolve("MyDriver.java"), """
        package com.example;
        @DriverMetadata(name = "chrome", engine = SomeEngine.class, platforms = {DESKTOP}, browserName = "chrome")
        public class MyDriver { }
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    assertEquals(1, profile.stepDefinitions().size(),
        "step definition must be indexed once, not once per overlapping source root");
    assertEquals(1, profile.drivers().size(),
        "driver must be indexed once, not once per overlapping source root");
  }
  @Test
  void indexesEffectiveFeatureScenarioAndExamplesTags(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("pom.xml"), "<project><properties><java.version>21</java.version></properties></project>");
    Path featureDir = projectRoot.resolve("src/test/resources/features");
    Files.createDirectories(featureDir);
    Files.writeString(featureDir.resolve("checkout.feature"), """
        @api
        Feature: Checkout

          @smoke
          Scenario: Quick checkout
            Given a cart
            Then checkout succeeds

          @regression
          Scenario Outline: Checkout by channel
            Given channel "<channel>"
            Then checkout succeeds

            @desktop
            Examples:
              | channel |
              | web     |
              | desktop |

            @mobile
            Examples:
              | channel |
              | app     |
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    TagIndex api = tag(profile, "@api");
    TagIndex smoke = tag(profile, "@smoke");
    TagIndex regression = tag(profile, "@regression");
    TagIndex desktop = tag(profile, "@desktop");
    TagIndex mobile = tag(profile, "@mobile");

    assertEquals(2, api.scenarioCount());
    assertEquals(4, api.executableCaseCount());
    assertEquals(1, smoke.scenarioCount());
    assertEquals(1, smoke.executableCaseCount());
    assertEquals(1, regression.scenarioCount());
    assertEquals(3, regression.executableCaseCount());
    assertEquals(1, desktop.scenarioCount());
    assertEquals(2, desktop.executableCaseCount());
    assertEquals(1, mobile.scenarioCount());
    assertEquals(1, mobile.executableCaseCount());
  }

  @Test
  void extractsCommandAndValidationAliasesAndCacheableInAnyOrder(@TempDir Path projectRoot) throws IOException {
    Path pkg = projectRoot.resolve("src/main/java/io/github/ygrip/testara/custom");
    Files.createDirectories(pkg);
    Files.writeString(pkg.resolve("SumCommand.java"), """
        package io.github.ygrip.testara.custom;
        @CommandTag(command = "sumof", alias = {"sum", "total"}, overwrite = true, cacheable = true)
        public class SumCommand implements CommandLogic<Map<String, List<Integer>>> { }
        """);
    Files.writeString(pkg.resolve("FindCommand.java"), """
        package io.github.ygrip.testara.custom;
        @CommandTag(alias = "findone", command = "element")
        public class FindCommand implements CommandLogic<String> { }
        """);
    Files.writeString(pkg.resolve("EqualsValidation.java"), """
        package io.github.ygrip.testara.custom;
        @ValidationTag(cacheable = true, command = "EQUALS", alias = "IS")
        public class EqualsValidation extends ValidatorLogic<Object, Object> { }
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    CommandIndex sum = command(profile, "sumof");
    assertEquals(List.of("sum", "total"), sum.aliases());
    assertTrue(sum.cacheable());
    assertEquals("Map<String, List<Integer>>", sum.returnType());
    CommandIndex find = command(profile, "element");
    assertEquals(List.of("findone"), find.aliases());
    assertFalse(find.cacheable());
    ValidationIndex equals = profile.validations().stream()
        .filter(v -> v.validation().equals("EQUALS")).findFirst().orElseThrow();
    assertEquals(List.of("IS"), equals.aliases());
    assertTrue(equals.cacheable());
  }

  @Test
  void readsCommandAndValidatorScanLocationsSeparatelyFromAllTestPropertyFiles(@TempDir Path projectRoot)
      throws IOException {
    Path resources = projectRoot.resolve("src/test/resources");
    Files.createDirectories(resources);
    Files.writeString(resources.resolve("application.properties"), """
        # command.executor.scan-locations=com.ignored
        command.executor.scan-locations=com.acme.command
        """);
    Files.writeString(resources.resolve("validation.properties"),
        "validator.helper.scan-locations=com.acme.validation\n");
    writeJava(projectRoot, "com/acme/command/TokenCommand.java",
        "@CommandTag(command = \"token\")\npublic class TokenCommand { }\n");
    writeJava(projectRoot, "com/acme/validation/OkValidation.java",
        "@ValidationTag(command = \"OK\")\npublic class OkValidation { }\n");
    writeJava(projectRoot, "com/acme/validation/StrayCommand.java",
        "@CommandTag(command = \"stray\")\npublic class StrayCommand { }\n");
    writeJava(projectRoot, "com/ignored/IgnoredCommand.java",
        "@CommandTag(command = \"ignored\")\npublic class IgnoredCommand { }\n");

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    List<String> commands = profile.commands().stream().map(CommandIndex::command).toList();
    assertEquals(List.of("token"), commands, "only command.executor.scan-locations filters commands");
    assertEquals(List.of("OK"), profile.validations().stream().map(ValidationIndex::validation).toList());
  }

  @Test
  void storesUnescapedProjectStepExpressionsSoRegexStepsLink(@TempDir Path projectRoot) throws IOException {
    writeJava(projectRoot, "com/example/CartSteps.java", """
        public class CartSteps {
          @Given("^user has (\\\\d+) items$")
          public void items(int count) { }
          @When("user types \\"([^\\"]*)\\"")
          public void types(String value) { }
          @Then("^broken (unclosed$")
          public void broken() { }
        }
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    List<String> expressions = profile.stepDefinitions().stream().map(StepDefinitionIndex::expression).toList();
    assertTrue(expressions.contains("^user has (\\d+) items$"), expressions::toString);
    assertTrue(expressions.contains("user types \"([^\"]*)\""), expressions::toString);
    var links = StepLinker.linkFeature("""
        Scenario: cart
          Given user has 3 items
          When user types "abc"
          Then broken (unclosed
        """, List.of(), profile.stepDefinitions());
    assertEquals(StepLinker.Source.PROJECT, links.get(0).source(), links::toString);
    assertEquals(StepLinker.Source.PROJECT, links.get(1).source(), links::toString);
    assertEquals(StepLinker.Source.UNMATCHED, links.get(2).source(), links::toString);
  }

  @Test
  void skipsBuildOutputCopiesAndOverlappingFeatureRoots(@TempDir Path projectRoot) throws IOException {
    Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }\n");
    Files.writeString(projectRoot.resolve("testara-agent.yaml"), """
        project:
          featureRoots:
            - "."
            - "src"
        """);
    String feature = "Feature: Once\n  Scenario: only once\n    Given a step\n";
    for (String dir : List.of("src/test/resources/features", "target/test-classes/features",
        "build/resources/test/features")) {
      Files.createDirectories(projectRoot.resolve(dir));
      Files.writeString(projectRoot.resolve(dir).resolve("once.feature"), feature);
    }

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    assertEquals(1, profile.features().size(), () -> profile.features().toString());
  }

  @Test
  void recordsFeatureParseErrorsInProfile(@TempDir Path projectRoot) throws IOException {
    Path features = projectRoot.resolve("src/test/resources/features");
    Files.createDirectories(features);
    Files.writeString(features.resolve("good.feature"), "Feature: Good\n  Scenario: ok\n    Given a step\n");
    Files.writeString(features.resolve("broken.feature"), "Feature: Broken\n  Scenario: bad\n    Given a step\n    this is not gherkin\n");

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    assertEquals(1, profile.features().size());
    assertEquals(1, profile.parseErrors().size(), () -> profile.parseErrors().toString());
    assertTrue(profile.parseErrors().get(0).startsWith("src/test/resources/features/broken.feature: "),
        () -> profile.parseErrors().toString());
  }

  @Test
  void countsSameNamedScenariosSeparately(@TempDir Path projectRoot) throws IOException {
    Path features = projectRoot.resolve("src/test/resources/features");
    Files.createDirectories(features);
    Files.writeString(features.resolve("dupe.feature"), """
        @api
        Feature: Dupe
          Scenario: same
            Given a step
          Scenario: same
            Given another step
        """);

    TestaraProjectProfile profile = new ProjectIndexer().index(projectRoot);

    assertEquals(2, tag(profile, "@api").scenarioCount());
  }

  private void writeJava(Path projectRoot, String relative, String body) throws IOException {
    Path file = projectRoot.resolve("src/main/java").resolve(relative);
    Files.createDirectories(file.getParent());
    String pkg = relative.substring(0, relative.lastIndexOf('/')).replace('/', '.');
    Files.writeString(file, "package " + pkg + ";\n" + body);
  }

  private CommandIndex command(TestaraProjectProfile profile, String name) {
    return profile.commands().stream().filter(c -> c.command().equals(name)).findFirst()
        .orElseThrow(() -> new AssertionError("expected command " + name + " in " + profile.commands()));
  }

  private TagIndex tag(TestaraProjectProfile profile, String tag) {
    TagIndex result = profile.tags().stream().filter(t -> t.tag().equals(tag)).findFirst().orElse(null);
    assertNotNull(result, "expected indexed tag " + tag);
    return result;
  }

}
