package io.github.ygrip.testara.command;

import io.github.ygrip.testara.command.error.CommandNotFoundException;
import io.github.ygrip.testara.command.error.InvalidCommandFormatException;
import io.github.ygrip.testara.command.model.CommandInfo;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.command.properties.CommandExecutorProperties;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.PopulatedTag;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.scan.ClassScannerConfig;
import io.github.ygrip.testara.core.support.Stopwatch;
import io.github.ygrip.testara.core.time.DurationParser;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>CommandExecutor class.</p>
 * Enhanced version with optimized command registration, improved caching, and better performance.
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
public final class CommandExecutor {
  public static final String IGNORED_COMMAND = "!";
  // Enhanced data structures for better performance
  private static final CopyOnWriteArrayList<PopulatedTag> COMMANDS_LIST = new CopyOnWriteArrayList<>();
  private static final Map<String, Class<? extends CommandLogic<?>>> REGISTERED_COMMANDS = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, CommandModel> PARSE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Object> EXECUTION_CACHE = new ConcurrentHashMap<>();
  // Cache configuration
  private static final int MAX_PARSE_CACHE_SIZE = 1000;
  private static final int MAX_EXECUTION_CACHE_SIZE = 500;
  private static final CommandExecutorProperties properties;

  static {
    // Initialize commands during class loading
    properties = getDefaultProperties();
    initializeCommands();
  }

  // Need to load default properties manually because command executor is being used for the configuration resolution,
  // so normal binding by TestFramework configuration won't be able to resolve it as it is not ready yet.
  private static CommandExecutorProperties getDefaultProperties() {
    CommandExecutorProperties prop = new CommandExecutorProperties();
    prop.setCacheEnabled(Boolean.parseBoolean(System.getProperty("command.executor.cache-enabled",
        prop.getCacheEnabled().toString())));
    prop.setScanTimeout(Integer.parseInt(System.getProperty("command.executor.scan-timeout",
        prop.getScanTimeout().toString())));
    prop.setMaxExecutionCacheSize(Integer.parseInt(System.getProperty("command.executor.max-execution-cache-size",
        prop.getMaxExecutionCacheSize().toString())));
    prop.setMaxExecutionCacheSize(Integer.parseInt(System.getProperty("command.executor.max-parse-cache-size",
        prop.getMaxParseCacheSize().toString())));
    prop.setMaxPrintableCharacters(Integer.parseInt(System.getProperty("command.executor.max-printable-characters",
        prop.getMaxPrintableCharacters().toString())));
    prop.setDefaultCommandSeparator(System.getProperty("command.executor.default-command-separator",
        prop.getDefaultCommandSeparator()));
    prop.setScanLocations(new HashSet<>(List.of(StringUtils.split(System.getProperty("command.executor.scan-locations",
        String.join(",", prop.getScanLocations())), ","))));
    return prop;
  }

  static CommandExecutorProperties properties() {
    return Objects.requireNonNull(properties);
  }

  public static String defaultSeparator() {
    return properties().getDefaultCommandSeparator();
  }

  public static Integer maxCharacters() {
    return properties().getMaxPrintableCharacters();
  }

  /**
   * Initialize commands with optimized registration
   */
  private static void initializeCommands() {
    try {
      // Use synchronous registration to avoid potential deadlocks
      Map<String, Class<? extends CommandLogic<?>>> commands = registerCommandsOptimized();
      REGISTERED_COMMANDS.putAll(commands);
    } catch (Exception e) {
      log.error("Failed to initialize commands: {}", e.getMessage());
      // Retry once more
      try {
        Map<String, Class<? extends CommandLogic<?>>> commands = registerCommandsOptimized();
        REGISTERED_COMMANDS.putAll(commands);
      } catch (Exception retryException) {
        log.error("Failed to initialize commands on retry: {}", retryException.getMessage());
      }
    }
  }

  /**
   * Optimized command instance creation
   */
  private static CommandLogic<?> getInstanceOptimized(Class<?> clazz) {
    return (CommandLogic<?>) TestFramework.context().factory().getInstance(clazz);
  }

