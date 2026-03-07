# Testara Command

Extensible command parser and expression engine for dynamic data generation, transformation, and computation within test scenarios.

## Command Syntax

Commands follow the format:

```
commandName(param1, param2, ...)
```

- **Nested commands**: `combine(uuid(), delimiter(-), fakename())` — inner commands are evaluated first
- **Literal escape**: `!(content)` — treats content as a literal string, skipping command parsing
- **Separator**: comma (`,`) by default, configurable via `command.executor.default-command-separator`

### Examples

```
uuid()                              → "a1b2c3d4-..."
combine(hello, delimiter( ), world) → "hello world"
timetravel(2, day, yyyy-MM-dd)      → "2026-03-09"
number(1, 100)                      → 42
fakeemail(example.com)              → "john.doe@example.com"
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `command.executor.scan-locations` | `io.github.ygrip.testara` | Packages to scan for commands |
| `command.executor.default-command-separator` | `,` | Parameter separator |
| `command.executor.cache-enabled` | `true` | Enable result caching |
| `command.executor.max-execution-cache-size` | `500` | Max execution cache entries |
| `command.executor.max-parse-cache-size` | `1000` | Max parse cache entries |
| `command.executor.max-printable-characters` | `100000` | Max characters for command parsing |
| `command.executor.scan-timeout` | `10` | Class scan timeout in seconds |

## Available Commands

### Generators

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `uuid` | — | Generates a random UUID | — | String |
| `random` | — | Random alphanumeric string | `[length]`, `[mode]` | String |
| `random_decimal` | `random decimal`, `random float`, `random_float` | Random decimal number | `[min]`, `[max]`, `[precision]` | Double |
| `number` | — | Random integer in range | `[min]`, `[max]` | Integer |
| `increment` | — | Thread-local auto-increment counter | — | Integer |
| `combine` | — | Joins parameters with optional `delimiter(d)` | values... | String |
| `loop` | — | Repeats a string | `input`, `[separator]`, `[count]` | String |
| `fakeemail` | — | Fake email address | `[domain]` | String |
| `fakephone` | — | Fake phone number | `[prefix]` | String |
| `fakename` | — | Fake full name | `[locale]` | String |
| `fakefirstname` | — | Fake first name | `[locale]` | String |
| `fakelastname` | — | Fake last name | `[locale]` | String |
| `fakeaddress` | — | Fake address | `[locale]` | FakeAddressModel |
| `prettyprint` | — | Pretty-prints a value (JSON formatting) | `value` | String |

### String Operations

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `capitalize` | — | Capitalizes first letter | `string` | String |
| `uppercase` | — | Converts to uppercase | `string` | String |
| `lowercase` | — | Converts to lowercase | `string` | String |
| `trim` | — | Trims whitespace | `string` | String |
| `replace` | `rep` | Find and replace | `value`, `[regex]`, `[replacement]` | String |
| `split` | — | Splits string into list | `string`, `[delimiter]` | List |
| `substring` | `sub string` | Extracts substring | `string`, `[start]`, `[end]` | String |
| `countchar` | `count char` | Character count | `string` | Integer |
| `encode_url` | — | URL-encodes a string | `string` | String |
| `decode_url` | — | URL-decodes a string | `string` | String |
| `base64` | — | Base64 encode/decode | `value`, `[encode\|decode]` | String |

### Type Conversion

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `string` | — | Converts to string | `value` | String |
| `integer` | — | Parses to integer | `value` | Integer |
| `long` | — | Parses to long | `value` | Long |
| `float` | — | Parses to float | `value` | Float |
| `double` | — | Parses to double | `value` | Double |
| `boolean` | — | Parses to boolean | `value` | Boolean |
| `nullvalue` | `null value`, `null` | Returns null | — | null |
| `ignored` | `!`, `ignore` | Returns content as literal | `content` | String |

### Data Commands

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `properties` | `prop` | Reads a configuration property | `key`, `[default]` | Object |
| `request` | — | Reads request data from DataHolder | `path` | Object |
| `response` | — | Reads response data from DataHolder | `path` | Object |
| `jsonpath` | — | JSONPath query on an object | `object`, `path` | Object |
| `readfile` | — | Reads file content as string | `[directory]`, `filename` | String |
| `openfile` | — | Opens a file handle | `[directory]`, `filename` | File |
| `oneof` | — | Picks random element from a list | values... | Object |
| `anyof` | `any_of` | First non-blank value | values... | Object |
| `setof` | — | Unique elements from a collection | `collection` | List |
| `sliceof` | `slice of` | Sublist of a list | `list`, `[start]`, `[end]` | List |
| `keyset` | — | Keys from a map | `map` | List |
| `ascending` | `asc` | Sorts ascending | values... | List |
| `descending` | `desc` | Sorts descending | values... | List |
| `reverse` | `flip` | Reverses a list | values... | List |
| `splitwise` | `split wise`, `split_wise` | Splits number into parts | `number`, `[parts]` | List |

### Math Operations

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `add` | `+` | Addition | `a`, `b` | Number |
| `subtract` | `-` | Subtraction | `a`, `b` | Number |
| `multiply` | `x` | Multiplication | `a`, `b` | Number |
| `divide` | `/` | Division | `a`, `b` | Number |
| `mod` | `%` | Modulo | `a`, `b` | Double |
| `pow` | `^` | Exponentiation | `base`, `[exponent]` | Double |
| `sqrt` | `root` | Square root | `number` | Double |
| `round` | — | Rounds to nearest integer | `number` | Double |
| `floor` | — | Floor value | `number` | Double |
| `sizeof` | `size` | Size of collection/string | `value` | Integer |
| `sumof` | — | Sum of numbers | values... | Number |

### Set Operations

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `union` | — | Union of collections | collections... | List |
| `intersection` | — | Intersection of collections | collections... | List |
| `difference` | — | Difference of collections | collections... | List |

### Date/Time Commands

| Command | Aliases | Description | Parameters | Returns |
|---------|---------|-------------|------------|---------|
| `now` | — | Current date/time | `[format]` | String |
| `date` | — | Formats a date | `[epoch]`, `[format]`, `[targetFormat]` | String |
| `timestamp` | `epoch` | Epoch in milliseconds | `[date]`, `[format]` | Long |
| `timetravel` | `tt` | Add/subtract time from a date | `[date]`, `amount`, `mode`, `[sourceFormat]`, `[targetFormat]` | String |
| `month` | — | Month from date | `[epoch]` | Integer |
| `year` | — | Year from date | `[epoch]` | Integer |
| `day_of_month` | — | Day of month | `[epoch]` | Integer |
| `last_day_of_month` | — | Last day of the month | `[epoch]` | Integer |

#### Time Travel Modes

`second`, `minute`, `hour`, `day`, `week`, `month`, `year`

```
timetravel(7, day, yyyy-MM-dd)           → 7 days from now
timetravel(2026-01-01, -3, month, yyyy-MM-dd, yyyy-MM-dd)  → "2025-10-01"
```

## Creating Custom Commands

1. Create a class implementing `CommandLogic<T>`:

```java
package com.myproject.commands;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import java.util.List;

