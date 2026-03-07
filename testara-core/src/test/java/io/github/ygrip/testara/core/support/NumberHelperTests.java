package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("numberHelper")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class NumberHelperTests extends BaseTests {

  // ==================== parseNumber(String) tests ====================

  @Test
  public void parseNumber_withNull_shouldReturnNull() {
    Number result = NumberHelper.parseNumber(null);
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseNumber_withEmptyString_shouldReturnNull() {
    Number result = NumberHelper.parseNumber("");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseNumber_withWhitespaceOnly_shouldReturnNull() {
    Number result = NumberHelper.parseNumber("   ");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseNumber_withInteger_shouldReturnIntegerValue() {
    Number result = NumberHelper.parseNumber("42");
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(42));
  }

  @Test
  public void parseNumber_withNegativeInteger_shouldReturnIntegerValue() {
    Number result = NumberHelper.parseNumber("-123");
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(-123));
  }

  @Test
  public void parseNumber_withLargeNumber_shouldReturnLong() {
    Number result = NumberHelper.parseNumber("9999999999");
    assertThat(result, instanceOf(Long.class));
    assertThat(result.longValue(), equalTo(9999999999L));
  }

  @Test
  public void parseNumber_withDecimalNumber_shouldReturnDouble() {
    // Non-whole decimal numbers return Double
    Number result = NumberHelper.parseNumber("3.14");
    assertThat(result, instanceOf(Double.class));
    assertThat(result.doubleValue(), closeTo(3.14, 0.001));
  }

  @Test
  public void parseNumber_withScientificNotation_shouldReturnLong() {
    // Scientific notation with whole number result returns Long
    Number result = NumberHelper.parseNumber("1.5e2");
    assertThat(result, notNullValue());
    assertThat(result, instanceOf(Long.class));
    assertThat(result.longValue(), equalTo(150L));
  }

  @Test
  public void parseNumber_withScientificNotationUpperCase_shouldReturnLong() {
    // Scientific notation always returns Long for whole numbers
    Number result = NumberHelper.parseNumber("2E3");
    assertThat(result, notNullValue());
    assertThat(result, instanceOf(Long.class));
    assertThat(result.longValue(), equalTo(2000L));
  }

  @Test
  public void parseNumber_withCommas_shouldIgnoreCommas() {
    Number result = NumberHelper.parseNumber("1,000,000");
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(1000000));
  }

  @Test
  public void parseNumber_withUnderscores_shouldIgnoreUnderscores() {
    Number result = NumberHelper.parseNumber("1_000_000");
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(1000000));
  }

  @Test
  public void parseNumber_withWhitespace_shouldIgnoreWhitespace() {
    Number result = NumberHelper.parseNumber("  100  ");
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(100));
  }

  @Test
  public void parseNumber_withInvalidString_shouldReturnNull() {
    Number result = NumberHelper.parseNumber("abc");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseNumber_withWholeNumberInScientificNotation_shouldReturnLong() {
    // 5.0e1 = 50.0, which is a whole number - returns Long (not Integer)
    Number result = NumberHelper.parseNumber("5.0e1");
    assertThat(result, notNullValue());
    assertThat(result, instanceOf(Long.class));
    assertThat(result.longValue(), equalTo(50L));
  }

  // ==================== parseNumber(String, Class) tests ====================

  @Test
  public void parseNumberWithType_toInteger_shouldReturnInteger() {
    Integer result = NumberHelper.parseNumber("42", Integer.class);
    assertThat(result, equalTo(42));
  }

  @Test
  public void parseNumberWithType_toLong_shouldReturnLong() {
    Long result = NumberHelper.parseNumber("999999999999", Long.class);
    assertThat(result, equalTo(999999999999L));
  }

  @Test
  public void parseNumberWithType_toFloat_shouldReturnFloat() {
    Float result = NumberHelper.parseNumber("3.14", Float.class);
    assertThat(result.doubleValue(), closeTo(3.14, 0.001));
  }

  @Test
  public void parseNumberWithType_toDouble_shouldReturnDouble() {
    Double result = NumberHelper.parseNumber("3.14159", Double.class);
    assertThat(result, closeTo(3.14159, 0.00001));
  }

  @Test
  public void parseNumberWithType_toShort_shouldReturnShort() {
    Short result = NumberHelper.parseNumber("100", Short.class);
    assertThat(result, equalTo((short) 100));
  }

  @Test
  public void parseNumberWithType_toByte_shouldReturnByte() {
    Byte result = NumberHelper.parseNumber("50", Byte.class);
    assertThat(result, equalTo((byte) 50));
  }

  @Test
  public void parseNumberWithType_toBigDecimal_shouldReturnBigDecimal() {
    BigDecimal result = NumberHelper.parseNumber("123.456", BigDecimal.class);
    assertThat(result, comparesEqualTo(new BigDecimal("123.456")));
  }

  @Test
  public void parseNumberWithType_withNull_shouldReturnNull() {
    Integer result = NumberHelper.parseNumber(null, Integer.class);
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseNumberWithType_withEmptyString_shouldReturnNull() {
    Integer result = NumberHelper.parseNumber("", Integer.class);
    assertThat(result, is(nullValue()));
  }

  // ==================== autoBoxingNumber tests ====================

  @Test
  public void autoBoxingNumber_withIntegerValue_shouldReturnIntegerValue() {
    Number result = NumberHelper.autoBoxingNumber(new BigDecimal("42"));
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(42));
  }

  @Test
  public void autoBoxingNumber_withLongValue_shouldReturnLongValue() {
    Number result = NumberHelper.autoBoxingNumber(new BigDecimal("9999999999"));
    assertThat(result, notNullValue());
    assertThat(result.longValue(), equalTo(9999999999L));
  }

  @Test
  public void autoBoxingNumber_withDecimalValue_shouldReturnDoubleValue() {
    Number result = NumberHelper.autoBoxingNumber(new BigDecimal("3.14"));
    assertThat(result, notNullValue());
    assertThat(result.doubleValue(), closeTo(3.14, 0.001));
  }

  @Test
  public void autoBoxingNumber_withVeryLargeNumber_shouldReturnBigInteger() {
    // Number larger than Long.MAX_VALUE
    BigDecimal veryLarge = new BigDecimal("99999999999999999999999999999");
    Number result = NumberHelper.autoBoxingNumber(veryLarge);
    assertThat(result, instanceOf(java.math.BigInteger.class));
  }

  @Test
  public void autoBoxingNumber_withTrailingZeros_shouldReturnIntegerValue() {
    // 42.000 should be treated as integer value
    Number result = NumberHelper.autoBoxingNumber(new BigDecimal("42.000"));
    assertThat(result, notNullValue());
    assertThat(result.intValue(), equalTo(42));
  }

  // ==================== isNumeric tests ====================

  @Test
  public void isNumeric_withNull_shouldReturnFalse() {
    boolean result = NumberHelper.isNumeric(null);
    assertThat(result, is(false));
  }

  @Test
  public void isNumeric_withEmptyString_shouldReturnFalse() {
    boolean result = NumberHelper.isNumeric("");
    assertThat(result, is(false));
  }

  @Test
  public void isNumeric_withWhitespaceOnly_shouldReturnFalse() {
    boolean result = NumberHelper.isNumeric("   ");
    assertThat(result, is(false));
  }

  @Test
  public void isNumeric_withValidNumber_shouldReturnTrue() {
    boolean result = NumberHelper.isNumeric("12345");
    assertThat(result, is(true));
  }

  @Test
  public void isNumeric_withLeadingZero_shouldReturnTrue() {
    boolean result = NumberHelper.isNumeric("007");
    assertThat(result, is(true));
  }

  @Test
  public void isNumeric_withLetters_shouldReturnFalse() {
    boolean result = NumberHelper.isNumeric("123abc");
    assertThat(result, is(false));
  }

  @Test
  public void isNumeric_withDecimalPoint_shouldReturnFalse() {
    // isNumeric only checks for digits 0-9, not decimal numbers
    boolean result = NumberHelper.isNumeric("3.14");
    assertThat(result, is(false));
  }

  @Test
  public void isNumeric_withNegativeSign_shouldReturnFalse() {
    // isNumeric only checks for digits 0-9, not negative numbers
    boolean result = NumberHelper.isNumeric("-42");
    assertThat(result, is(false));
  }

  // ==================== splitWise tests ====================

  @Test
  public void splitWise_withInteger_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise(100, 4);
    assertThat(result, hasSize(4));
    int sum = result.stream().mapToInt(Number::intValue).sum();
    assertThat(sum, equalTo(100));
  }

  @Test
  public void splitWise_withLong_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise(1000L, 3);
    assertThat(result, hasSize(3));
    long sum = result.stream().mapToLong(Number::longValue).sum();
    assertThat(sum, equalTo(1000L));
  }

  @Test
  public void splitWise_withDouble_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise(100.0, 4);
    assertThat(result, hasSize(4));
    double sum = result.stream().mapToDouble(Number::doubleValue).sum();
    assertThat(sum, closeTo(100.0, 0.001));
  }

  @Test
  public void splitWise_withFloat_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise(100.0f, 2);
    assertThat(result, hasSize(2));
  }

  @Test
  public void splitWise_withShort_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise((short) 100, 2);
    assertThat(result, hasSize(2));
  }

  @Test
  public void splitWise_withByte_shouldSplitCorrectly() {
    List<? extends Number> result = NumberHelper.splitWise((byte) 100, 2);
    assertThat(result, hasSize(2));
  }

  @Test
  public void splitWise_withZeroTotal_shouldReturnEmptyList() {
    List<? extends Number> result = NumberHelper.splitWise(0, 4);
    assertThat(result, is(empty()));
  }

  @Test
  public void splitWise_withZeroDivider_shouldReturnEmptyList() {
    List<? extends Number> result = NumberHelper.splitWise(100, 0);
    assertThat(result, is(empty()));
  }

  @Test
  public void splitWise_withNegativeTotal_shouldReturnEmptyList() {
    List<? extends Number> result = NumberHelper.splitWise(-100, 4);
    assertThat(result, is(empty()));
  }

  @Test
  public void splitWise_withOneDivider_shouldReturnSingleElement() {
    List<? extends Number> result = NumberHelper.splitWise(100, 1);
    assertThat(result, hasSize(1));
    assertThat(result.get(0).intValue(), equalTo(100));
  }
}
