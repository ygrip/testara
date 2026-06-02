package io.github.ygrip.testara.ui.executor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.support.FetchParameter;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.error.ActionNotFoundException;
import io.github.ygrip.testara.ui.error.FailToParseParametersException;
import io.github.ygrip.testara.ui.model.Action;
import io.github.ygrip.testara.ui.model.OnPage;
import io.github.ygrip.testara.ui.model.UserActionModel;

import lombok.extern.log4j.Log4j2;

/**
 * <p>ActionResolver class.</p>
 *
 * @author yunaz.ramadhan on 8/21/2020
 * @version $Id: $Id
 */
@Log4j2
public class ActionResolver {
  private static final ReentrantLock actionLock = new ReentrantLock();
  private static volatile ObjectConverter converter;
  private static volatile Map<Class<?>, Map<String, UserActionModel>> AVAILABLE_ACTIONS;

  /**
   * <p>Constructor for ActionResolver.</p>
   */
  private ActionResolver() {

  }

  private static ObjectConverter converter() {
    if (converter == null) {
      converter = TestFramework.context()
        .converter();
    }
    return converter;
  }

  private static Map<Class<?>, Map<String, UserActionModel>> getAvailableActions() {
    Map<Class<?>, Map<String, UserActionModel>> result = AVAILABLE_ACTIONS;
    if (result == null) {
      actionLock.lock();
      try {
        if (AVAILABLE_ACTIONS == null) {
          AVAILABLE_ACTIONS = registerUserActions();
          log.debug("ActionResolver initialized (memory-optimized, lazy loading)");
        }
        result = AVAILABLE_ACTIONS;
      } finally {
        actionLock.unlock();
      }
    }
    return result;
  }

  private static Map<Class<?>, Map<String, UserActionModel>> registerUserActions() {
    Map<Class<?>, Map<String, UserActionModel>> result = new IdentityHashMap<>();
    DriverSession<?> session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    var configType = Optional.ofNullable(session)
      .map(DriverSession::configType)
      .orElse(null);
    AbstractDriverProperties config = ObjectUtils.isNotEmpty(configType) ?
      TestFramework.configuration()
        .get(configType) :
      null;
    ClassScanner scanner = TestFramework.context()
      .get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> loaded = ObjectUtils.isNotEmpty(config) ?
      scanner.scanOnPackages(UserAction.class, OnPage.class, config.getActionScanLocations()) :
      scanner.scan(UserAction.class, OnPage.class);
    try {
      List<Class<?>> resolved = loaded.get(60, TimeUnit.SECONDS);
      for (Class<?> clazz : resolved) {
        try {
          OnPage page = clazz.getAnnotation(OnPage.class);
          if (!CommonHelper.isBlank(page)) {
            for (Class<?> item : page.value()) {
              Map<String, UserActionModel> actions = result.getOrDefault(item, new HashMap<>());
              Map<String, UserActionModel> anonymousActions = result.getOrDefault(null, new HashMap<>());
              Map<String, UserActionModel> allActionInThisClass = getActions(clazz);
              Map<String, UserActionModel> anonymousActionInThisClass = allActionInThisClass.entrySet()
                .stream()
                .filter(action -> action.getValue()
                  .isAllowAnonymousCall())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
              actions.putAll(allActionInThisClass);
              anonymousActions.putAll(anonymousActionInThisClass);
              result.put(item, actions);
              result.put(null, anonymousActions);
            }
          }
        } catch (Exception ignored) {
        }
      }
    } catch (Exception ignored) {

    }
    return result;
  }

  private static Map<String, UserActionModel> getActions(Class<?> clazz) {
    Map<String, UserActionModel> result = new HashMap<>();
    final List<Method> methods = CommonHelper.getMethodsUpTo(clazz, UserAction.class);
    for (Method method : methods) {
      method.setAccessible(true);
      boolean isPublic = (method.getModifiers() & Modifier.PUBLIC) != 0;
      if (isPublic && method.isAnnotationPresent(Action.class)) {
        String identifier = method.getAnnotation(Action.class)
          .value();
        boolean anonymousCall = method.getAnnotation(Action.class)
          .allowAnonymousCall();
        UserActionModel action = UserActionModel.builder()
          .identifier(identifier)
          .methodName(method.getName())
          .allowAnonymousCall(anonymousCall)
          .actionClass(method.getDeclaringClass())
          .parameterSize(method.getParameterCount())
          .parameterTypes(method.getParameterTypes())
          .isStatic(Modifier.isStatic(method.getModifiers()))
          .parameterNames(Arrays.stream(method.getParameters())
            .map(Parameter::getName)
            .toArray(String[]::new))
          .build();
        result.put(identifier, action);
      }
    }
    return result;
  }

  private static Object getInstance(Class<?> clazz) {
    Object instance;
    try {
      instance = TestFramework.context()
        .get(clazz);
    } catch (Exception ignored) {
      instance = TestFramework.factory()
        .getInstance(clazz);
    }
    return instance;
  }

