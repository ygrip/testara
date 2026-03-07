package io.github.ygrip.testara.command.time;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.time.DateHelper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

/**
 * <p>TimeTravelerCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "timetravel", alias = "tt", overwrite = true)
public class TimeTravelerCommand implements CommandLogic<String> {
  private static final String DEFAULT_TARGET_FORMAT = getDefaultTargetFormat();

  private static String getDefaultTargetFormat() {
    String target = null;
    try {
      target = TestFramework.context().get(DefaultProperties.class).getTargetDateFormat();
    } catch (Exception ignored) {

    }
    return StringUtils.isBlank(target) ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : target;
  }

  private static String timeTraveling(Long epoch, Integer addition, String mode, String targetFormat) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(targetFormat);

    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(epoch);

    cal.add(getTargetMode(mode), addition);

    return ZonedDateTime.ofInstant(cal.toInstant(), DateHelper.getZoneId()).format(formatter);
  }

  private static String timeTraveling(Integer addition, String mode) {
    return timeTraveling(DateHelper.getCurrentDateTimeEpoch(), addition, mode, DEFAULT_TARGET_FORMAT);
  }

  private static String timeTraveling(Integer addition, String mode, String targetFormat) {
    return timeTraveling(DateHelper.getCurrentDateTimeEpoch(), addition, mode, targetFormat);
  }

  private static String timeTraveling(String date, Integer addition, String mode, String targetFormat) {
    return timeTraveling(DateHelper.convertToDateTimeEpoch(date), addition, mode, targetFormat);
  }

  private static String timeTraveling(String date, Integer addition, String mode) {
    return timeTraveling(DateHelper.convertToDateTimeEpoch(date), addition, mode, DEFAULT_TARGET_FORMAT);
  }

  /**
   * <p>timeTraveling.</p>
   *
   * @param date         a {@link String} object.
   * @param addition     a {@link Integer} object.
   * @param mode         a {@link String} object.
   * @param sourceFormat a {@link String} object.
   * @param targetFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String timeTraveling(String date,
      Integer addition,
      String mode,
      String sourceFormat,
      String targetFormat) {
    return timeTraveling(DateHelper.convertToDateTimeEpoch(date, sourceFormat), addition, mode, targetFormat);
  }

  private static int getTargetMode(String mode) {
    int targetMode;
    mode = mode.toUpperCase();
    switch (mode) {
      case "SECONDS":
        targetMode = Calendar.SECOND;
        break;
      case "MINUTE":
        targetMode = Calendar.MINUTE;
        break;
      case "HOUR":
        targetMode = Calendar.HOUR;
        break;
      case "DAY":
        targetMode = Calendar.DATE;
        break;
      case "WEEK":
        targetMode = Calendar.WEEK_OF_YEAR;
        break;
      case "MONTH":
        targetMode = Calendar.MONTH;
        break;
      case "YEAR":
        targetMode = Calendar.YEAR;
        break;
      default:
        targetMode = Calendar.DATE;
        break;
    }
    return targetMode;
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
    return ObjectUtils.isEmpty(parameters) || parameters.size() < 2 ?
        DateHelper.getCurrentDate() :
        parameters.size() == 2 ?
            timeTraveling(Integer.parseInt(String.valueOf(parameters.get(0))), String.valueOf(parameters.get(1))) :
            parameters.size() == 3 ?
                String.valueOf(parameters.get(0)).matches("-?\\d+") ?
                    timeTraveling(Integer.parseInt(String.valueOf(parameters.get(0))),
                        String.valueOf(parameters.get(1)),
                        String.valueOf(parameters.get(2))) :
                    timeTraveling(String.valueOf(parameters.get(0)),
                        Integer.parseInt(String.valueOf(parameters.get(1))),
                        String.valueOf(parameters.get(2))) :
                parameters.size() == 4 ?
                    timeTraveling(String.valueOf(parameters.get(0)),
                        Integer.parseInt(String.valueOf(parameters.get(1))),
                        String.valueOf(parameters.get(2)),
                        String.valueOf(parameters.get(3))) :
                    timeTraveling(String.valueOf(parameters.get(0)),
                        Integer.parseInt(String.valueOf(parameters.get(1))),
                        String.valueOf(parameters.get(2)),
                        String.valueOf(parameters.get(3)),
                        String.valueOf(parameters.get(4)));
  }
}
