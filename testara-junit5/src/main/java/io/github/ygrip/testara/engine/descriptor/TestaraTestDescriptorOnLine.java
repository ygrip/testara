package io.github.ygrip.testara.engine.descriptor;

import io.cucumber.core.feature.FeatureWithLines;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.discovery.ClasspathResourceSelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.discovery.UriSelector;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;

import java.util.Optional;
import java.util.function.Predicate;

public class TestaraTestDescriptorOnLine {

  static Predicate<TestDescriptor> testDescriptorOnLine(int line) {
    return (descriptor) -> (Boolean) descriptor.getSource().flatMap((testSource) -> {
      if (testSource instanceof FileSource) {
        FileSource fileSystemSource = (FileSource) testSource;
        return fileSystemSource.getPosition();
      } else if (testSource instanceof ClasspathResourceSource) {
        ClasspathResourceSource classpathResourceSource = (ClasspathResourceSource) testSource;
        return classpathResourceSource.getPosition();
      } else {
        return Optional.empty();
      }
    }).map(FilePosition::getLine).map((testSourceLine) -> line == testSourceLine).orElse(false);
  }

  private static boolean anyTestDescriptor(TestDescriptor testDescriptor) {
    return true;
  }

  private static Predicate<TestDescriptor> eitherTestDescriptor(Predicate<TestDescriptor> a,
      Predicate<TestDescriptor> b) {
    return a.or(b);
  }

  static Predicate<TestDescriptor> from(FeatureWithLines selector) {
    return selector.lines()
        .stream()
        .map(TestaraTestDescriptorOnLine::testDescriptorOnLine)
        .reduce(TestaraTestDescriptorOnLine::eitherTestDescriptor)
        .orElse(TestaraTestDescriptorOnLine::anyTestDescriptor);
  }

  static Predicate<TestDescriptor> from(UriSelector selector) {
    String query = selector.getUri().getQuery();
    return FilePosition.fromQuery(query)
        .map(FilePosition::getLine)
        .map(TestaraTestDescriptorOnLine::testDescriptorOnLine)
        .orElse(TestaraTestDescriptorOnLine::anyTestDescriptor);
  }

  static Predicate<TestDescriptor> from(ClasspathResourceSelector selector) {
    return selector.getPosition()
        .map(org.junit.platform.engine.discovery.FilePosition::getLine)
        .map(TestaraTestDescriptorOnLine::testDescriptorOnLine)
        .orElse(TestaraTestDescriptorOnLine::anyTestDescriptor);
  }

  static Predicate<TestDescriptor> from(FileSelector selector) {
    return selector.getPosition()
        .map(org.junit.platform.engine.discovery.FilePosition::getLine)
        .map(TestaraTestDescriptorOnLine::testDescriptorOnLine)
        .orElse(TestaraTestDescriptorOnLine::anyTestDescriptor);
  }
}
