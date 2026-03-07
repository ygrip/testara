package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.List;

/**
 * <p>RandomStringCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "random", overwrite = true)
public class RandomStringCommand implements CommandLogic<String> {
  private static final String UPPER_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
  private static final String LOWER_ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz1234567890";
  private static final String ALL_ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
  private static final String UPPER_ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String ALL_ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String LOWER_ALPHA = "abcdefghijklmnopqrstuvwxyz";
  private static final String NUMERIC = "1234567890";
  private static final SecureRandom random = new SecureRandom();

  /**
   * <p>getRandomString.</p>
   *
   * @param size a {@link Integer} object.
   * @param mode a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getRandomString(Integer size, String mode) {
    size = size < 1 ? 1 : size;
    String SALTCHARS = mode;
    if (mode.equalsIgnoreCase("u_alpha")) {
      SALTCHARS = UPPER_ALPHA;
    } else if (mode.equalsIgnoreCase("l_alpha")) {
      SALTCHARS = LOWER_ALPHA;
    } else if (mode.equalsIgnoreCase("alpha")) {
      SALTCHARS = ALL_ALPHA;
    } else if (mode.equalsIgnoreCase("u_alphanumeric")) {
      SALTCHARS = UPPER_ALPHANUMERIC;
    } else if (mode.equalsIgnoreCase("l_alphanumeric")) {
      SALTCHARS = LOWER_ALPHANUMERIC;
    } else if (mode.equalsIgnoreCase("alphanumeric")) {
      SALTCHARS = ALL_ALPHANUMERIC;
    } else if (mode.equalsIgnoreCase("numeric")) {
      SALTCHARS = NUMERIC;
    }
    if (StringUtils.isBlank(SALTCHARS)) {
      return "";
    }

    StringBuilder sb = new StringBuilder(size);
    for (int i = 0; i < size; i++) {
      int rndCharAt = random.nextInt(SALTCHARS.length());
      char rndChar = SALTCHARS.charAt(rndCharAt);
      sb.append(rndChar);
    }

    return sb.toString();
  }

  /**
   * <p>getRandomString.</p>
   *
   * @param size a {@link Integer} object.
   * @return a {@link String} object.
   */
  public static String getRandomString(Integer size) {
    return getRandomString(size, ALL_ALPHANUMERIC);
  }

  /**
   * <p>getRandomString.</p>
   *
   * @return a {@link String} object.
   */
  public static String getRandomString() {
    return getRandomString(1, ALL_ALPHANUMERIC);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String execute(List<Object> parameters) {
    return ObjectUtils.isEmpty(parameters) ?
        getRandomString() :
        parameters.size() == 1 ?
            getRandomString(Integer.parseInt(String.valueOf(parameters.get(0)))) :
            getRandomString(Integer.parseInt(String.valueOf(parameters.get(0))), String.valueOf(parameters.get(1)));
  }
}
