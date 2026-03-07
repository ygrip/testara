package io.github.ygrip.testara.core.time;

import io.github.ygrip.testara.core.model.ValueUnit;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
  // matches "<number><unit>"
  private static final Pattern PATTERN = Pattern.compile("^(\\d+)([a-zA-Z]+)$");
  private final static PeriodFormatter TIME_FORMATTER = (new PeriodFormatterBuilder()).appendDays()
      .appendSeparator(" ")
      .appendHours()
      .appendSuffix("H")
      .appendSeparator(" ")
      .appendMinutes()
      .appendSuffix("m")
      .appendSeparator(" ")
      .printZeroRarelyFirst()
      .appendSeconds()
      .appendSuffix("s")
      .appendSeparator(" ")
      .printZeroIfSupported()
      .appendMillis()
      .appendSuffix("ms")
      .toFormatter();

  private DurationParser() {
  }

  public static Duration parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Duration string cannot be null/empty");
    }

    Matcher matcher = PATTERN.matcher(text.trim().toLowerCase(Locale.ROOT));
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid duration format: " + text);
    }

    long amount = Long.parseLong(matcher.group(1));
    String unit = matcher.group(2);

    switch (unit) {
      case "ns":
        return Duration.ofNanos(amount);
      case "us":
        return Duration.ofNanos(amount * 1_000); // microseconds
      case "ms":
        return Duration.ofMillis(amount);
      case "s":
        return Duration.ofSeconds(amount);
      case "m":
        return Duration.ofMinutes(amount);
      case "h":
        return Duration.ofHours(amount);
      case "d":
        return Duration.ofDays(amount);
      default:
        throw new IllegalArgumentException("Unsupported duration unit: " + unit);
    }
  }

  public static ValueUnit toValueUnit(Duration duration) {
    long seconds = duration.getSeconds();
    int nanos = duration.getNano();

    if (seconds % 86400 == 0 && nanos == 0) {
      return new ValueUnit(seconds / 86400, TimeUnit.DAYS);
    } else if (seconds % 3600 == 0 && nanos == 0) {
      return new ValueUnit(seconds / 3600, TimeUnit.HOURS);
    } else if (seconds % 60 == 0 && nanos == 0) {
      return new ValueUnit(seconds / 60, TimeUnit.MINUTES);
    } else if (nanos == 0) {
      return new ValueUnit(seconds, TimeUnit.SECONDS);
    } else if (nanos % 1_000_000 == 0) {
      return new ValueUnit(nanos / 1_000_000, TimeUnit.MILLISECONDS);
    } else if (nanos % 1_000 == 0) {
      return new ValueUnit(nanos / 1_000, TimeUnit.MICROSECONDS);
    } else {
      return new ValueUnit(nanos, TimeUnit.NANOSECONDS);
    }
  }

  public static TemporalUnit toTemporalUnit(TimeUnit timeUnit) {
    switch (timeUnit) {
      case NANOSECONDS:
        return ChronoUnit.NANOS;
      case MICROSECONDS:
        return ChronoUnit.MICROS;
      case MILLISECONDS:
        return ChronoUnit.MILLIS;
      case SECONDS:
        return ChronoUnit.SECONDS;
      case MINUTES:
        return ChronoUnit.MINUTES;
      case HOURS:
        return ChronoUnit.HOURS;
      case DAYS:
        return ChronoUnit.DAYS;
      default:
        throw new IllegalArgumentException("Unsupported TimeUnit: " + timeUnit);
    }
  }

  /**
   * <p>formatDuration.</p>
   * will convert nano seconds into human readable format
   *
   * @param duration a {@link Long} object.
   */
  public static String formatDuration(long duration) {
    return TIME_FORMATTER.print(new Period(0L, duration / 1000000L));
  }

  /**
   * <p>formatDuration.</p>
   * will convert duration object into human readable format
   *
   * @param duration a {@link Duration} object.
   */
  public static String formatDuration(Duration duration) {
    return TIME_FORMATTER.print(new Period(0L, duration.getNano() / 1000000L));
  }
}
