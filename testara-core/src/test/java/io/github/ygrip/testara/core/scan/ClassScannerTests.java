package io.github.ygrip.testara.core.scan;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;

@Tag("scan")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ClassScannerTests extends BaseTests {

  @Test
  void withDefaultScanLocations() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> results = scanner.scan(DefaultData.class, RequestData.class);
    List<Class<?>> resolved = results.get(10, TimeUnit.SECONDS);

    assertThat(resolved, notNullValue());
  }

  @Test
  void withAnnotationOnly() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> results = scanner.scan(RequestData.class);
    List<Class<?>> resolved = results.get(10, TimeUnit.SECONDS);

    assertThat(resolved, notNullValue());
  }

  @Test
  void withMappedScanLocations() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> results = scanner.scan("request-data", DefaultData.class, RequestData.class);
    List<Class<?>> resolved = results.get(10, TimeUnit.SECONDS);

    assertThat(resolved, notNullValue());
  }

  @Test
  void onCustomPackages() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> results =
        scanner.scanOnPackages(DefaultData.class, RequestData.class, Set.of("io.github.ygrip.testara.core.data"));
    List<Class<?>> resolved = results.get(10, TimeUnit.SECONDS);

    assertThat(resolved, notNullValue());
  }

  @Test
  void emptyResult() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> results = scanner.scan(DefaultData.class, Annotation.class);
    List<Class<?>> resolved = results.get(10, TimeUnit.SECONDS);

    assertThat(resolved, empty());
  }
}