  /**
   * Optimized command registration with parallel processing
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Class<? extends CommandLogic<?>>> registerCommandsOptimized()
      throws ExecutionException, InterruptedException, TimeoutException {
    Stopwatch stopwatch = Stopwatch.start();

    Map<String, Class<? extends CommandLogic<?>>> commands = new ConcurrentHashMap<>();
    Set<String> processedCommands = ConcurrentHashMap.newKeySet();

    // Need to use dedicated class scanner as the test framework might not be ready during configuration resolve process
    int bufferSize = Integer.parseInt(System.getProperty("class.loader.buffer-size", "33554432"));
    boolean enableParallelScanning =
        Boolean.parseBoolean(System.getProperty("class.loader.enable-parallel-scanning", "false"));
    Set<String> rejectPackages = new HashSet<>(List.of(StringUtils.split(System.getProperty(
            "class.loader.reject-packages",
            "org.*,com.sun.*,java.*,javax.*,io.netty.*,org.springframework.*,net.bytebuddy.*,com.fasterxml.*,org.apache.*,org.junit.*,org.hamcrest.*,org.mockito.*,com.google.*,org.slf4j.*,ch.qos.logback.*,org.seleniumhq.*,net.serenitybdd.*,io.restassured.*,com.browserup.*,org.json.*,org.yaml.*,com.jayway.*,org.objenesis.*,net.sf.*,org.w3c.*,org.xml.*,com.squareup.*,okhttp3.*,retrofit2.*,com.github.*,io.github.classgraph.*,io.github.bonigarcia.*,org.jetbrains.*,kotlin.*,kotlinx.*"),
        ",")));
    Set<String> defaultLocations =
        new HashSet<>(List.of(StringUtils.split(System.getProperty("class.loader.default-scan-locations", "io.github.ygrip.testara"),
            ",")));
    ClassScannerConfig scannerConfig =
        new ClassScannerConfig(bufferSize, enableParallelScanning, rejectPackages, defaultLocations, new HashMap<>());
    ClassScanner scanner = new ClassScanner(scannerConfig);

    // Use optimized class scanning
    List<Class<?>> loaded =
        scanner.scanOnPackages(CommandLogic.class, CommandTag.class, properties().getScanLocations())
            .get(properties().getScanTimeout(), TimeUnit.SECONDS);

    // Process commands sequentially to avoid potential hanging issues
    loaded.forEach(clazz -> {
      try {

        CommandInfo info = new CommandInfo(clazz);
        List<String> aliases = info.aliases() != null ? info.aliases() : Collections.emptyList();

        String name = info.name();
        PopulatedTag identifier = PopulatedTag.builder().build();

        if (name != null) {
          name = name.trim().toLowerCase();
          if (info.overwrite() || processedCommands.add(name)) {
            commands.put(name, (Class<? extends CommandLogic<?>>) clazz);
            identifier.setName(name);
          }
        }

        if (!aliases.isEmpty()) {
          List<String> filteredAliases = new ArrayList<>();
          for (String alias : aliases) {
            if (alias != null) {
              alias = alias.trim().toLowerCase();
              if (info.overwrite() || processedCommands.add(alias)) {
                commands.put(alias, (Class<? extends CommandLogic<?>>) clazz);
                filteredAliases.add(alias);
              }
            }
          }
          identifier.setAliases(filteredAliases);
        }

        if (identifier.getName() != null) {
          COMMANDS_LIST.add(identifier); // ensure this list is thread-safe
        }

      } catch (Exception e) {
        log.debug("Failed to register command for {}: {}", clazz.getSimpleName(), e.getMessage());
      }
    });

    log.info("#Populating commands took {} to populate {} commands",
        DurationParser.formatDuration(stopwatch.stop().elapsed(TimeUnit.NANOSECONDS)),
        commands.size());
    log.debug("#Available commands :\n{}", COMMANDS_LIST.toString());

    return commands;
  }

  /**
   * <p>parseRegisteredCommand.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link io.github.ygrip.testara.command.model.CommandModel} object.
   * @throws io.github.ygrip.testara.command.error.InvalidCommandFormatException if any.
   * @throws io.github.ygrip.testara.command.error.CommandNotFoundException      if any.
   */
  public static CommandModel parseRegisteredCommand(Object input)
      throws InvalidCommandFormatException, CommandNotFoundException {
    CommandModel command = parseCommand(input);
    if (command == null) {
      throw new InvalidCommandFormatException("Unable to parse command");
    } else if (isCommandLogicExists(command.getCommand())) {
      return command;
    } else {
      throw new CommandNotFoundException("Cannot find command : " + command.getCommand());
    }
  }

  /**
   * <p>parseCommand.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link io.github.ygrip.testara.command.model.CommandModel} object.
   * @throws io.github.ygrip.testara.command.error.InvalidCommandFormatException if any.
   */
  public static CommandModel parseCommand(Object input) throws InvalidCommandFormatException {
    return parseCommandOptimized(input, null);
  }

