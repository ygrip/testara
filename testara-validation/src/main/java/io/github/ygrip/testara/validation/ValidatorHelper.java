package io.github.ygrip.testara.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.concurrency.ExecutorFactory;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.PopulatedTag;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.Stopwatch;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.validation.model.DataValidation;
import io.github.ygrip.testara.validation.model.ValidatorCatalogEntry;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorInfo;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.validation.model.ValidatorResult;
import io.github.ygrip.testara.validation.properties.ValidatorProperties;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertions;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>ValidatorHelper class.</p>
 * This class is thread-safe for concurrent validation operations :
 * - ValidatorLogic instances are created per validation to prevent race conditions
 * - Type references are cached safely to avoid repeated reflection overhead
 * - Object conversion results are cached with thread-safe concurrent maps
 * - Each thread operates on its own ValidatorLogic instance state
 *
 * @author yunaz.ramadhan on 12/9/2019
 * @version $Id: $Id
 */
@Log4j2
public final class ValidatorHelper {
  private static final ValidatorProperties properties;
  private static final String DEFAULT_REQUEST_FOLDER;
  private static final String DEFAULT_FORMAT = "json";
  private static final ConcurrentMap<String, Class<?>> REGISTERED_VALIDATORS = new ConcurrentHashMap<>();
  private static List<PopulatedTag> VALIDATOR_LIST;

  static {
    properties = TestFramework.context().configuration().get(ValidatorProperties.class);
    DEFAULT_REQUEST_FOLDER = String.format("%s%s",
        System.getProperty("user.dir"),
        StringUtils.isBlank(properties.getValidationsPath()) ?
            "/src/test/resources/" :
            properties.getValidationsPath());
    REGISTERED_VALIDATORS.putAll(registerValidators());
  }

  static ValidatorProperties properties() {
    return Objects.requireNonNull(properties);
  }

  private static ValidatorLogic<?, ?> getInstance(Class<?> clazz) {
    ValidatorLogic<?, ?> instance = null;
    try {
      instance = (ValidatorLogic<?, ?>) clazz.getDeclaredConstructor().newInstance();
    } catch (Exception ignored) {
      //ignored
    }
    return instance;
  }

  private static ConcurrentMap<String, Class<?>> registerValidators() {
    Stopwatch stopwatch = Stopwatch.start();
    VALIDATOR_LIST = new ArrayList<>();
    ConcurrentMap<String, Class<?>> validators = new ConcurrentHashMap<>();

    try {
      // Use optimized scanning for improved performance
      ClassScanner scanner = TestFramework.context().get(ClassScanner.class);
      List<Class<?>> loaded =
          scanner.scanOnPackages(ValidatorLogic.class, ValidationTag.class, properties().getScanLocations())
              .get(properties().getScanTimeout(), TimeUnit.SECONDS);

      for (Class<?> clazz : loaded) {
        try {
          PopulatedTag identifier = PopulatedTag.builder().build();
          ValidatorInfo info = new ValidatorInfo(clazz);
          boolean overwriteExisting = info.overwrite();
          List<String> aliases = info.aliases();

          String name = info.name();
          if (overwriteExisting || !validators.containsKey(name)) {
            validators.put(name, clazz);
            identifier.setName(name);
          }

          if (ObjectUtils.isNotEmpty(aliases)) {
            List<String> filteredAlias = new ArrayList<>();
            aliases.forEach(alias -> {
              String aliasName = alias.toLowerCase().trim();
              if (overwriteExisting || !validators.containsKey(aliasName)) {
                validators.put(aliasName, clazz);
                filteredAlias.add(aliasName);
              }
            });
            identifier.setAliases(filteredAlias);
          }

          if (StringUtils.isNotBlank(identifier.getName())) {
            VALIDATOR_LIST.add(identifier);
          }
        } catch (Exception ignored) {
          log.warn("#Fail to load validation logic for {}", clazz.getSimpleName());
        }
      }
    } catch (Exception err) {
      Thread.currentThread().interrupt();
      log.warn("#Fail to load validation logic, error {}", err.getMessage());
    }

    log.info("#Populating validators, Took {} to populate {} validators",
        DurationParser.formatDuration(stopwatch.stop().elapsed(TimeUnit.NANOSECONDS)),
        VALIDATOR_LIST.size());
    log.debug("#Available validators :\n{}", VALIDATOR_LIST.toString());
    return validators;
  }

