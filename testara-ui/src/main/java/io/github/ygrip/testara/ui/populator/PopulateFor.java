package io.github.ygrip.testara.ui.populator;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

import lombok.extern.log4j.Log4j2;

/**
 * <p>PopulateFor class.</p>
 *
 * @author yunaz.ramadhan on 12/28/2019
 * @version $Id: $Id
 */
@Log4j2
public final class PopulateFor {

  private PopulateFor() {
  }

  /**
   * <p>one.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link SingleElementPopulator} object.
   */
  public static SingleElementPopulator one(Locator selector) {
    return new Builder(Element.of(selector)
      .build()).one();
  }

  /**
   * <p>one.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link SingleElementPopulator} object.
   */
  public static SingleElementPopulator one(String selector) {
    return new Builder(Element.of(selector)
      .build()).one();
  }

  /**
   * <p>all.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link SingleElementPopulator} object.
   */
  public static SingleElementPopulator one(Element.ElementContext selector) {
    return new Builder(selector.build()).one();
  }

  /**
   * <p>one.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link MultipleElementPopulator} object.
   */
  public static MultipleElementPopulator all(Locator selector) {
    return new Builder(Element.of(selector)
      .build()).all();
  }

  /**
   * <p>one.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link MultipleElementPopulator} object.
   */
  public static MultipleElementPopulator all(String selector) {
    return new Builder(Element.of(selector)
      .build()).all();
  }

  /**
   * <p>one.</p>
   *
   * @param selector a {@link Locator} object.
   * @return a {@link MultipleElementPopulator} object.
   */
  public static MultipleElementPopulator all(Element.ElementContext selector) {
    return new Builder(selector.build()).all();
  }

  public static class Builder {
    private final Element locator;
    private final Actor actor;
    private final DriverSession<?> session;

    Builder(Element locator) {
      this.locator = locator;
      this.actor = ActorManager.currentActor();
      this.session = DriverSessionManager.inThisTestThread()
        .getCurrentDriver();
    }

    SingleElementPopulator one() {
      log.info("Populating element {}", locator.getLocator());
      return new SingleElementPopulator(actor, session, locator);
    }

    MultipleElementPopulator all() {
      log.info("Populating list of element {}", locator.getLocator());
      return new MultipleElementPopulator(actor, session, locator);
    }
  }
}