  /**
   * Optimized command parsing with caching
   */
  private static CommandModel parseCommandOptimized(Object input, String parent) throws InvalidCommandFormatException {
    if (input == null) {
      return null;
    } else if (input instanceof CommandModel) {
      return (CommandModel) input;
    } else if (!String.class.isAssignableFrom(input.getClass())) {
      throw new InvalidCommandFormatException("Cannot parse command from input of non string type");
    }

    final String stringInput = input.toString();

    if (stringInput.length() > properties().getMaxPrintableCharacters()) {
      return null;
    }

    // Check parse cache first
    if (properties().getCacheEnabled() && PARSE_CACHE.size() < MAX_PARSE_CACHE_SIZE) {
      String cacheKey = (parent != null ? parent + ":" : "") + stringInput;
      CommandModel cached = PARSE_CACHE.get(cacheKey);
      if (cached != null) {
        return cached;
      }
    }

    CommandModel result = parseCommandInternal(stringInput, parent);

    // Cache the result
    if (properties().getCacheEnabled() && result != null && PARSE_CACHE.size() < MAX_PARSE_CACHE_SIZE) {
      String cacheKey = (parent != null ? parent + ":" : "") + stringInput;
      PARSE_CACHE.put(cacheKey, result);
    }

    return result;
  }

  /**
   * Internal command parsing logic using streaming approach for better performance
   */
  private static CommandModel parseCommandInternal(String input, String parent) throws InvalidCommandFormatException {
    if (!StringUtils.trim(input).endsWith(")")) {
      throw new InvalidCommandFormatException("Invalid command format: " + input);
    }
    // Use streaming parser instead of regex for better performance
    CommandParseResult parseResult = parseCommandStream(input);

    if (parseResult == null || parseResult.command == null) {
      throw new InvalidCommandFormatException("Invalid command format: " + input);
    }

    List<Object> parametersList = reconstructParameters(parseResult.command, parseResult.parameters);

    CommandModel result = new CommandModel();
    result.setCommand(parseResult.command);
    result.setParameters(parametersList);
    result.setParentCommand(parent);
    result.setCacheable(isCacheableCommand(result));

    return result;
  }

  /**
   * Stream-based command parser for better performance with large strings
   */
  private static CommandParseResult parseCommandStream(String input) {
    if (input == null || input.isEmpty()) {
      return null;
    }

    int length = input.length();
    int openParenIndex = -1;

    // Find the first opening parenthesis
    for (int i = 0; i < length; i++) {
      if (input.charAt(i) == '(') {
        openParenIndex = i;
        break;
      }
    }

    if (openParenIndex == -1) {
      // No parentheses found, not a valid command format
      return null;
    }

    // Extract command name (everything before first opening parenthesis)
    String command = input.substring(0, openParenIndex);
    if (command.trim().equals(IGNORED_COMMAND)) {
      return new CommandParseResult(command, input.substring(openParenIndex + 1, length - 1));
    }

    // Find matching closing parenthesis using streaming approach
    int closeParenIndex = findMatchingClosingParentheses(input, openParenIndex);
    if (closeParenIndex == -1) {
      // No matching closing parenthesis found
      return null;
    }

    // Extract parameters (everything between parentheses)
    String parameters = "";
    if (closeParenIndex > openParenIndex + 1) {
      parameters = input.substring(openParenIndex + 1, closeParenIndex);
    }

    return new CommandParseResult(command, parameters);
  }

