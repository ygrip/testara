# Testara Validation

Rich assertion and validation framework with 40+ built-in validators, soft assertion support, and an extensible architecture for custom validation logic.

## How It Works

Validations are defined as `DataValidation` objects with three fields:

```java
DataValidation validation = new DataValidation();
validation.setActual(actualValue);
validation.setValidation("equal");         // validator tag name
validation.setExpectation(expectedValue);
```

The `ValidatorHelper` resolves the validator by tag name, instantiates it, and executes the validation. Multiple validations can run in parallel with soft assertions.

### Execution Modes

```java
// Single validation — throws AssertionError on failure
ValidatorHelper.validate(dataValidation);

// Multiple validations — soft assertions, reports all failures
ValidatorHelper.validates(List.of(validation1, validation2, validation3));

// Boolean check — no exception
boolean result = ValidatorHelper.isValid(dataValidation);

// From JSON file
ValidatorHelper.validate("path/to/validations.json");
```

### Validation JSON File Format

```json
[
  {
    "actual": "hello world",
    "validation": "contains_text",
    "expectation": "hello"
  },
  {
    "actual": [1, 2, 3],
    "validation": "has_size",
    "expectation": 3
  }
]
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `validator.helper.scan-locations` | `io.github.ygrip.testara` | Packages to scan for validators |
| `validator.helper.scan-timeout` | `10` | Class scan timeout (seconds) |
| `validator.helper.timeout-seconds` | `30` | Timeout per validation task |
| `validator.helper.validations-path` | *(empty)* | Base path for validation JSON files |

## Available Validations

### Equality

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `EQUAL` | — | Object | Object | Actual equals expected (deep comparison) |
| `NOT_EQUAL` | `not equal` | Object | Object | Actual does not equal expected |

### Empty / Not Empty

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `EMPTY` | `empty` | Object | Boolean | Actual is empty/blank when expected is `true` |
| `NOT_EMPTY` | `not empty` | Object | Boolean | Actual is not empty when expected is `true` |
| `ALL_EMPTY` | `all empty` | Collection | Boolean | All elements are empty |
| `ALL_NOT_EMPTY` | `all not empty` | Collection | Boolean | All elements are non-empty |

### Contains (Collections)

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `CONTAINS` | — | Collection | Object/Collection | Actual contains all expected elements |
| `NOT_CONTAINS` | `not contains` | Collection | Object | Actual does not contain expected |
| `ALL_CONTAINS` | `all contains` | Collection | Object | Every element in actual contains expected |
| `NOT_ALL_CONTAINS` | `not all contains` | Collection | Object | Not all elements contain expected |
| `AT_LEAST_HAVE` | `at least have` | Collection | Object/Collection | At least one element matches |

### Contains (Text)

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `CONTAINS_TEXT` | `contains text`, `has text` | String | String | Actual string contains expected substring |
| `NOT_CONTAINS_TEXT` | `not contains text` | String | String | Actual does not contain expected |
| `ALL_CONTAINS_TEXT` | `all contains text`, `all has text` | List\<String\> | String | Every string contains expected |
| `ALL_NOT_CONTAINS_TEXT` | `all not contains text`, `all has no text` | List\<String\> | String | No string contains expected |

### String Prefix / Suffix

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `STARTS_WITH` | `starts with` | String | String | Actual starts with expected |
| `ENDS_WITH` | `ends with` | String | String | Actual ends with expected |

### Pattern / Regex

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `MATCH_PATTERN` | `match pattern`, `match regex` | Object | String (regex) | All items match the regex |
| `NOT_MATCH_PATTERN` | `not match pattern`, `not match regex` | Object | String (regex) | Items do not match the regex |

### Numeric

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `IS_NUMERIC` | `is numeric` | List\<String\> | Boolean | All items are numeric |
| `IS_NOT_NUMERIC` | `is not numeric` | List\<String\> | Boolean | All items are non-numeric |

### Case

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `IS_UPPERCASE` | `is uppercase` | String | Boolean | String is uppercase |
| `IS_LOWERCASE` | `is lowercase` | String | Boolean | String is lowercase |
| `IS_CAPITALIZED` | `is capitalized` | String | Boolean | String is capitalized |

### Type / Structure

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `IS_ARRAY` | `is array` | Object | Boolean | Actual is a collection/array |
| `IS_UNIQUE` | `is unique` | Collection | Boolean | No duplicate elements |

### Size

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `HAS_SIZE` | `has size` | Collection | Integer | Collection size equals expected |

### Comparison

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `GREATER_THAN` | `greater than`, `>` | Object | Object | All items greater than expected |
| `GREATER_THAN_EQUAL` | `greater than equal`, `>=` | Object | Object | All items greater than or equal |
| `LESSER_THAN` | `lesser than`, `<` | Object | Object | All items less than expected |
| `LESSER_THAN_EQUAL` | `lesser than equal`, `<=` | Object | Object | All items less than or equal |

### Range

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `IN_RANGE_OF` | `in range of` | List | List `[min, max]` | Values fall within range |
| `OUT_RANGE_OF` | `out range of` | List | List `[min, max]` | Values fall outside range |

### Sorting

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `SORTED` | `sorted` | Collection | String (`ascending`/`descending`) | Collection is sorted in given order |
| `NOT_SORTED` | `not sorted` | Collection | String | Collection is not sorted |

### All Elements

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `ALL_EQUAL` | `all equal`, `all elements should equal` | Collection | Object | All elements equal expected |
| `ALL_NOT_EQUAL` | `all not equal`, `all elements not equal` | Collection | Object | No element equals expected |

### Map

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `CONTAINS_KEY` | `contains key` | Map | String | Map contains the key |
| `CONTAINS_VALUE` | `contains value` | Map | Object | Map contains the value |

### JSON Schema

| Tag | Aliases | Actual | Expected | Description |
|-----|---------|--------|----------|-------------|
| `MATCH_SCHEMA` | `match schema` | Object | String (schema file) | Actual matches JSON schema |
| `ALL_MATCH_SCHEMA` | `all match schema` | List | String (schema file) | All elements match JSON schema |

## Creating Custom Validations

1. Create a class extending `ValidatorLogic<ACTUAL, EXPECTED>`:

```java
package com.myproject.validations;

