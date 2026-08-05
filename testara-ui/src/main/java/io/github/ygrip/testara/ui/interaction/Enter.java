package io.github.ygrip.testara.ui.interaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: enter text into an element.
 * Use {@link #text(String)} then {@link Into#into(String)} or {@link Into#into(Locator)}.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Enter implements Interaction {
  private final String text;
  private final Element locator;
  private final List<CharSequence> followedKeys;

  private Enter(String text, Element locator) {
    this.text = text;
    this.locator = locator;
    this.followedKeys = new ArrayList<>();
  }

  public static Into text(String text) {
    return new Into(text);
  }

  public Enter thenHit(CharSequence... keys) {
    followedKeys.addAll(Arrays.asList(keys));
    return this;
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .enter(text)
      .into(locator);
    if (ObjectUtils.isNotEmpty(followedKeys)) {
      context.interaction()
        .enter(followedKeys.stream()
          .map(CharSequence::toString)
          .collect(Collectors.joining()))
        .into(locator);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new Enter(text, root.withChild(locator).child());
  }

  /**
   * Fluent step to specify target locator.
   */
  public static final class Into {
    private final String text;

    Into(String text) {
      this.text = text;
    }

    public Enter into(String locator) {
      return new Enter(
        text,
        Element.of(locator)
          .build()
      );
    }

    public Enter into(Locator locator) {
      return new Enter(
        text,
        Element.of(locator)
          .build()
      );
    }

    public Enter into(Element.ElementContext locator) {
      return new Enter(text, locator.build());
    }
  }
}
