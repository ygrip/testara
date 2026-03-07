package io.github.ygrip.testara.core.time;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultProperties;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * <p>DateHelper class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public final class DateHelper {
  private static final String UNWANTED_CHAR = "[\\t\\n\\r\\f\\v]";
  private static final String DEFAULT_SOURCE_FORMAT = getDefaultSourceFormat();
  private static final String DEFAULT_TARGET_FORMAT = getDefaultTargetFormat();
  private static final ZoneId DEFAULT_ZONE_ID = getDefaultZoneId();

  private static DefaultProperties getConfig() {
    return TestFramework.context().get(DefaultProperties.class);
  }

  private static ZoneId getDefaultZoneId() {
    ZoneId zoneId = null;
    try {
      String timezone = getConfig().getTimeZone();
      if (StringUtils.isNotBlank(timezone)) {
        zoneId = ZoneId.of(timezone);
      }
    } catch (Exception ignored) {

    }
    if (ObjectUtils.isEmpty(zoneId)) {
      zoneId = ZoneId.systemDefault();
    }
    return zoneId;
  }

  /**
   * <p>getZoneId.</p>
   *
   * @return a {@link ZoneId} object.
   */
  public static ZoneId getZoneId() {
    return DEFAULT_ZONE_ID;
  }

  private static String getDefaultSourceFormat() {
    String source = null;
    try {
      source = getConfig().getSourceDateFormat();
    } catch (Exception ignored) {

    }
    return StringUtils.isBlank(source) ? "yyyy-MM-dd HH:mm:ss" : source;
  }

  private static String getDefaultTargetFormat() {
    String target = null;
    try {
      target = getConfig().getTargetDateFormat();
    } catch (Exception ignored) {

    }
    return StringUtils.isBlank(target) ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : target;
  }

  /**
   * <p>getCurrentDateTimeEpoch.</p>
   *
   * @return a {@link Long} object.
   */
  public static Long getCurrentDateTimeEpoch() {
    return ZonedDateTime.now(DEFAULT_ZONE_ID).toInstant().toEpochMilli();
  }

  /**
   * <p>convertToDateTimeEpoch.</p>
   *
   * @param date a {@link Date} object.
   * @return a {@link Long} object.
   */
  public static Long convertToDateTimeEpoch(Date date) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_SOURCE_FORMAT);
    return Objects.requireNonNull(convertToInstantTime(ZonedDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE_ID)
        .format(formatter), DEFAULT_SOURCE_FORMAT)).toEpochMilli();
  }

  /**
   * <p>convertToDateTimeEpoch.</p>
   *
   * @param date a {@link String} object.
   * @return a {@link Long} object.
   */
  public static Long convertToDateTimeEpoch(String date) {
    return Objects.requireNonNull(convertToInstantTime(date, DEFAULT_SOURCE_FORMAT)).toEpochMilli();
  }

  /**
   * <p>convertToDateTimeEpoch.</p>
   *
   * @param date   a {@link String} object.
   * @param format a {@link String} object.
   * @return a {@link Long} object.
   */
  public static Long convertToDateTimeEpoch(String date, String format) {
    return Objects.requireNonNull(convertToInstantTime(date, format)).toEpochMilli();
  }

  /**
   * <p>convertToInstantTime.</p>
   *
   * @param date   a {@link String} object.
   * @param format a {@link String} object.
   * @return a {@link Instant} object.
   */
  public static Instant convertToInstantTime(String date, String format) {
    SimpleDateFormat formatter = new SimpleDateFormat(format.trim().replaceAll(UNWANTED_CHAR, ""));
    try {
      formatter.setTimeZone(TimeZone.getTimeZone(DEFAULT_ZONE_ID));
      return formatter.parse(date.trim().replaceAll(UNWANTED_CHAR, "")).toInstant();
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * <p>getDate.</p>
   *
   * @param date         a {@link String} object.
   * @param sourceFormat a {@link String} object.
   * @param targetFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getDate(String date, String sourceFormat, String targetFormat) {
    date = date.trim().replaceAll(UNWANTED_CHAR, "");
    targetFormat = targetFormat.trim().replaceAll(UNWANTED_CHAR, "");
    sourceFormat = sourceFormat.trim().replaceAll(UNWANTED_CHAR, "");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(targetFormat);
    Instant dateInstant = convertToInstantTime(date, sourceFormat);
    if (dateInstant != null) {
      return ZonedDateTime.ofInstant(dateInstant, DEFAULT_ZONE_ID).format(formatter);
    } else {
      return date;
    }
  }

  /**
   * <p>getDate.</p>
   *
   * @param date         a {@link String} object.
   * @param sourceFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getDate(String date, String sourceFormat) {
    return getDate(date, sourceFormat, DEFAULT_TARGET_FORMAT);
  }

  /**
   * <p>getDate.</p>
   *
   * @param date a {@link Date} object.
   * @return a {@link String} object.
   */
  public static String getDate(Date date) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_SOURCE_FORMAT);
    return getDate(ZonedDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE_ID).format(formatter),
        DEFAULT_SOURCE_FORMAT,
        DEFAULT_TARGET_FORMAT);
  }

  /**
   * <p>getDate.</p>
   *
   * @param date         a {@link Date} object.
   * @param targetFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getDate(Date date, String targetFormat) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_SOURCE_FORMAT);
    return getDate(ZonedDateTime.ofInstant(date.toInstant(), DEFAULT_ZONE_ID).format(formatter),
        DEFAULT_SOURCE_FORMAT,
        targetFormat);
  }

  /**
   * <p>getDate.</p>
   *
   * @param timeMilis    a long.
   * @param targetFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getDate(long timeMilis, String targetFormat) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_SOURCE_FORMAT);
    return getDate(ZonedDateTime.ofInstant(new Date(timeMilis).toInstant(), DEFAULT_ZONE_ID).format(formatter),
        DEFAULT_SOURCE_FORMAT,
        targetFormat);
  }

  /**
   * <p>getDate.</p>
   *
   * @param timeMilis a long.
   * @return a {@link String} object.
   */
  public static String getDate(long timeMilis) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_SOURCE_FORMAT);
    return getDate(ZonedDateTime.ofInstant(new Date(timeMilis).toInstant(), DEFAULT_ZONE_ID).format(formatter),
        DEFAULT_SOURCE_FORMAT,
        DEFAULT_TARGET_FORMAT);
  }

  /**
   * <p>getDate.</p>
   *
   * @param date a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getDate(String date) {
    return getDate(date, DEFAULT_SOURCE_FORMAT, DEFAULT_TARGET_FORMAT);
  }

  /**
   * <p>getCurrentDate.</p>
   *
   * @param targetFormat a {@link String} object.
   * @return a {@link String} object.
   */
  public static String getCurrentDate(String targetFormat) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(targetFormat.trim().replaceAll(UNWANTED_CHAR, ""));
    return ZonedDateTime.now(DEFAULT_ZONE_ID).format(formatter);
  }

  /**
   * <p>getCurrentDate.</p>
   *
   * @return a {@link String} object.
   */
  public static String getCurrentDate() {
    return getCurrentDate(DEFAULT_TARGET_FORMAT);
  }

  public static int getDayOfMonth() {
    DateTimeZone timezone = DateTimeZone.forID(getZoneId().getId());
    return DateTime.now().withZone(timezone).dayOfMonth().get();
  }

  public static int getDayOfMonth(Long epoch) {
    return new DateTime(epoch).dayOfMonth().get();
  }

  public static int getLastDayOfMonth() {
    DateTimeZone timezone = DateTimeZone.forID(getZoneId().getId());
    return DateTime.now().withZone(timezone).dayOfMonth().getMaximumValue();
  }

  public static int getLastDayOfMonth(Long epoch) {
    return new DateTime(epoch).dayOfMonth().getMaximumValue();
  }

  public static int getMonth() {
    DateTimeZone timezone = DateTimeZone.forID(getZoneId().getId());
    return DateTime.now().withZone(timezone).monthOfYear().get();
  }

  public static int getMonth(Long epoch) {
    return new DateTime(epoch).monthOfYear().get();
  }

  public static int getYear() {
    DateTimeZone timezone = DateTimeZone.forID(getZoneId().getId());
    return DateTime.now().withZone(timezone).year().get();
  }

  public static int getYear(Long epoch) {
    return new DateTime(epoch).year().get();
  }
}