@CommandTag(command = "greet", alias = {"hello"}, cacheable = true)
public class GreetCommand implements CommandLogic<String> {

  @Override
  public boolean preProcessParameters() {
    return true; // parse nested commands in parameters first
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    if (parameters.isEmpty()) {
      return "Hello, World!";
    }
    return "Hello, " + parameters.get(0) + "!";
  }
}
```

2. Ensure the class is in a package covered by `command.executor.scan-locations` (defaults to `io.github.ygrip.testara`). Add your package if needed:

```properties
command.executor.scan-locations=io.github.ygrip.testara,com.myproject.commands
```

### `@CommandTag` Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `command` | String | *(required)* | Primary command name |
| `alias` | String[] | `{}` | Alternative names |
| `subCommands` | String[] | `{}` | Sub-commands |
| `overwrite` | boolean | `false` | Overwrite existing command with same name |
| `cacheable` | boolean | `false` | Cache results for identical inputs |

### `CommandLogic<T>` Methods

| Method | Description |
|--------|-------------|
| `preProcessParameters()` | Return `true` to resolve nested commands in parameters before execution |
| `execute(List<Object> parameters)` | Main command logic — receives resolved parameters, returns result |
| `info()` | Returns `CommandInfo` metadata (default implementation from `@CommandTag`) |

### SPI Integration

Commands are discovered via classpath scanning. If you need to integrate the command parser as an `ObjectConverter` or `PropertyResolver`, the module registers these via SPI:

- `io.github.ygrip.testara.core.converter.ObjectConverter` → `CommandPatternObjectConverter`
- `io.github.ygrip.testara.core.config.PropertyResolver` → `CommandPatternPropertyResolver`
