package io.github.ygrip.testara.engine.option;

import io.github.ygrip.testara.engine.model.TestaraDefaultNamingStrategy;
import io.github.ygrip.testara.engine.model.TestaraNamingStrategy;
import io.github.ygrip.testara.engine.model.RerunStrategy;
import io.cucumber.core.backend.ObjectFactory;
import io.cucumber.core.eventbus.UuidGenerator;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.feature.GluePath;
import io.cucumber.core.options.ObjectFactoryParser;
import io.cucumber.core.options.PluginOption;
import io.cucumber.core.options.SnippetTypeParser;
import io.cucumber.core.options.UuidGeneratorParser;
import io.cucumber.core.plugin.NoPublishFormatter;
import io.cucumber.core.plugin.PublishFormatter;
import io.cucumber.core.snippets.SnippetType;
import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.Node;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.cucumber.core.options.Constants.ANSI_COLORS_DISABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_NAME_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PUBLISH_TOKEN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.SNIPPET_TYPE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.UUID_GENERATOR_PROPERTY_NAME;
import static io.cucumber.core.resource.ClasspathSupport.CLASSPATH_SCHEME_PREFIX;
import static java.util.Comparator.comparing;

@Log4j2
public class TestaraCucumberEngineOptions
    implements io.cucumber.core.plugin.Options, io.cucumber.core.runner.Options, io.cucumber.core.backend.Options,
    io.cucumber.core.eventbus.Options {
  private final ConfigurationParameters configurationParameters;
  private final int MAX_RETRY_ATTEMPT = 10;

  public TestaraCucumberEngineOptions(ConfigurationParameters configurationParameters) {
    this.configurationParameters = configurationParameters;
  }

  public Node.ExecutionMode getExecutionMode(){
    return this.configurationParameters
        .get("cucumber.execution.execution-mode.feature",
            (value) -> Node.ExecutionMode.valueOf(value.toUpperCase(Locale.US)))
        .orElse(Node.ExecutionMode.CONCURRENT);
  }

  public ConfigurationParameters getConfigurationParameters() {
    return this.configurationParameters;
  }

  @Override
  public List<Plugin> plugins() {
    List<Plugin> plugins = configurationParameters.get(PLUGIN_PROPERTY_NAME,
        s -> Arrays.stream(s.split(","))
            .map(String::trim)
            .map(PluginOption::parse)
            .map(pluginOption -> (Plugin) pluginOption)
            .collect(Collectors.toList())).orElseGet(ArrayList::new);

    getPublishPlugin().ifPresent(plugins::add);

    return plugins;
  }

  private Optional<PluginOption> getPublishPlugin() {
    if (isPublishPluginEnabled()) {
      return createPublishPlugin();
    }
    return createCucumberReportsAdvertisingPlugin();
  }

  private Optional<PluginOption> createCucumberReportsAdvertisingPlugin() {
    Optional<PluginOption> noPublishOption = Optional.of(PluginOption.forClass(NoPublishFormatter.class));
    Optional<PluginOption> quiteOption = Optional.empty();
    return configurationParameters.getBoolean(PLUGIN_PUBLISH_QUIET_PROPERTY_NAME)
        .map(quite -> quite ? quiteOption : noPublishOption)
        // Disable the banner advertising the hosted cucumber reports
        // by default until the uncertainty around the projects future
        // is resolved. It would not be proper to advertise a service
        // that may be discontinued to new users.
        // For context see: https://mattwynne.net/new-beginning
        .orElse(quiteOption);
  }

  private Optional<PluginOption> createPublishPlugin() {
    PluginOption publishPlugin = configurationParameters.get(PLUGIN_PUBLISH_TOKEN_PROPERTY_NAME)
        .map(token -> PluginOption.forClass(PublishFormatter.class, token))
        .orElse(PluginOption.forClass(PublishFormatter.class));
    return Optional.of(publishPlugin);
  }

  private boolean isPublishPluginEnabled() {
    return configurationParameters.getBoolean(PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME)
        // Implicitly enabled by the token if not explicitly disabled
        .orElse(configurationParameters.get(PLUGIN_PUBLISH_TOKEN_PROPERTY_NAME).isPresent());
  }

  public boolean isValidTags(List<String> tags) {
    boolean isValid = false;
    Optional<Boolean> validTags = tagFilter().map((expression -> expression.evaluate(tags)));
    if (validTags.isPresent()) {
      isValid = validTags.get();
    }
    return isValid;
  }

  public boolean isValidTags(String tagFilter, List<String> tags) {
    return TagExpressionParser.parse(tagFilter).evaluate(tags);
  }

  @Override
  public boolean isMonochrome() {
    return configurationParameters.getBoolean(ANSI_COLORS_DISABLED_PROPERTY_NAME).orElse(false);
  }

  @Override
  public boolean isWip() {
    return false;
  }

  public Optional<Expression> tagFilter() {
    return configurationParameters.get(FILTER_TAGS_PROPERTY_NAME, TagExpressionParser::parse);
  }

  public Optional<Pattern> nameFilter() {
    return configurationParameters.get(FILTER_NAME_PROPERTY_NAME, Pattern::compile);
  }

  @Override
  public List<URI> getGlue() {
    return configurationParameters.get(GLUE_PROPERTY_NAME, s -> Arrays.asList(s.split(",")))
        .orElse(Collections.singletonList(CLASSPATH_SCHEME_PREFIX))
        .stream()
        .map(String::trim)
        .map(GluePath::parse)
        .collect(Collectors.toList());
  }

  @Override
  public boolean isDryRun() {
    return configurationParameters.getBoolean(EXECUTION_DRY_RUN_PROPERTY_NAME).orElse(false);
  }

  @Override
  public SnippetType getSnippetType() {
    return configurationParameters.get(SNIPPET_TYPE_PROPERTY_NAME, SnippetTypeParser::parseSnippetType)
        .orElse(SnippetType.UNDERSCORE);
  }

  @Override
  public Class<? extends ObjectFactory> getObjectFactoryClass() {
    return configurationParameters.get(OBJECT_FACTORY_PROPERTY_NAME, ObjectFactoryParser::parseObjectFactory)
        .orElse(null);
  }

  @Override
  public Class<? extends UuidGenerator> getUuidGeneratorClass() {
    return configurationParameters.get(UUID_GENERATOR_PROPERTY_NAME, UuidGeneratorParser::parseUuidGenerator)
        .orElse(null);
  }

  public boolean isParallelExecutionEnabled() {
    return configurationParameters.getBoolean("cucumber.execution.parallel.enabled").orElse(false);
  }

  public boolean isGenerateTestaraCustomReportEnabled() {
    return configurationParameters.getBoolean("custom.report.enabled").orElse(true);
  }

  public String reportPath() {
    String reportPath = configurationParameters.get("custom.report.target-location").orElse("/target/destination/");
    reportPath = reportPath.trim();
    if(!reportPath.endsWith("/")){
      reportPath += "/";
    }
    return reportPath;
  }

  public String reportName() {
    return configurationParameters.get("custom.report.report-name").orElse("cucumber.json");
  }

  public String rerunFileName() {
    return configurationParameters.get("custom.report.rerun-file-name").orElse("rerun.txt");
  }

  public boolean shouldFilterSkippedScenarios() {
    return configurationParameters.getBoolean("cucumber.filter.skipped.scenarios").orElse(true);
  }

  public boolean stepNotifications() {
    return configurationParameters.getBoolean("cucumber.step.notifications.enabled").orElse(false);
  }

  public boolean isRerunEnabled() {
    return maxRetryFailedScenarios() > 0 || getRerunStrategy() != RerunStrategy.NONE;
  }

  public int maxRetryFailedScenarios() {
    int maxAttempt = 0;
    try {
      maxAttempt = Integer.parseInt(configurationParameters.get("cucumber.max.retry.failed.scenarios").orElse("0"));
    } catch (Exception ignored) {

    }
    return Math.max(0, Math.min(maxAttempt, MAX_RETRY_ATTEMPT));
  }

  public RerunStrategy getRerunStrategy() {
    String strategy = configurationParameters.get("cucumber.rerun.strategy").orElse("NONE");
    try {
      return RerunStrategy.valueOf(strategy.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid rerun strategy '{}', defaulting to NONE", strategy);
      return RerunStrategy.NONE;
    }
  }

  /**
   * Get the timeout in milliseconds for waiting on file operations
   * @return timeout in milliseconds, default is 15 seconds (15000ms)
   */
  public int fileAwaitTimeoutMillis() {
    int timeout = 15000; // Default 15 seconds
    try {
      timeout = Integer.parseInt(configurationParameters.get("cucumber.file.await.timeout").orElse("15000"));
    } catch (NumberFormatException e) {
      log.warn("Invalid file await timeout value, using default 15000ms");
    }
    return Math.max(1000, timeout); // Minimum 1 second
  }


  public TestaraNamingStrategy namingStrategy() {
    return configurationParameters.get("cucumber.junit-platform.naming-strategy",
        TestaraDefaultNamingStrategy::getStrategy).orElse(TestaraDefaultNamingStrategy.SHORT);
  }

  // Dynamic parallelism configuration
  public String getParallelStrategy() {
    return configurationParameters.get("cucumber.execution.parallel.config.strategy")
        .orElse("fixed");
  }

  /**
   * Get the fixed parallelism value from configuration.
   * Returns the value of cucumber.execution.parallel.config.fixed.parallelism
   *
   * @return configured fixed parallelism, or available processors as default
   */
  public int getFixedParallelism() {
    int parallelism = Runtime.getRuntime().availableProcessors();
    try {
      parallelism = Integer.parseInt(
          configurationParameters.get("cucumber.execution.parallel.config.fixed.parallelism")
              .orElse(String.valueOf(parallelism)));
    } catch (NumberFormatException e) {
      log.warn("Invalid fixed.parallelism value, using CPU cores: {}", parallelism);
    }
    return Math.max(1, parallelism);
  }

  // Virtual Thread (Project Loom) configuration
  public boolean isVirtualThreadEnabled() {
    return configurationParameters.getBoolean("cucumber.execution.parallel.virtual-thread.enabled")
        .orElse(false);
  }

  public int getVirtualThreadMinThreads() {
    int minThreads = 4; // Default minimum
    try {
      minThreads = Integer.parseInt(
          configurationParameters.get("cucumber.execution.parallel.virtual-thread.min-threads")
              .orElse("4"));
    } catch (NumberFormatException e) {
      log.warn("Invalid virtual thread min-threads value, using default 4");
    }
    return Math.max(1, minThreads);
  }

  public int getVirtualThreadMaxThreads() {
    int maxThreads = 1000; // Default maximum
    try {
      maxThreads = Integer.parseInt(
          configurationParameters.get("cucumber.execution.parallel.virtual-thread.max-threads")
              .orElse("1000"));
    } catch (NumberFormatException e) {
      log.warn("Invalid virtual thread max-threads value, using default 1000");
    }
    return Math.max(getVirtualThreadMinThreads(), maxThreads);
  }

  /**
   * Get the effective parallelism for virtual threads based on the configured strategy.
   * <p>
   * - For "fixed" strategy: uses cucumber.execution.parallel.config.fixed.parallelism
   * - For "dynamic" strategy: uses cucumber.execution.parallel.virtual-thread.max-threads
   * </p>
   *
   * @return effective parallelism for virtual thread execution
   */
  public int getEffectiveVirtualThreadParallelism() {
    String strategy = getParallelStrategy();

    if ("fixed".equalsIgnoreCase(strategy)) {
      // For fixed strategy, respect fixed.parallelism setting
      int fixedParallelism = getFixedParallelism();
      log.debug("Using fixed parallelism for virtual threads: {}", fixedParallelism);
      return fixedParallelism;
    } else {
      // For dynamic strategy, use max-threads as the upper bound
      int maxThreads = getVirtualThreadMaxThreads();
      log.debug("Using dynamic max-threads for virtual threads: {}", maxThreads);
      return maxThreads;
    }
  }

  public String getVirtualThreadNamePrefix() {
    return configurationParameters.get("cucumber.execution.parallel.virtual-thread.name-prefix")
        .orElse("testara-vthread");
  }

  public Set<FeatureWithLines> featuresWithLines() {
    String featurePath = this.configurationParameters.get(FEATURES_PROPERTY_NAME, (features) -> features).orElse("");
    if (featurePath.trim().isEmpty()) {
      return Collections.emptySet();
    } else if (featurePath.trim().startsWith("@")) {
      //read feature files from file
      String path = featurePath.trim().replaceFirst("@", "");
      List<String> content = null;
      try {
        content = FileUtils.readLines(Paths.get(path).toFile(), StandardCharsets.UTF_8);
      } catch (IOException ignored) {

      }

      if (content != null && !content.isEmpty()) {
        return content.stream()
            .map(String::trim)
            .map(FeatureWithLines::parse)
            .sorted(comparing(FeatureWithLines::uri))
            .collect(Collectors.toCollection(LinkedHashSet::new));
      } else {
        return Collections.emptySet();
      }
    } else {
      return Arrays.stream(featurePath.split(","))
          .map(String::trim)
          .map(FeatureWithLines::parse)
          .sorted(comparing(FeatureWithLines::uri))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }
}