import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.validation.model.ValidationTag;

@ValidationTag(command = "PALINDROME", alias = {"is palindrome"})
public class PalindromeValidation extends ValidatorLogic<String, Boolean> {

  @Override
  protected String setDefaultMessage() {
    return "Expected value to be a palindrome";
  }

  @Override
  public boolean validate() throws Exception {
    String value = getActual();
    if (value == null) return !getExpected();
    String reversed = new StringBuilder(value).reverse().toString();
    return getExpected() == value.equalsIgnoreCase(reversed);
  }
}
```

2. Ensure your class is in a package covered by `validator.helper.scan-locations`:

```properties
validator.helper.scan-locations=io.github.ygrip.testara,com.myproject.validations
```

3. Use it:

```java
DataValidation validation = new DataValidation();
validation.setActual("racecar");
validation.setValidation("palindrome");
validation.setExpectation(true);

ValidatorHelper.validate(validation);
```

### `@ValidationTag` Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `command` | String | *(required)* | Primary validator name (lowercased for lookup) |
| `alias` | String[] | `{}` | Alternative names |
| `overwrite` | boolean | `false` | Overwrite existing validator with the same name |

### `ValidatorLogic<ACTUAL, EXPECTED>` Methods

| Method | Description |
|--------|-------------|
| `validate()` | Core logic — return `true` for pass, `false` for fail |
| `setDefaultMessage()` | Default failure message |
| `getActual()` | The actual value under test |
| `getExpected()` | The expected value |
| `addMessage(String)` | Append additional failure details |
| `result()` | Executes validation and returns `ValidatorResult` |

### `ValidatorResult`

| Field | Type | Description |
|-------|------|-------------|
| `validation` | String | Validator tag name |
| `success` | boolean | Pass/fail |
| `error` | Throwable | Failure details (null on success) |
