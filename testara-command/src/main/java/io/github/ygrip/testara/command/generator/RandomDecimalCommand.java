package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.math.RoundingMode;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;

/**
 * <p>RandomDecimalCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "random_decimal", alias = {"random decimal", "random float", "random_float"}, overwrite = true)
public class RandomDecimalCommand implements CommandLogic<Double> {
  /**
   * <p>getRandomDecimal.</p>
   *
   * @param min a int.
   * @param max a int.
   * @return a double.
   */
  public static double getRandomDecimal(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("max must be greater than min");
    } else if (max == min) {
      return max;
    }

    SecureRandom r = new SecureRandom();
    return min + r.nextDouble() * (max - min);
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
  public Double execute(List<Object> parameters) throws ParseException {
    DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance();
    DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
    char separator = symbols.getDecimalSeparator();
    int min =
        ObjectUtils.isEmpty(parameters) ? 0 : NumberFormat.getInstance().parse(parameters.get(0).toString()).intValue();
    int max = ObjectUtils.isEmpty(parameters) || parameters.size() < 2 ?
        min :
        NumberFormat.getInstance().parse(parameters.get(1).toString()).intValue();
    int precission = ObjectUtils.isEmpty(parameters) || parameters.size() < 3 ?
        0 :
        NumberFormat.getInstance().parse(parameters.get(2).toString()).intValue();
    if (precission < 1) {
      return getRandomDecimal(min, max);
    } else {
      String pattern = String.format("#%s%s", separator, String.join("", Collections.nCopies(precission, "#")));
      DecimalFormat df = new DecimalFormat(pattern);
      df.setRoundingMode(RoundingMode.CEILING);
      return Double.valueOf(df.format(getRandomDecimal(min, max)));
    }
  }
}