  @SuppressWarnings("unchecked")
  private static <T> T convert(Object data, TypeReference<T> type) {
    T result = null;
    if (data != null) {
      JavaType javaType = MapperHelper.getGenericType(type);
      if (data.getClass().isAssignableFrom(javaType.getRawClass())) {
        return (T) data;
      } else if (String.class.isAssignableFrom(javaType.getRawClass())) {
        return (T) MapperHelper.toString(data);
      } else if (data instanceof String) {
        result = (T) CommonHelper.parseStringToObject((String) data);
      } else {
        result = MapperHelper.toObject(data, javaType);
      }
    }
    return result;
  }

  /**
   * Executes validation logic in parallel with thread safety guarantees.
   * <p>Current Implementation:</p>
   * - Creates new ValidatorLogic instance per validation (thread isolation)
   * - Caches TypeReferences to optimize reflection calls
   * - Maintains thread-safe object conversion caching
   *
   * @param validations List of validations to execute concurrently
   * @return List of validation results in the same order as input
   */
  @SneakyThrows
  @SuppressWarnings(value = {"unchecked", "rawtypes"})
  private static List<ValidatorResult> getValidationResult(List<DataValidation> validations) {
    List<ValidatorResult> results = new ArrayList<>();
    if (validations != null && !validations.isEmpty()) {
      ConcurrentMap<DataValidation, ValidatorResult> cachedValidation = new ConcurrentHashMap<>();

      int timeoutSeconds = properties().getTimeoutSeconds();
      final String executorName = "validator-helper";
      ExecutorService executor = ExecutorFactory.createVirtualThreadPerTaskExecutor(executorName);

      try {
        List<CompletableFuture<ValidatorResult>> futures = new ArrayList<>(validations.size());

        for (DataValidation validation : validations) {
          futures.add(CompletableFuture.supplyAsync(() -> cachedValidation.computeIfAbsent(validation, v -> {
            String validationName = v.getValidation().toLowerCase().trim();
            Class<?> validatorClass = REGISTERED_VALIDATORS.get(validationName);

            if (validatorClass == null) {
              return ValidatorResult.builder()
                  .validation(validationName)
                  .success(false)
                  .error(new Exception("Cannot find validation logic for: " + validationName))
                  .build();
            }

            ValidatorLogic logic = getInstance(validatorClass);
            if (logic == null) {
              return ValidatorResult.builder()
                  .validation(validationName)
                  .success(false)
                  .error(new Exception("Failed to create validator logic instance"))
                  .build();
            }

            try {
              logic.setActual(convert(v.getActual(), logic.getActualType()))
                  .setExpected(convert(v.getExpectation(), logic.getExpectedType()));

              ValidatorResult result = logic.result();
              if (result == null) {
                return ValidatorResult.builder()
                    .validation(validationName)
                    .success(false)
                    .error(new Exception("Validator logic returned null result"))
                    .build();
              }
              return result;

            } catch (Exception ex) {
              return ValidatorResult.builder().validation(validationName).success(false).error(ex).build();
            }
          }), executor));
        }

        // Wait for all tasks or timeout
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
          all.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          log.warn("Validation timed out after {} seconds", timeoutSeconds);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Validation interrupted: {}", e.getMessage());
        } catch (Exception e) {
          log.error("Unexpected error while awaiting validation results", e);
        }

        // Collect results in order
        for (CompletableFuture<ValidatorResult> f : futures) {
          results.add(f.getNow(ValidatorResult.builder()
              .success(false)
              .error(new TimeoutException("Validation did not complete in time"))
              .build()));
        }
      } catch (Exception err) {
        log.error("Validation failed {}", err.getMessage());
      } finally {
        ExecutorFactory.safeShutdown(executor, 10, executorName);
      }
    } else {
      log.warn("No validation provided, process will be skipped");
    }
    return results;
  }

  public static boolean isValid(DataValidation validation) {
    List<ValidatorResult> results = getValidationResult(Collections.singletonList(validation));
    return !results.isEmpty() && results.get(0).isSuccess();
  }

  /**
   * <p>validate.</p>
   *
   * @param validation a {@link io.github.ygrip.testara.validation.model.DataValidation} object.
   * @throws AssertionError if any.
   */
  @SneakyThrows
  public static void validate(DataValidation validation) {
    log.debug("#Validating data : \nvalidation : {}\nactual : {}\nexpected : {}",
        validation.getValidation(),
        validation.getActual(),
        validation.getExpectation());
    List<ValidatorResult> results = getValidationResult(Collections.singletonList(validation));
    if (!results.isEmpty()) {
      ValidatorResult result = results.get(0);
      if (!result.isSuccess()) {
        throw new AssertionError(String.format("Validation %s", result.getError().getMessage()), result.getError());
      }
    }
  }

  /**
   * <p>validates.</p>
   *
   * @param validations a {@link List} object.
   * @throws AssertionError if any.
   */
  public static void validates(List<DataValidation> validations) {
    if (validations == null || validations.isEmpty()) {
      log.warn("No validations to check");
    }
    final SoftAssertions softly = new SoftAssertions();
    List<ValidatorResult> results = getValidationResult(validations);

    for (int i = 0; i < results.size(); i++) {
      ValidatorResult result = results.get(i);
      String desc = String.format("Validation #%d [%s]", i + 1, result.getValidation());

      softly.assertThat(result.isSuccess()).as(desc).withFailMessage(() -> {
        Throwable error = result.getError();
        return error != null ? error.getMessage() : "Unknown error occurred";
      }).isTrue();
    }

    softly.assertAll();
  }

  /**
   * <p>getAvailableValidator.</p>
   *
   * @return a {@link List} object.
   */
  public static List<PopulatedTag> getAvailableValidator() {
    return VALIDATOR_LIST;
  }

  /**
   * <p>listValidatorCatalog.</p>
   * Returns a sorted list of all registered validators with their parameter type details.
   * Only primary validator names (not aliases) are included as top-level entries.
   *
   * @return a {@link List} of {@link ValidatorCatalogEntry} objects.
   */
  public static List<ValidatorCatalogEntry> listValidatorCatalog() {
    return REGISTERED_VALIDATORS.entrySet().stream()
        .filter(e -> {
          ValidatorInfo info = new ValidatorInfo(e.getValue());
          return e.getKey().equals(info.name()); // only primary names
        })
        .map(e -> {
          Class<?> clazz = e.getValue();
          ValidatorInfo info = new ValidatorInfo(clazz);
          String[] types = extractValidatorTypes(clazz);
          return new ValidatorCatalogEntry(
              info.name(),
              info.aliases(),
              types[0],
              types[1]
          );
        })
        .sorted(Comparator.comparing(ValidatorCatalogEntry::name))
        .collect(Collectors.toList());
  }

  private static String[] extractValidatorTypes(Class<?> clazz) {
    // Walk superclass chain to find ValidatorLogic<ACTUAL, EXPECTED>
    Class<?> current = clazz;
    while (current != null && !current.getSuperclass().equals(Object.class)) {
      java.lang.reflect.Type superType = current.getGenericSuperclass();
      if (superType instanceof java.lang.reflect.ParameterizedType pt) {
        java.lang.reflect.Type[] args = pt.getActualTypeArguments();
        if (args.length == 2) {
          return new String[]{typeSimpleName(args[0]), typeSimpleName(args[1])};
        }
      }
      current = current.getSuperclass();
    }
    return new String[]{"Object", "Object"};
  }

  private static String typeSimpleName(java.lang.reflect.Type type) {
    if (type instanceof Class<?> c) return c.getSimpleName();
    String name = type.getTypeName();
    int lastDot = name.lastIndexOf('.');
    return lastDot >= 0 ? name.substring(lastDot + 1) : name;
  }

  /**
   * {@inheritDoc}
   */
  @SneakyThrows
  public static void validate(String validationPath) {
    if (StringUtils.isBlank(validationPath)) {
      throw new InvalidParameterException("No validation file path provided");
    }
    log.trace("#Load validation from {}", validationPath);
    String fullPath = String.format("%s%s.%s", DEFAULT_REQUEST_FOLDER, validationPath, DEFAULT_FORMAT);
    File file = FileHelper.openFile(fullPath);
    if (!file.exists()) {
      throw new Exception(String.format("Cannot find validation at : %s", fullPath));
    }
    validates(new TransformerService().setTemplate(FileHelper.readFile(fullPath))
        .sourceData(null)
        .toList(DataValidation.class));
  }
}