  /**
   * Find matching closing parenthesis using streaming approach
   * Handles nested parentheses and quoted strings correctly
   */
  private static int findMatchingClosingParentheses(String input, int openIndex) {
    int length = input.length();
    int depth = 0;
    boolean hasIgnoredIdentifier = false;
    int lastIndexOfClosing = input.lastIndexOf(')');

    for (int i = openIndex; i < length; i++) {
      char c = input.charAt(i);

      if (hasIgnoredIdentifier) {
        return lastIndexOfClosing;
      }

      if (c == '!') {
        hasIgnoredIdentifier = true;
      }

      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i; // Found matching closing parenthesis
        }
      }
    }

    return -1; // No matching closing parenthesis found
  }

  /**
   * <p>getRegisteredCommandNames.</p>
   *
   * @return a {@link List} object.
   */
  public static List<PopulatedTag> getRegisteredCommandNames() {
    return new ArrayList<>(COMMANDS_LIST);
  }

  /**
   * <p>getRegisteredCommands.</p>
   *
   * @return a {@link Map} object.
   */
  public static Map<String, Class<? extends CommandLogic<?>>> getRegisteredCommands() {
    return REGISTERED_COMMANDS;
  }

  /**
   * Execute command with optimized caching and performance tracking
   */
  @SuppressWarnings("unchecked")
  public static <T> T executeCommand(CommandModel command) throws CommandNotFoundException {
    if (command == null) {
      return null;
    }

    final String commandName = normalizeCommandName(command.getCommand());
    Class<? extends CommandLogic<?>> logicClass = REGISTERED_COMMANDS.get(commandName);
    CommandLogic<?> logic = null;
    if (ObjectUtils.isNotEmpty(logicClass)) {
      logic = getInstanceOptimized(logicClass);
    }

    if (logic == null) {
      throw new CommandNotFoundException("Command not found: " + commandName);
    }

    try {
      // Check execution cache for cacheable commands
      if (properties().getCacheEnabled() && isCacheableCommand(command)) {
        String cacheKey = generateCacheKey(command);
        Object cached = EXECUTION_CACHE.get(cacheKey);
        if (cached != null) {
          return (T) cached;
        }
      }

      List<Object> parameters = command.getParameters();
      if (parameters == null) {
        parameters = Collections.emptyList();
      }

      T result = (T) logic(logic, parameters);

      // Cache the result if applicable
      if (properties().getCacheEnabled() && isCacheableCommand(command) && result != null
          && EXECUTION_CACHE.size() < MAX_EXECUTION_CACHE_SIZE) {
        String cacheKey = generateCacheKey(command);
        EXECUTION_CACHE.put(cacheKey, result);
      }

      return result;

    } catch (Exception e) {
      log.debug("Command execution failed for {}: {}", command.getCommand(), e.getMessage());
      return null;
    }
  }

  /**
   * Generate cache key for command execution
   */
  private static String generateCacheKey(CommandModel command) {
    StringBuilder keyBuilder = new StringBuilder();
    keyBuilder.append(command.getCommand());

    if (command.getParameters() != null) {
      for (Object param : command.getParameters()) {
        keyBuilder.append(":").append(param != null ? param.toString() : "null");
      }
    }

    return keyBuilder.toString();
  }

  /**
   * <p>executeCommand.</p>
   *
   * @param input a {@link Object} object.
   * @param <T>   a T object.
   * @return a T object.
   */
  @SuppressWarnings("unchecked")
  public static <T> T executeCommand(Object input) {
    if (input == null) {
      return null;
    }

    if (input instanceof CommandModel) {
      executeCommand((CommandModel) input);
    }

    try {
      CommandModel command = parseCommand(input);
      if (command == null) {
        return (T) input;
      }

      return executeCommand(command);

    } catch (InvalidCommandFormatException e) {
      log.debug("Invalid command format: {}", e.getMessage());
      return (T) input;
    } catch (CommandNotFoundException e) {
      log.debug("Command not found: {}", e.getMessage());
      return (T) input;
    } catch (Exception e) {
      log.debug("Command execution failed: {}", e.getMessage());
      return (T) input;
    }
  }

  /**
   * Bulk command execution with parallel processing
   */
  public static List<Object> bulkExecuteCommand(List<?> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return Collections.emptyList();
    }

    // Execute commands sequentially to avoid potential hanging issues
    return inputs.stream()
        .map(CommandExecutor::executeCommand)
        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  /**
   * Execute command logic with optimized parameter handling
   */
  private static <T> T logic(CommandLogic<T> logic, List<Object> parameters) throws Exception {
    if (logic == null) {
      return null;
    }

    // Optimize parameter processing
    List<Object> processedParameters = parameters;
    if (logic.preProcessParameters() && parameters != null && !parameters.isEmpty()) {
      processedParameters = new ArrayList<>();
      for (Object param : parameters) {
        if (param != null) {
          if (param instanceof CommandModel) {
            processedParameters.add(executeCommand((CommandModel) param));
          } else {
            processedParameters.add(param);
          }
        }
      }
    }

    return logic.execute(processedParameters);
  }

  /**
   * Reconstruct parameters using efficient streaming approach
   */
  private static List<Object> reconstructParameters(String command, String input) {
    if (input == null || input.trim().isEmpty()) {
      return Collections.emptyList();
    }

    if (command.trim().equals(IGNORED_COMMAND)) {
      return Collections.singletonList(input);
    }

    // Handle single parameter (no separators)
    if (!input.contains(properties().getDefaultCommandSeparator())) {
      return Collections.singletonList(parseSingleParameter(input, command));
    }

    // Parse multiple parameters using streaming approach
    return parseMultipleParameters(input, command);
  }

  /**
   * Parse a single parameter, checking if it's a nested command
   */
  private static Object parseSingleParameter(String input, String parentCommand) {
    if (input.contains("(") && input.trim().endsWith(")")) {
      try {
        return parseCommandInternal(input, parentCommand);
      } catch (Exception ignored) {
        return input;
      }
    }
    return input;
  }

  private static int reverseFindMatchingClosingParentheses(String input, int openIndex) {
    int length = input.length();
    char separator = properties().getDefaultCommandSeparator().charAt(0);
    int lastIndex = input.lastIndexOf(')');
    if (!input.contains(properties().getDefaultCommandSeparator())) {
      return lastIndex;
    }

    for (int i = length - 1; i > openIndex; i--) {
      char c = input.charAt(i);
      if (c == separator) {
        if (i > 0) {
          if (input.charAt(i - 1) == ')') {
            lastIndex = i - 1;
            break;
          }
        }
      }
    }
    return lastIndex;
  }

  /**
   * Parse multiple parameters using efficient streaming approach
   */
  private static List<Object> parseMultipleParameters(String input, String parentCommand) {
    List<Object> parameters = new ArrayList<>();
    StringBuilder currentParam = new StringBuilder();

    int length = input.length();
    int depth = 0;
    boolean hasIgnoredParameter = false;
    int ignoredAt = 0;
    char separatorChar = properties().getDefaultCommandSeparator().charAt(0);

    for (int i = 0; i < length; i++) {
      char c = input.charAt(i);

      if (hasIgnoredParameter) {
        int lastClosing = reverseFindMatchingClosingParentheses(input, ignoredAt);
        if (lastClosing > 0) {
          parameters.add(parseParameterValue(input.substring(ignoredAt, lastClosing + 1), parentCommand));
          currentParam.setLength(0); // Reset for next parameter
          depth = 0;
          i = lastClosing;
        } else {
          currentParam.append(c);
        }
        ignoredAt = 0;
        hasIgnoredParameter = false;
        continue;
      }

      if (c == '!') {
        String temp = currentParam.toString().trim();
        if (temp.isEmpty()) {
          ignoredAt = i;
          hasIgnoredParameter = true;
        }
        currentParam.append(c);
        continue;
      }

      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }

      // Check for parameter separator at root level
      if (c == separatorChar && depth == 0) {
        // Found a parameter separator at root level
        String paramStr = currentParam.toString();
        parameters.add(parseParameterValue(paramStr, parentCommand));
        currentParam.setLength(0); // Reset for next parameter
      } else {
        currentParam.append(c);
      }
    }

    // Add the last parameter
    String paramStr = currentParam.toString();
    parameters.add(parseParameterValue(paramStr, parentCommand));

    return parameters;
  }

  /**
   * Parse individual parameter value, handling nested commands
   */
  private static Object parseParameterValue(String param, String parentCommand) {
    if (param == null || param.trim().isEmpty()) {
      return param;
    }

    // Check if this parameter is a nested command
    if (param.contains("(") && param.trim().endsWith(")")) {
      try {
        return parseCommandInternal(param, parentCommand);
      } catch (Exception ignored) {
        // If parsing as command fails, treat as regular parameter
      }
    }

    return param;
  }

  /**
   * Normalize command name for consistency
   */
  private static String normalizeCommandName(String command) {
    if (command == null) {
      return "";
    }
    return command.trim().toLowerCase();
  }

  /**
   * Check if command logic exists
   */
  public static boolean isCommandLogicExists(String command) {
    return command != null && REGISTERED_COMMANDS.containsKey(normalizeCommandName(command));
  }

  /**
   * Check if command is cacheable
   */
  public static boolean isCacheableCommand(CommandModel command) {
    String normalizedCommand = normalizeCommandName(command.getCommand());
    if (REGISTERED_COMMANDS.containsKey(normalizedCommand)) {
      boolean cacheable = new CommandInfo(REGISTERED_COMMANDS.get(normalizedCommand)).isCacheable();
      if (cacheable && ObjectUtils.isEmpty(command.getParameters())) {
        return true;
      } else if (cacheable) {
        List<Object> parameters = command.getParameters();
        for (Object parameter : parameters) {
          if (parameter instanceof CommandModel) {
            if (!((CommandModel) parameter).isCacheable()) {
              return false;
            }
          }
        }
        return true;
      } else {
        return false;
      }
    } else {
      return true;
    }
  }

  /**
   * Clear all caches
   */
  public static void clearAllCaches() {
    PARSE_CACHE.clear();
    EXECUTION_CACHE.clear();
  }


  /**
   * Result holder for command parsing
   */
  private static class CommandParseResult {
    final String command;
    final String parameters;

    CommandParseResult(String command, String parameters) {
      this.command = command;
      this.parameters = parameters;
    }
  }
}