  /**
   * <p>doActionOnPage.</p>
   *
   * @param action              a {@link String} object.
   * @param page                a {@link Class} object.
   * @param additionalParameter a {@link Map} object.
   * @throws Exception if any.
   */
  public static void doActionOnPage(String action, Class<?> page, Map<String, Object> additionalParameter)
    throws Exception {
    String pageName = CommonHelper.isBlank(page) ? "anonymous" : page.getSimpleName();
    log.info("#Executing action {} on {} page", action, pageName);
    UserActionModel actionModel = findMatchingAction(page, action, additionalParameter);
    if (!CommonHelper.isBlank(actionModel)) {
      Method method = actionModel.getActionClass()
        .getDeclaredMethod(actionModel.getMethodName(), actionModel.getParameterTypes());
      method.setAccessible(true);
      Object[] args = parseParameters(
        new FetchParameter(actionModel.getIdentifier()).fromInput(action)
          .getParameters(), actionModel.getParameterTypes(), additionalParameter
      );
      try {
        if (actionModel.isStatic()) {
          method.invoke(null, args);
        } else {
          Object instance = getInstance(actionModel.getActionClass());
          method.invoke(instance, args);
        }
      } catch (Exception e) {
        throw new Exception("Fail to execute action " + action + " on " + pageName + " page", e.getCause());
      }
    }
  }

  private static UserActionModel findMatchingAction(Class<?> currentPage, String identifier,
    Map<String, Object> additionalParameter) throws ActionNotFoundException {
    String pageName = CommonHelper.isBlank(currentPage) ? "anoymous" : currentPage.getSimpleName();
    boolean useAnonymousCall = CommonHelper.isBlank(currentPage);
    UserActionModel action = null;
    final var allActions = getAvailableActions();
    Map<String, UserActionModel> actions = allActions.getOrDefault(currentPage, new HashMap<>());
    if (!CommonHelper.isBlank(actions)) {
      action = matchingAction(identifier, actions, additionalParameter);
    }

    //fetch for action that allows anonymous call
    if (CommonHelper.isBlank(action) && !useAnonymousCall) {
      actions = allActions.getOrDefault(null, new HashMap<>());
      if (CommonHelper.isBlank(actions)) {
        throw new ActionNotFoundException("#ERROR Cannot find any user actions");
      } else {
        useAnonymousCall = true;
        action = matchingAction(identifier, actions, additionalParameter);
      }
    }

    if (CommonHelper.isBlank(action)) {
      throw new ActionNotFoundException(String.format(
        "#ERROR Cannot find matching action for \"%s\" in %s page",
        identifier,
        pageName
      ));
    } else {
      if (useAnonymousCall) {
        log.debug("#Found action \"{}\" using anonymous call from {} page", identifier, pageName);
      } else {
        log.debug("#Found action \"{}\" in {} pageName", identifier, pageName);
      }
    }
    return action;
  }

  private static UserActionModel matchingAction(String identifier, Map<String, UserActionModel> actions,
    Map<String, Object> additionalParameter) {
    for (Map.Entry<String, UserActionModel> action : actions.entrySet()) {
      FetchParameter fetchParameter = new FetchParameter(action.getKey()).fromInput(identifier);
      if (fetchParameter.matchPattern()) {
        int desiredParameterSize = CommonHelper.isBlank(additionalParameter) ?
          fetchParameter.getParameters()
            .size() :
          fetchParameter.getParameters()
            .size() + 1;
        if (action.getValue()
          .getParameterSize()
          .equals(desiredParameterSize)) {
          return action.getValue();
        }
      }
    }
    return null;
  }

  private static Object[] parseParameters(List<String> input, Class<?>[] expectedType,
    Map<String, Object> additionalParameter) throws FailToParseParametersException {
    final var expectedSize = determineParameterSize(input, additionalParameter);
    Object[] result = new Object[expectedSize];
    if (expectedType.length != expectedSize) {
      throw new FailToParseParametersException(String.format(
        "#ERROR Number of parameters to parse did not match the required parameter\n<expect> : %s\n<got> : %s",
        expectedType.length,
        expectedSize
      ));
    }
    for (int i = 0; i < input.size(); i++) {
      result[i] = String.class.isAssignableFrom(expectedType[i]) ?
        MapperHelper.toString(converter().convert(input.get(i))) :
        MapperHelper.toObject(converter().convert(input.get(i)), expectedType[i]);
    }
    if (!CommonHelper.isBlank(additionalParameter)) {
      result[expectedSize - 1] = additionalParameter;
    }
    return result;
  }

  private static int determineParameterSize(List<String> input, Map<String, Object> additionalParameter) {
    var size = Optional.ofNullable(input)
      .map(List::size)
      .orElse(0);
    if (additionalParameter != null) {
      size++;
    }
    return size;
  }
}
