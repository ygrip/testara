package io.github.ygrip.testara.core.time;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.model.ValueUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("durationParser")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class DurationParserTests extends BaseTests {

  // ==================== parse tests ====================

  @Test
  public void parse_withNanoseconds_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("1000ns");
    assertThat(result.toNanos(), equalTo(1000L));
  }

  @Test
  public void parse_withMicroseconds_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("500us");
    assertThat(result.toNanos(), equalTo(500_000L));
  }

  @Test
  public void parse_withMilliseconds_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("100ms");
    assertThat(result.toMillis(), equalTo(100L));
  }

  @Test
  public void parse_withSeconds_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("30s");
    assertThat(result.getSeconds(), equalTo(30L));
  }

  @Test
  public void parse_withMinutes_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("5m");
    assertThat(result.toMinutes(), equalTo(5L));
  }

  @Test
  public void parse_withHours_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("2h");
    assertThat(result.toHours(), equalTo(2L));
  }

  @Test
  public void parse_withDays_shouldReturnCorrectDuration() {
    Duration result = DurationParser.parse("7d");
    assertThat(result.toDays(), equalTo(7L));
  }

  @Test
  public void parse_withUpperCase_shouldParseCorrectly() {
    Duration result = DurationParser.parse("10S");
    assertThat(result.getSeconds(), equalTo(10L));
  }

  @Test
  public void parse_withMixedCase_shouldParseCorrectly() {
    Duration result = DurationParser.parse("5Ms");
    assertThat(result.toMillis(), equalTo(5L));
  }

  @Test
  public void parse_withWhitespace_shouldTrimAndParse() {
    Duration result = DurationParser.parse("  10s  ");
    assertThat(result.getSeconds(), equalTo(10L));
  }

  @Test
  public void parse_withNull_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(null));
  }

  @Test
  public void parse_withEmptyString_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
  }

  @Test
  public void parse_withBlankString_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("   "));
  }

  @Test
  public void parse_withInvalidFormat_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("invalid"));
  }

  @Test
  public void parse_withUnsupportedUnit_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10x"));
  }

  @Test
  public void parse_withZeroValue_shouldReturnZeroDuration() {
    Duration result = DurationParser.parse("0s");
    assertThat(result.isZero(), is(true));
  }

  @Test
  public void parse_withLargeValue_shouldParseCorrectly() {
    Duration result = DurationParser.parse("999999999ms");
    assertThat(result.toMillis(), equalTo(999999999L));
  }

  // ==================== toValueUnit tests ====================

  @Test
  public void toValueUnit_withDays_shouldReturnDaysUnit() {
    Duration duration = Duration.ofDays(3);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(3L));
    assertThat(result.getUnit(), equalTo(TimeUnit.DAYS));
  }

  @Test
  public void toValueUnit_withHours_shouldReturnHoursUnit() {
    Duration duration = Duration.ofHours(5);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(5L));
    assertThat(result.getUnit(), equalTo(TimeUnit.HOURS));
  }

  @Test
  public void toValueUnit_withMinutes_shouldReturnMinutesUnit() {
    Duration duration = Duration.ofMinutes(30);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(30L));
    assertThat(result.getUnit(), equalTo(TimeUnit.MINUTES));
  }

  @Test
  public void toValueUnit_withSeconds_shouldReturnSecondsUnit() {
    Duration duration = Duration.ofSeconds(45);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(45L));
    assertThat(result.getUnit(), equalTo(TimeUnit.SECONDS));
  }

  @Test
  public void toValueUnit_withMilliseconds_shouldReturnMillisecondsUnit() {
    Duration duration = Duration.ofMillis(500);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(500L));
    assertThat(result.getUnit(), equalTo(TimeUnit.MILLISECONDS));
  }

  @Test
  public void toValueUnit_withMicroseconds_shouldReturnMicrosecondsUnit() {
    Duration duration = Duration.ofNanos(500_000);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(500L));
    assertThat(result.getUnit(), equalTo(TimeUnit.MICROSECONDS));
  }

  @Test
  public void toValueUnit_withNanoseconds_shouldReturnNanosecondsUnit() {
    Duration duration = Duration.ofNanos(750);
    ValueUnit result = DurationParser.toValueUnit(duration);

    assertThat(result.getValue(), equalTo(750L));
    assertThat(result.getUnit(), equalTo(TimeUnit.NANOSECONDS));
  }

  // ==================== toTemporalUnit tests ====================

  @Test
  public void toTemporalUnit_withNanoseconds_shouldReturnChronoNanos() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.NANOSECONDS);
    assertThat(result, equalTo(ChronoUnit.NANOS));
  }

  @Test
  public void toTemporalUnit_withMicroseconds_shouldReturnChronoMicros() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.MICROSECONDS);
    assertThat(result, equalTo(ChronoUnit.MICROS));
  }

  @Test
  public void toTemporalUnit_withMilliseconds_shouldReturnChronoMillis() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.MILLISECONDS);
    assertThat(result, equalTo(ChronoUnit.MILLIS));
  }

  @Test
  public void toTemporalUnit_withSeconds_shouldReturnChronoSeconds() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.SECONDS);
    assertThat(result, equalTo(ChronoUnit.SECONDS));
  }

  @Test
  public void toTemporalUnit_withMinutes_shouldReturnChronoMinutes() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.MINUTES);
    assertThat(result, equalTo(ChronoUnit.MINUTES));
  }

  @Test
  public void toTemporalUnit_withHours_shouldReturnChronoHours() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.HOURS);
    assertThat(result, equalTo(ChronoUnit.HOURS));
  }

  @Test
  public void toTemporalUnit_withDays_shouldReturnChronoDays() {
    TemporalUnit result = DurationParser.toTemporalUnit(TimeUnit.DAYS);
    assertThat(result, equalTo(ChronoUnit.DAYS));
  }

  // ==================== formatDuration tests ====================

  @Test
  public void formatDuration_withNanoseconds_shouldReturnFormattedString() {
    // 1 second in nanoseconds
    String result = DurationParser.formatDuration(1_000_000_000L);
    assertThat(result, containsString("s"));
  }

  @Test
  public void formatDuration_withMilliseconds_shouldReturnFormattedString() {
    // 500 milliseconds in nanoseconds
    String result = DurationParser.formatDuration(500_000_000L);
    assertThat(result, containsString("ms"));
  }

  @Test
  public void formatDuration_withMinutes_shouldReturnFormattedString() {
    // 2 minutes in nanoseconds
    String result = DurationParser.formatDuration(120_000_000_000L);
    assertThat(result, containsString("m"));
  }

  @Test
  public void formatDuration_withHours_shouldReturnFormattedString() {
    // 1 hour in nanoseconds
    String result = DurationParser.formatDuration(3_600_000_000_000L);
    assertThat(result, containsString("H"));
  }

  @Test
  public void formatDuration_withDurationObject_shouldReturnFormattedString() {
    Duration duration = Duration.ofMillis(500);
    String result = DurationParser.formatDuration(duration);
    assertThat(result, containsString("ms"));
  }

  @Test
  public void formatDuration_withZero_shouldReturnFormattedString() {
    String result = DurationParser.formatDuration(0L);
    assertThat(result, notNullValue());
  }
}
