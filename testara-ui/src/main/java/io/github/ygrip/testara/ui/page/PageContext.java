package io.github.ygrip.testara.ui.page;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.model.ValueUnit;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.ui.config.WebPageDataProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.error.PageFailureException;
import io.github.ygrip.testara.ui.model.Page;
import io.github.ygrip.testara.ui.model.WebPageData;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class PageContext<D extends DriverSession<?>> {
  private final Page metadata;
  private final WebPageData pageData;
  private final ObjectConverter converter;
  private final Supplier<D> driver;

  public PageContext() {
    try {
      this.driver = getActiveDriver();
    } catch (Exception err) {
      throw new PageFailureException(err.getMessage(), err);
    }
    this.metadata = this.getClass()
      .getAnnotation(Page.class);
    this.pageData = loadPageDataProperties(metadata);
    this.converter = ObjectConverterLoader.instance();
  }

  public PageContext(D driver) {
    this.driver = () -> driver;
    this.metadata = this.getClass()
      .getAnnotation(Page.class);
    this.pageData = loadPageDataProperties(metadata);
    this.converter = ObjectConverterLoader.instance();
  }

  private WebPageData loadPageDataProperties(Page metadata) {
    if (ObjectUtils.isEmpty(metadata)) {
      return new WebPageData();
    }
    String pageName = metadata.name();
    return Optional.ofNullable(properties())
      .map(WebPageDataProperties::getPage)
      .filter(ObjectUtils::isNotEmpty)
      .map(platforms -> platforms.get(driver().platform()))
      .filter(ObjectUtils::isNotEmpty)
      .map(properties -> properties.getOrDefault(pageName, new WebPageData()))
      .orElse(new WebPageData());
  }

  private WebPageDataProperties properties() {
    return TestFramework.configuration()
      .get(WebPageDataProperties.class);
  }

  @SuppressWarnings("unchecked")
  private Supplier<D> getActiveDriver() throws PageFailureException {
    try {
      return () -> (D) DriverSessionManager.inThisTestThread()
        .getCurrentDriver();
    } catch (Exception err) {
      log.error("Fail to get current driver on page {}, with error {}", getClass(), err.getMessage(), err);
      throw new PageFailureException(err.getMessage(), err);
    }
  }

  public D driver() {
    return this.driver.get();
  }

  public Page metadata() {
    return this.metadata;
  }

  public abstract String currentUrl();

  public abstract String pageTitle();

  public void open() {
    String url = pageUrl();
    if (StringUtils.isNotEmpty(url)) {
      open(converter.convert(url));
    }
  }

  public String name() {
    return Optional.ofNullable(metadata)
      .map(Page::name)
      .filter(StringUtils::isNotBlank)
      .orElse(null);
  }

  public String pageUrl() {
    return Optional.ofNullable(metadata)
      .map(Page::url)
      .map(converter::convert)
      .filter(ObjectUtils::isNotEmpty)
      .map(Object::toString)
      .orElseGet(() -> Optional.ofNullable(pageData)
        .map(WebPageData::getUrl)
        .map(converter::convert)
        .filter(ObjectUtils::isNotEmpty)
        .map(Object::toString)
        .orElse(null));
  }

  public boolean isCurrentPage() {
    final var pageUrl = pageUrl();
    if (StringUtils.isBlank(pageUrl)) {
      return false;
    }
    final var current = currentUrl();
    log.debug("Current url : {}", current);
    return Optional.ofNullable(current)
      .filter(StringUtils::isNotBlank)
      .map(currentUrl -> currentUrl.startsWith(pageUrl))
      .orElse(false);
  }

  public boolean isCurrentPage(Duration timeout) {
    AtomicReference<Throwable> lastError = new AtomicReference<>();
    AtomicReference<Boolean> result = new AtomicReference<>();
    try {
      Awaitility.await()
        .pollInSameThread()
        .atMost(timeout.plusMillis(1))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> {
          try {
            boolean valid = isCurrentPage();
            result.set(valid);
            return valid;
          } catch (Exception err) {
            lastError.set(err);
            result.set(null);
            return false;
          }
        });
      return result.get();
    } catch (ConditionTimeoutException e) {
      ValueUnit valueUnit = DurationParser.toValueUnit(timeout);
      Throwable cause = lastError.get();
      log.warn(
        "Retry failed after {} {}. Last error: {}",
        valueUnit.getValue(),
        valueUnit.getUnit()
          .name(),
        cause != null ? cause.getMessage() : "unspecified"
      );
      return false;
    }
  }

  public abstract void open(String url);

  public abstract void refresh();

  public abstract void reload();

  public abstract void forward();

  public abstract void back();
}
