package io.github.ygrip.testara.ui.page;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.ObjectUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import io.github.ygrip.testara.core.model.ValueUnit;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.Locator;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class Element<E> {
  @Getter
  private final Locator locator;
  private final Element<E> parent;
  private final PageFinder<?, E, ?> finder;
  private final PageContext<?> pageContext;
  private final Kind kind;
  private E instance;
  private Element<E> child;
  private int childIndex;

  Element(Kind kind, PageFinder<?, E, ?> finder, PageContext<?> pageContext, Locator locator) {
    this.finder = Optional.ofNullable(finder)
      .orElseGet(() -> DriverSessionManager.inThisTestThread()
        .getCurrentDriver()
        .finder());
    this.pageContext = Optional.ofNullable(pageContext)
      .orElseGet(() -> {
        if (ObjectUtils.isNotEmpty(finder)) {
          return finder.getCurrentPage();
        }
        return null;
      });
    this.locator = locator;
    this.parent = null;
    this.instance = null;
    this.kind = kind;
  }

  Element(PageFinder<?, E, ?> finder, PageContext<?> pageContext, E instance) {
    this.finder = finder;
    this.pageContext = pageContext;
    this.locator = null;
    this.parent = null;
    this.instance = instance;
    this.kind = Kind.SELF;
  }

  Element(PageFinder<?, E, ?> finder, PageContext<?> pageContext, Element<E> parent, Locator locator) {
    this.finder = Optional.ofNullable(finder)
      .orElseGet(() -> DriverSessionManager.inThisTestThread()
        .getCurrentDriver()
        .finder());
    this.pageContext = Optional.ofNullable(pageContext)
      .orElseGet(() -> {
        if (ObjectUtils.isNotEmpty(finder)) {
          return finder.getCurrentPage();
        }
        return null;
      });
    this.locator = locator;
    this.parent = parent;
    this.instance = null;
    this.kind = Kind.SELF;
  }

  public static ElementContext of(String locator) {
    return new ElementContext(Locator.parse(locator));
  }

  public static ElementContext of(Locator locator) {
    return new ElementContext(locator);
  }

  public static <E> Element<E> instance(PageFinder<?, E, ?> finder, PageContext<?> pageContext,
    E instance) {
    return new Element<>(finder, pageContext, instance);
  }

  Element<E> childIndex(int childIndex) {
    this.childIndex = childIndex;
    return this;
  }

  public Element<E> child() {
    return this.child;
  }

  public Element<E> withChild(Element<E> child) {
    if (ObjectUtils.isNotEmpty(child)) {
      Element<E> grandChild = child.child();
      this.child = new Element<>(this.finder, this.pageContext, this, child.locator).withChild(grandChild);
    } else {
      this.child = null;
    }

    return this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> T location() {
    if (locator == null || (locator.getValue() == null)) {
      return null;
    }
    String elementName = locator.getValue();
    finder.setSuppressLog(true);
    T result;
    if (ObjectUtils.isNotEmpty(pageContext)) {
      result = (T) ((PageFinder) finder).getLocator(pageContext, elementName);
      if (result == null) {
        result = (T) ((PageFinder) finder).getLocator(pageContext, locator);
      }
    } else {
      result = (T) finder.getLocator(elementName);
    }
    finder.setSuppressLog(false);
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> T one() throws Exception {
    if (ObjectUtils.isNotEmpty(instance)) {
      try {
        return (T) instance;
      } catch (Exception ignored) {
        // fall through
      }
    }
    if (ObjectUtils.isEmpty(parent) && (locator == null || locator.getValue() == null)) {
      return null;
    }
    finder.setSuppressLog(true);
    T result;
    if (ObjectUtils.isNotEmpty(parent)) {
      result = switch (kind) {
        case FOLLOWING_SIBLING, SIBLINGS -> (T) finder.getFollowingSiblingElement(parent.one(), location());
        case PRECEDING_SIBLING -> (T) finder.getPrecedingSiblingElement(parent.one(), location());
        case CHILD -> (T) finder.getChildNode(parent.one(), location(), childIndex);
        case PARENT -> parent.one();
        default -> (T) ((PageFinder) finder).getElementWithRoot(parent.one(), location());
      };
    } else {
      String elementName = locator.getValue();
      if (ObjectUtils.isNotEmpty(pageContext)) {
        result = (T) ((PageFinder) finder).getElement(pageContext, elementName)
          .get();
      } else {
        result = (T) finder.getElement(elementName)
          .get();
      }
      result = switch (kind) {
        case FOLLOWING_SIBLING, SIBLINGS -> (T) finder.getFollowingSiblingElement((E) result);
        case PRECEDING_SIBLING -> (T) finder.getPrecedingSiblingElement((E) result);
        case CHILD -> (T) finder.getChildNode((E) result, childIndex);
        default -> result;
      };
    }

    try {
      instance = (E) result;
    } catch (Exception ignored) {
      // ignore
    }
    finder.setSuppressLog(false);
    return result;
  }

  public <T> T one(Duration duration) throws Exception {
    if (ObjectUtils.isNotEmpty(pageContext)) {
      finder.setCurrentPage(pageContext);
    }
    AtomicReference<Throwable> lastError = new AtomicReference<>();
    AtomicReference<T> result = new AtomicReference<>();
    try {
      Awaitility.await()
        .pollInSameThread()
        .atMost(duration.plusMillis(1))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> {
          try {
            T temp = one();
            result.set(temp);
            return !ObjectUtils.isEmpty(temp);
          } catch (Exception err) {
            lastError.set(err);
            result.set(null);
            return false;
          }
        });
      return result.get();
    } catch (ConditionTimeoutException e) {
      ValueUnit valueUnit = DurationParser.toValueUnit(duration);
      Throwable cause = lastError.get();
      TimeoutException wrapped = new TimeoutException(
        "Retry failed after " + valueUnit.getValue() + " " + valueUnit.getUnit()
          .name() + ". Last error: " + (cause != null ? cause.getMessage() : "unspecified"));
      if (cause != null) {
        wrapped.initCause(cause);
      }
      throw wrapped;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> List<T> all() throws Exception {
    if (ObjectUtils.isEmpty(parent) && (locator == null || locator.getValue() == null)) {
      return List.of();
    }
    finder.setSuppressLog(true);
    List<T> result;
    if (ObjectUtils.isNotEmpty(parent)) {
      result = switch (kind) {
        case FOLLOWING_SIBLING -> (List<T>) finder.getFollowingSiblingElements(parent.one(), location());
        case SIBLINGS -> (List<T>) finder.getSiblings(parent.one(), location());
        case PRECEDING_SIBLING -> (List<T>) finder.getPrecedingSiblingElements(parent.one(), location());
        default -> (List<T>) ((PageFinder) finder).getElementsWithRoot(parent.one(), location());
      };
    } else {
      String elementName = locator.getValue();
      if (ObjectUtils.isNotEmpty(pageContext)) {
        result = (List<T>) ((PageFinder) finder).getElements(pageContext, elementName)
          .get();
      } else {
        result = (List<T>) finder.getElements(elementName)
          .get();
      }
      result = switch (kind) {
        case FOLLOWING_SIBLING -> (List<T>) finder.getFollowingSiblingElements((E) result);
        case SIBLINGS -> (List<T>) finder.getSiblings((E) result);
        case PRECEDING_SIBLING -> (List<T>) finder.getPrecedingSiblingElements((E) result);
        default -> result;
      };
    }
    finder.setSuppressLog(false);
    return result;
  }

  public <T> List<T> all(Duration duration) throws Exception {
    if (ObjectUtils.isNotEmpty(pageContext)) {
      finder.setCurrentPage(pageContext);
    }
    AtomicReference<Throwable> lastError = new AtomicReference<>();
    AtomicReference<List<T>> result = new AtomicReference<>();
    try {
      Awaitility.await()
        .pollInSameThread()
        .atMost(duration.plusMillis(1))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> {
          try {
            List<T> temp = all();
            result.set(temp);
            return !ObjectUtils.isEmpty(temp);
          } catch (Exception err) {
            lastError.set(err);
            result.set(null);
            return false;
          }
        });
      return result.get();
    } catch (ConditionTimeoutException e) {
      ValueUnit valueUnit = DurationParser.toValueUnit(duration);
      Throwable cause = lastError.get();
      TimeoutException wrapped = new TimeoutException(
        "Retry failed after " + valueUnit.getValue() + " " + valueUnit.getUnit()
          .name() + ". Last error: " + (cause != null ? cause.getMessage() : "unspecified"));
      if (cause != null) {
        wrapped.initCause(cause);
      }
      throw wrapped;
    }
  }

  enum Kind {
    SELF, PARENT, PRECEDING_SIBLING, FOLLOWING_SIBLING, SIBLINGS, CHILD
  }


  public static class ElementContext {
    private final Locator locator;
    private Kind kind;
    private int childIndex;
    private PageFinder<?, ?, ?> finder;
    private PageContext<?> pageContext;

    public ElementContext(Locator locator) {
      this.locator = locator;
      this.kind = Kind.SELF;
    }

    public ElementContext parent() {
      this.kind = Kind.PARENT;
      return this;
    }

    public ElementContext child() {
      this.kind = Kind.CHILD;
      this.childIndex = 0;
      return this;
    }

    public ElementContext child(int childIndex) {
      this.kind = Kind.CHILD;
      this.childIndex = childIndex;
      return this;
    }

    public ElementContext precedingSibling() {
      this.kind = Kind.PRECEDING_SIBLING;
      return this;
    }

    public ElementContext followingSibling() {
      this.kind = Kind.FOLLOWING_SIBLING;
      return this;
    }

    public ElementContext siblings() {
      this.kind = Kind.SIBLINGS;
      return this;
    }

    public ElementContext on(PageContext<?> pageContext) {
      this.pageContext = pageContext;
      return this;
    }

    public ElementContext by(PageFinder<?, ?, ?> finder) {
      this.finder = finder;
      return this;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Element build() {
      return new Element(kind, finder, pageContext, locator).childIndex(childIndex);
    }
  }
}
