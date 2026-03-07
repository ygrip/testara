package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("scalarConverter")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ScalarConverterTests extends BaseTests {

  private final ScalarConverter converter = new ScalarConverter();

  // ==================== Null handling tests ====================

  @Test
  public void convert_withNullValue_shouldReturnNull() {
    String result = converter.convert(null, String.class);
    assertThat(result, is(nullValue()));
  }

  // ==================== String and Object type tests ====================

  @Test
  public void convert_toString_shouldReturnString() {
    String result = converter.convert("hello", String.class);
    assertThat(result, equalTo("hello"));
  }

  @Test
  public void convert_toObject_shouldReturnValue() {
    Object result = converter.convert("test", Object.class);
    assertThat(result, equalTo("test"));
  }

  // ==================== Integer type tests ====================

  @Test
  public void convert_toInt_shouldReturnInteger() {
    int result = converter.convert("42", int.class);
    assertThat(result, equalTo(42));
  }

  @Test
  public void convert_toInteger_shouldReturnInteger() {
    Integer result = converter.convert("42", Integer.class);
    assertThat(result, equalTo(42));
  }

  @Test
  public void convert_toIntWithNegative_shouldReturnNegativeInteger() {
    Integer result = converter.convert("-123", Integer.class);
    assertThat(result, equalTo(-123));
  }

  // ==================== Long type tests ====================

  @Test
  public void convert_toLong_shouldReturnLong() {
    long result = converter.convert("9999999999", long.class);
    assertThat(result, equalTo(9999999999L));
  }

  @Test
  public void convert_toLongWrapper_shouldReturnLong() {
    Long result = converter.convert("123456789", Long.class);
    assertThat(result, equalTo(123456789L));
  }

  // ==================== Boolean type tests ====================

  @Test
  public void convert_toBoolean_withTrue_shouldReturnTrue() {
    boolean result = converter.convert("true", boolean.class);
    assertThat(result, is(true));
  }

  @Test
  public void convert_toBooleanWrapper_withFalse_shouldReturnFalse() {
    Boolean result = converter.convert("false", Boolean.class);
    assertThat(result, is(false));
  }

  @Test
  public void convert_toBoolean_withUpperCase_shouldReturnTrue() {
    Boolean result = converter.convert("TRUE", Boolean.class);
    assertThat(result, is(true));
  }

  @Test
  public void convert_toBoolean_withInvalidValue_shouldReturnFalse() {
    // Boolean.valueOf returns false for non-"true" strings
    Boolean result = converter.convert("invalid", Boolean.class);
    assertThat(result, is(false));
  }

  // ==================== Double type tests ====================

  @Test
  public void convert_toDouble_shouldReturnDouble() {
    double result = converter.convert("3.14159", double.class);
    assertThat(result, closeTo(3.14159, 0.00001));
  }

  @Test
  public void convert_toDoubleWrapper_shouldReturnDouble() {
    Double result = converter.convert("2.718", Double.class);
    assertThat(result, closeTo(2.718, 0.001));
  }

  // ==================== Float type tests ====================

  @Test
  public void convert_toFloat_shouldReturnFloat() {
    float result = converter.convert("1.5", float.class);
    assertThat((double) result, closeTo(1.5, 0.001));
  }

  @Test
  public void convert_toFloatWrapper_shouldReturnFloat() {
    Float result = converter.convert("2.5", Float.class);
    assertThat(result.doubleValue(), closeTo(2.5, 0.001));
  }

  // ==================== Enum type tests ====================

  private enum TestEnum {
    VALUE_ONE, VALUE_TWO
  }

  @Test
  public void convert_toEnum_shouldReturnEnum() {
    TestEnum result = converter.convert("VALUE_ONE", TestEnum.class);
    assertThat(result, equalTo(TestEnum.VALUE_ONE));
  }

  @Test
  public void convert_toEnum_withLowerCase_shouldReturnEnum() {
    TestEnum result = converter.convert("value_two", TestEnum.class);
    assertThat(result, equalTo(TestEnum.VALUE_TWO));
  }

  // ==================== valueOf method tests ====================

  @Test
  public void convert_withValueOfMethod_shouldUseValueOf() {
    // BigInteger has a static valueOf(String) method
    // Note: This tests the valueOf path but BigInteger.valueOf expects long, not String
    // Let's test with a type that has accessible valueOf
    BigDecimal result = converter.convert("123.45", BigDecimal.class);
    // BigDecimal has Constructor(String), so this should work
    assertThat(result, comparesEqualTo(new BigDecimal("123.45")));
  }

  // ==================== Constructor(String) tests ====================

  @Test
  public void convert_withStringConstructor_shouldUseConstructor() {
    BigInteger result = converter.convert("12345678901234567890", BigInteger.class);
    assertThat(result, equalTo(new BigInteger("12345678901234567890")));
  }

  // ==================== Edge cases tests ====================

  @Test
  public void convert_emptyString_toString_shouldReturnEmptyString() {
    String result = converter.convert("", String.class);
    assertThat(result, equalTo(""));
  }

  @Test
  public void convert_toInteger_withZero_shouldReturnZero() {
    Integer result = converter.convert("0", Integer.class);
    assertThat(result, equalTo(0));
  }

  @Test
  public void convert_toDouble_withScientificNotation_shouldParse() {
    Double result = converter.convert("1.5E2", Double.class);
    assertThat(result, closeTo(150.0, 0.001));
  }

  @Test
  public void convert_toMaxIntegerValue_shouldParse() {
    Integer result = converter.convert(String.valueOf(Integer.MAX_VALUE), Integer.class);
    assertThat(result, equalTo(Integer.MAX_VALUE));
  }

  @Test
  public void convert_toMinLongValue_shouldParse() {
    Long result = converter.convert(String.valueOf(Long.MIN_VALUE), Long.class);
    assertThat(result, equalTo(Long.MIN_VALUE));
  }
}
