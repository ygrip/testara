package io.github.ygrip.testara.engine.descriptor;

import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Step;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.Node;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.UriSource;

import java.net.URI;

public abstract class TestaraFeatureOrigin {
  private static final String RULE_SEGMENT_TYPE = "rule";
  private static final String FEATURE_SEGMENT_TYPE = "feature";
  private static final String SCENARIO_SEGMENT_TYPE = "scenario";
  private static final String STEP_SEGMENT_TYPE = "step";
  private static final String EXAMPLES_SEGMENT_TYPE = "examples";
  private static final String EXAMPLE_SEGMENT_TYPE = "example";

  TestaraFeatureOrigin() {
  }

  private static FilePosition createFilePosition(Location location) {
    return FilePosition.from(location.getLine(), location.getColumn());
  }

  public static TestaraFeatureOrigin fromUri(URI uri) {
    if ("classpath".equals(uri.getScheme())) {
      if (!uri.getSchemeSpecificPart().startsWith("/")) {
        uri = URI.create("classpath:/" + uri.getRawSchemeSpecificPart());
      }

      ClasspathResourceSource source = ClasspathResourceSource.from(uri);
      return new ClasspathFeatureOrigin(source);
    } else {
      UriSource source = UriSource.from(uri);
      return (source instanceof FileSource ? new FileFeatureOrigin((FileSource) source) : new UriFeatureOrigin(source));
    }
  }

  static boolean isFeatureSegment(UniqueId.Segment segment) {
    return FEATURE_SEGMENT_TYPE.equals(segment.getType());
  }

  abstract TestSource featureSource();

  public abstract TestSource nodeSource(Node node);

  abstract TestSource stepSource(Step step);

  abstract UniqueId featureSegment(UniqueId id, Feature feature);

  UniqueId ruleSegment(UniqueId parent, Node rule) {
    return parent.append(RULE_SEGMENT_TYPE, String.valueOf(rule.getLocation().getLine()));
  }

  public UniqueId scenarioSegment(UniqueId parent, Node scenarioDefinition) {
    return parent.append(SCENARIO_SEGMENT_TYPE, String.valueOf(scenarioDefinition.getLocation().getLine()));
  }

  UniqueId stepSegment(UniqueId parent, Step stepDefinition) {
    return parent.append(STEP_SEGMENT_TYPE, String.valueOf(stepDefinition.getLocation().getLine()));
  }

  public UniqueId stepSegment(UniqueId parent, Location location) {
    return parent.append(STEP_SEGMENT_TYPE, String.valueOf(location.getLine()));
  }

  UniqueId examplesSegment(UniqueId parent, Node examples) {
    return parent.append(EXAMPLES_SEGMENT_TYPE, String.valueOf(examples.getLocation().getLine()));
  }

  UniqueId exampleSegment(UniqueId parent, Node tableRow) {
    return parent.append(EXAMPLE_SEGMENT_TYPE, String.valueOf(tableRow.getLocation().getLine()));
  }

  private static class ClasspathFeatureOrigin extends TestaraFeatureOrigin {
    private final ClasspathResourceSource source;

    ClasspathFeatureOrigin(ClasspathResourceSource source) {
      this.source = source;
    }

    TestSource featureSource() {
      return this.source;
    }

    public TestSource nodeSource(Node node) {
      return ClasspathResourceSource.from(this.source.getClasspathResourceName(),
          TestaraFeatureOrigin.createFilePosition(node.getLocation()));
    }

    @Override
    TestSource stepSource(Step step) {
      return ClasspathResourceSource.from(this.source.getClasspathResourceName(),
          TestaraFeatureOrigin.createFilePosition(step.getLocation()));
    }

    UniqueId featureSegment(UniqueId parent, Feature feature) {
      return parent.append(FEATURE_SEGMENT_TYPE, feature.getUri().toString());
    }
  }


  private static class FileFeatureOrigin extends TestaraFeatureOrigin {
    private final FileSource source;

    FileFeatureOrigin(FileSource source) {
      this.source = source;
    }

    TestSource featureSource() {
      return this.source;
    }

    public TestSource nodeSource(Node node) {
      return FileSource.from(this.source.getFile(), TestaraFeatureOrigin.createFilePosition(node.getLocation()));
    }

    TestSource stepSource(Step step) {
      return FileSource.from(this.source.getFile(), TestaraFeatureOrigin.createFilePosition(step.getLocation()));
    }

    UniqueId featureSegment(UniqueId parent, Feature feature) {
      return parent.append(FEATURE_SEGMENT_TYPE, this.source.getUri().toString());
    }
  }


  private static class UriFeatureOrigin extends TestaraFeatureOrigin {
    private final UriSource source;

    UriFeatureOrigin(UriSource source) {
      this.source = source;
    }

    TestSource featureSource() {
      return this.source;
    }

    public TestSource nodeSource(Node node) {
      return this.source;
    }

    @Override
    TestSource stepSource(Step step) {
      return UriSource.from(this.source.getUri());
    }

    UniqueId featureSegment(UniqueId parent, Feature feature) {
      return parent.append(FEATURE_SEGMENT_TYPE, this.source.getUri().toString());
    }
  }
}

