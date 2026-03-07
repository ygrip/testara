package io.github.ygrip.testara.ui.model;

/**
 * <p>Selector class.</p>
 *
 * @author yunaz.ramadhan
 * @version $Id: $Id
 */
public enum Selector {
  ID(0, "id"), CSS(1, "css"), XPATH(2, "xpath"), CLASS(3, "class"), TAG(4, "tag"), NAME(5, "name"), LINKTEXT(
    6,
    "link-text"
  ), PARTIALLINK(7, "partial-link"), ACCESSIBILITY(
    8,
    "accessibility-id"
  ), ANDROID_UI_AUTOMATOR(9, "android-ui-automator"), IOS_CLASS_CHAIN(10, "ios-class-chain");
  private final String id;
  private final int ordinal;

  Selector(int ordinal, String id) {
    this.ordinal = ordinal;
    this.id = id;
  }

  /**
   * <p>Getter for the field <code>ordinal</code>.</p>
   *
   * @return a int.
   */
  public int getOrdinal() {
    return this.ordinal;
  }

  /**
   * <p>Getter for the field <code>id</code>.</p>
   *
   * @return a {@link String} object.
   */
  public String getId() {
    return this.id;
  }
}
