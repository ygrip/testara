package io.github.ygrip.testara.api;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static io.github.ygrip.testara.core.support.CommonHelper.mergeMapObject;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.tika.Tika;

import io.github.ygrip.testara.api.config.ServiceConfig;
import io.github.ygrip.testara.api.config.SharedServiceConfigCache;
import io.github.ygrip.testara.api.interceptor.RequestInterceptor;
import io.github.ygrip.testara.api.interceptor.RequestInterceptorLoader;
import io.github.ygrip.testara.api.interceptor.ResponseInterceptor;
import io.github.ygrip.testara.api.interceptor.ResponseInterceptorLoader;
import io.github.ygrip.testara.api.model.ApiModel;
import io.github.ygrip.testara.api.model.CookieModel;
import io.github.ygrip.testara.api.model.CreateRequestSpecification;
import io.github.ygrip.testara.api.model.ProxyModel;
import io.github.ygrip.testara.api.model.RequestLog;
import io.github.ygrip.testara.api.model.ResponseLog;
import io.github.ygrip.testara.api.support.CookieHelper;
import io.github.ygrip.testara.api.support.VirtualRestAssured;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.transformer.TransformerService;

import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.MultiPartSpecification;
import io.restassured.specification.ProxySpecification;
import io.restassured.specification.RequestSpecification;
import lombok.extern.log4j.Log4j2;

/**
 * <p>RequestBuilderImpl class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class RequestBuilderImpl implements RequestBuilder, RestApiFacade {
  private static final String DEFAULT_FORMAT = "json";
  private final String REQUEST_FOLDER;
  private final Map<String, RestAssuredConfig> configs;
  private final ConcurrentHashMap<String, RequestInternal> requests;
  private final ObjectConverter converter;

  // New caching mechanism with TTL and size limits
  private final SharedServiceConfigCache sharedServiceConfigCache;
  private final List<Class<? extends RequestInterceptor>> requestInterceptors;
  private final List<Class<? extends ResponseInterceptor>> responseInterceptors;
  private String currentServiceName;

  /**
   * <p>Constructor for RequestBuilderImpl.</p>
   *
   * @param sharedServiceConfigCache a {@link io.github.ygrip.testara.api.config.SharedServiceConfigCache} object.
   */
  public RequestBuilderImpl(SharedServiceConfigCache sharedServiceConfigCache) {
    this.sharedServiceConfigCache = sharedServiceConfigCache;
    this.converter = ObjectConverterLoader.instance();

    String scriptFolder = TestFramework.context()
      .configuration()
      .get(DefaultProperties.class)
      .getScriptFolder();
    this.REQUEST_FOLDER = String.format(
      "%s%s",
      System.getProperty("user.dir"),
      isBlank(scriptFolder) ? "/src/test/resources/" : scriptFolder
    );
    this.configs = new HashMap<>();
    this.requests = new ConcurrentHashMap<>();

    requestInterceptors = RequestInterceptorLoader.loads();
    responseInterceptors = ResponseInterceptorLoader.loads();
  }

  private void init(String serviceName) {
    this.currentServiceName = serviceName;

    if (!this.requests.containsKey(serviceName)) {

      log.debug("#Initializing Request specification for {}", serviceName);

      ServiceConfig serviceConfig = this.sharedServiceConfigCache.getServiceConfig(serviceName);
      ApiModel model = serviceConfig != null ? serviceConfig.getApiModel() : null;
      this.requests.put(this.currentServiceName, new RequestInternal(model, getConfig(serviceName)));
    }
  }

  private RestAssuredConfig getConfig(String currentServiceName) {
    if (!this.configs.containsKey(currentServiceName)) {
      ServiceConfig serviceConfig = this.sharedServiceConfigCache.getServiceConfig(currentServiceName);
      ApiModel model = serviceConfig != null ? serviceConfig.getApiModel() : null;

      RestAssuredConfig config = config();

      if (model != null) {
        config.encoderConfig(RestAssuredConfig.config()
          .getEncoderConfig()
          .appendDefaultContentCharsetToContentTypeIfUndefined(model.isApplyDefaultContentIfUndefined()));
        if (model.isAutoCloseIdleConnection()) {
          config.connectionConfig(RestAssuredConfig.config()
            .getConnectionConfig()
            .closeIdleConnectionsAfterEachResponse());
        }
        if (model.isFollowRedirects()) {
          config.redirect(RedirectConfig.redirectConfig()
            .followRedirects(true)
            .maxRedirects(model.getMaxRedirect()));
        }

        // Override with model-specific settings if provided
        if (model.isReuseHttpClientInstance()) {
          config.httpClient(HttpClientConfig.httpClientConfig()
            .reuseHttpClientInstance());
        }
      }

      this.configs.put(currentServiceName, config);
    }

    return this.configs.get(currentServiceName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void checkRequest() throws Exception {
    checkRequest(this.currentServiceName);
  }

  private void checkRequest(String currentServiceName) throws Exception {
    if (isBlank(currentServiceName)) {
      throw new Exception("service name should not be empty ");
    }
    init(currentServiceName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setService(String serviceName) {
    init(serviceName);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addHeaders(Map<String, Object> headers) throws Exception {
    getInternal().addHeaders(headers);
    log.debug("#Add headers :\n{}", headers);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addHeader(String key, String value) throws Exception {
    getSpecification().addHeader(key, converter.convert(value));
    log.debug("#Add header :\n{} : {}", key, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addFormParams(Map<String, Object> parameters) throws Exception {
    getInternal().addForms(parameters);
    log.debug("#Set default form parameters :\n{}", parameters);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addQueryParam(String key, Object value) throws Exception {
    getInternal().addParameter(key, converter.convert(value));
    log.debug("#Add query parameter :\n{} : {}", key, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addQueryParams(Map<String, Object> value) throws Exception {
    getInternal().addParameters(value);
    log.debug("#Add query parameters :\n{}", value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setPathParam(String key, Object value) throws Exception {
    getInternal().addPathParameter(key, converter.convert(value));
    log.debug("#Set path parameter :\n{} : {}", key, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setBody(Object body) throws Exception {
    if (body != null) {
      getInternal().getRequest().setBody(body);
      log.trace("#Set request body :\n{}", body);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder addFormParam(String key, Object value) throws Exception {
    getInternal().addForm(key, converter.convert(value));
    log.debug("#Add form parameter :\n{} : {}", key, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setCookie(Cookie cookie) throws Exception {
    getInternal().getRequest()
      .addCookie(cookie);
    log.debug("#Set cookie : {}", cookie);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setCookies(Cookies cookies) throws Exception {
    getInternal().getRequest()
      .addCookies(cookies);
    log.debug("#Set cookies : {}", cookies);
    return this;
  }

  @Override
  public RequestSpecBuilder getSpecification() throws Exception {
    return getInternal(this.currentServiceName).getRequest();
  }

  private RequestInternal getInternal() throws Exception {
    return getInternal(this.currentServiceName);
  }

  private RequestInternal getInternal(String serviceName) throws Exception {
    checkRequest(serviceName);
    return this.requests.get(serviceName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestSpecification buildSpecification() throws Exception {
    String serviceName = this.currentServiceName;
    ServiceConfig serviceConfig = this.sharedServiceConfigCache.getServiceConfig(serviceName);

    if (serviceConfig == null) {
      log.debug("No service configuration found for: {}", serviceName);
      serviceConfig = ServiceConfig.builder()
        .build(); // Empty config as fallback
    }

    RequestInternal internal = getInternal(serviceName);

    RequestSpecification specification = VirtualRestAssured.given(internal.getRequest()
      .build());

    // Resolve query parameters with new caching approach
    Map<String, Object> queryParams =
      mergeMapObject(internal.getParameters(), resolveServiceValues(serviceConfig.getParameters(), serviceName));
    specification.queryParams(queryParams);

    // Resolve form parameters with new caching approach
    Map<String, Object> formParams =
      mergeMapObject(internal.getForms(), resolveServiceValues(serviceConfig.getFormParams(), serviceName));
    specification.formParams(formParams);

    // Resolve headers with new caching approach
    Map<String, Object> headers =
      mergeMapObject(internal.getHeaders(), resolveServiceValues(serviceConfig.getHeaders(), serviceName));
    specification.headers(headers);

    // Resolve paths
    Map<String, Object> paths = internal.getPaths();
    if (!CommonHelper.isBlank(paths)) {
      specification.pathParams(paths);
    }

    return specification;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setMultiPartData(String key, Object value) throws Exception {
    checkRequest();
    MultiPartSpecification multiPartSpecification = null;
    try {
      key = converter.convert(key)
        .toString();
      Object data = converter.convert(value);
      if (ObjectUtils.isNotEmpty(data)) {
        switch (data) {
          case File file -> {
            Tika tika = new Tika();
            String mimeType = tika.detect(file);
            multiPartSpecification = new MultiPartSpecBuilder(file).controlName(key)
              .fileName(file.getName())
              .mimeType(mimeType)
              .build();
          }
          case InputStream inputStream -> {
            Tika tika = new Tika();
            String mimeType = tika.detect(inputStream);
            multiPartSpecification = new MultiPartSpecBuilder(inputStream).controlName(key)
              .mimeType(mimeType)
              .build();
          }
          case String s -> {
            Tika tika = new Tika();
            String mimeType = tika.detect(s);
            multiPartSpecification = new MultiPartSpecBuilder(s).controlName(key)
              .mimeType(mimeType)
              .build();
          }
          default -> multiPartSpecification = new MultiPartSpecBuilder(data).controlName(key)
            .build();
        }
      }
    } catch (Exception ignored) {

    }
    setMultiPartData(multiPartSpecification);
    return this;
  }

  @Override
  public RequestBuilder setMultiPartData(MultiPartSpecification multiPartData) throws Exception {
    if (isBlank(multiPartData)) {
      log.warn("No multipart data to pass");
    } else {
      log.debug(
        "#Set multipart data for control:{}\nname:{}\ntype:{}",
        multiPartData.getControlName(),
        multiPartData.getFileName(),
        multiPartData.getMimeType()
      );
      getInternal().getRequest()
        .addMultiPart(multiPartData);
    }
    return this;
  }

  /**
   * Resolve service values using the new shared configuration cache approach
   * Only CommandModel values are resolved and cached per thread; static values are shared
   *
   * @param parsedConfig the parsed configuration from shared cache
   * @param serviceName  service name for caching context
   * @return resolved map combining static and resolved command values
   */
  private Map<String, Object> resolveServiceValues(ServiceConfig.ParsedConfig parsedConfig, String serviceName) {
    Map<String, Object> result = new HashMap<>();

    if (parsedConfig == null || parsedConfig.isEmpty()) {
      return result;
    }

    parsedConfig.getStaticValues()
      .forEach((key, value) -> {
        result.put(key, converter.convert(value));
      });

    return result;
  }

  /**
   * Clear all caches - CONSISTENT STRATEGY
   */
  @Override
  public void clearAllCaches() {
    try {

      // CONSISTENT CACHING: Close all RequestInternal objects properly
      for (RequestInternal internal : this.requests.values()) {
        try {
          internal.close(); // Full destruction when clearing all caches
        } catch (Exception e) {
          log.trace("Error closing RequestInternal: {}", e.getMessage());
        }
      }

      // Clear all cached objects with consistent strategy
      this.requests.clear();
      this.configs.clear();

      // Reset current service name
      this.currentServiceName = null;

    } catch (Exception e) {
      log.warn("Error during cache clearing: {}", e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Response process(Method httpMethod, String url) throws Exception {
    RequestSpecification specification = buildSpecification();
    interceptRequest(specification, Collections.emptySet());
    Response response = process(specification, httpMethod, url);
    interceptResponse(currentServiceName, response, Collections.emptySet());
    return response;
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> interceptors(List<Class<? extends T>> types) {
    List<T> results = new ArrayList<>();
    types.forEach(type -> {
      try {
        T instance = (T) type.getDeclaredConstructors()[0].newInstance();
        results.add(instance);
      } catch (Exception ignored) {

      }
    });
    return results;
  }

  private void interceptRequest(RequestSpecification specification, Set<RequestLog> logLevels) {
    List<RequestInterceptor> interceptors = interceptors(requestInterceptors);
    interceptors.sort(Comparator.comparing(RequestInterceptor::priority));
    final TestContext context = TestFramework.context();
    interceptors.forEach(requestInterceptor -> requestInterceptor.logs(logLevels)
      .context(context)
      .intercept(specification));
  }

  private void interceptResponse(String serviceName, Response response, Set<ResponseLog> logLevels) {
    List<ResponseInterceptor> interceptors = interceptors(responseInterceptors);
    interceptors.sort(Comparator.comparing(ResponseInterceptor::priority));
    final TestContext context = TestFramework.context();
    interceptors.forEach(responseInterceptor -> responseInterceptor.service(serviceName)
      .logs(logLevels)
      .context(context)
      .intercept(response));
  }

  private Response process(RequestSpecification specification, Method method, String url) {
    log.debug("#{} request to {}", method, url);
    return VirtualRestAssured.asyncCall(() -> {
      Response response = null;
      String serviceName = this.currentServiceName;
      try {
        response = switch (method) {
          case POST -> specification.post(url);
          case PUT -> specification.put(url);
          case DELETE -> specification.delete(url);
          case PATCH -> specification.patch(url);
          case HEAD -> specification.head(url);
          case OPTIONS -> specification.options(url);
          case GET -> specification.get(url);
          default -> specification.request(method, url);
        };
      } catch (Exception e) {
        log.error("#ERROR while sending {} request to {}, log :", method, url, e);
      } finally {
        // CONSISTENT CACHING: Clear state but keep RequestInternal cached for reuse
        RequestInternal internal = this.requests.get(serviceName);
        if (internal != null) {
          try {
            internal.clearRequestState(this.configs.get(serviceName)); // Clear state but keep RequestSpecBuilder
          } catch (Exception ignored) {
          }
        }
      }
      this.currentServiceName = serviceName;

      return response;
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Response process(String httpMethod, String url) throws Exception {
    Method method = CommonHelper.searchEnum(Method.class, httpMethod);
    method = isBlank(method) ? Method.GET : method;
    return process(method, url);
  }

  @Override
  public Response process(String requestSpecificationPath) throws Exception {
    log.debug("#Load request specification for {}", requestSpecificationPath);
    String fullPath = String.format("%s%s.%s", REQUEST_FOLDER, requestSpecificationPath, DEFAULT_FORMAT);
    File file = FileHelper.openFile(fullPath);
    if (!file.exists()) {
      throw new Exception(String.format("Cannot find request specification : %s", fullPath));
    }
    return process(new TransformerService().setTemplate(FileHelper.readFile(fullPath))
      .to(CreateRequestSpecification.class));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Response process(CreateRequestSpecification request) throws Exception {
    Response response;
    String serviceName = request.getSpecification();
    init(serviceName);
    setRequestContentType(request.getContentType());
    setBody(request.getPayload());
    if (!isBlank(request.getCookies())) {
      for (CookieModel cookie : request.getCookies()) {
        setCookie(CookieHelper.buildCookie(cookie));
      }
    }
    if (!isBlank(request.getMultiPartData())) {
      for (String key : request.getMultiPartData()
        .keySet()) {
        setMultiPartData(
          key,
          request.getMultiPartData()
            .get(key)
        );
      }
    }
    if (!isBlank(request.getQueryParameters())) {
      addQueryParams(request.getQueryParameters());
    }
    if (!isBlank(request.getPathParameters())) {
      for (String key : request.getPathParameters()
        .keySet()) {
        setPathParam(
          key,
          request.getPathParameters()
            .get(key)
        );
      }
    }
    if (!isBlank(request.getHeaders())) {
      addHeaders(request.getHeaders());
    }
    if (!isBlank(request.getFormParameters())) {
      for (String key : request.getFormParameters()
        .keySet()) {
        addFormParam(
          key,
          request.getFormParameters()
            .get(key)
        );
      }
    }
    if (request.isAutoCloseConnection()) {
      getInternal(serviceName).getRequest()
        .setConfig(getConfig(serviceName).connectionConfig(RestAssuredConfig.config()
          .getConnectionConfig()
          .closeIdleConnectionsAfterEachResponse()));
    }

    RequestSpecification specification = buildSpecification();
    Set<RequestLog> logLevels;
    if (request.getRequestLog() == null) {
      logLevels = new HashSet<>();
    } else {
      logLevels = request.getRequestLog();
    }
    interceptRequest(specification, logLevels);
    response = process(specification, request.getHttpMethod(), request.getUrl());
    interceptResponse(serviceName, response, request.getResponseLog());

    return response;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setRequestContentType(ContentType contentType) throws Exception {
    getInternal().getRequest()
      .setContentType(contentType);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RequestBuilder setRequestContentType(String contentType) throws Exception {
    ContentType type = ContentType.ANY;
    try {
      type = ContentType.fromContentType(contentType);
    } catch (Exception ignored) {

    }
    setRequestContentType(type);
    return this;
  }

  @Override
  public String serviceName() {
    return this.currentServiceName;
  }

  private static class RequestInternal implements AutoCloseable {
    private final Map<String, Object> parameters;
    private final Map<String, Object> forms;
    private final Map<String, Object> headers;
    private final Map<String, Object> paths;
    private final ContentType defaultContentType;
    private final String baseUri;
    private final String basePath;
    private final Integer port;
    private final ProxySpecification proxy;
    private RequestSpecBuilder requestSpecBuilder;

    RequestInternal(ApiModel model, RestAssuredConfig config) {
      this.parameters = new HashMap<>();
      this.forms = new HashMap<>();
      this.headers = new HashMap<>();
      this.paths = new HashMap<>();

      String baseUri = "";
      String basePath = "";
      Integer port = null;
      ProxySpecification proxy = null;

      if (model != null) {
        baseUri = isBlank(model.getHost()) ? "http://localhost" : model.getHost();
        basePath = isBlank(model.getBasePath()) ? "/" : model.getBasePath();
        port = model.getPort();

        if (model.getProxy() != null) {
          ProxyModel proxyModel = model.getProxy();
          proxy = new ProxySpecification(proxyModel.getHost(), proxyModel.getPort(), proxyModel.getScheme());
          if (proxyModel.isWithAuthentication()) {
            proxy.withAuth(proxyModel.getUsername(), proxyModel.getPassword());
          }
        }
      }

      this.baseUri = baseUri;
      this.basePath = basePath;
      this.port = port;
      this.proxy = proxy;
      this.defaultContentType = getDefaultContentType(model);
      this.requestSpecBuilder = rebuild(config);
    }

    RequestSpecBuilder rebuild(RestAssuredConfig config) {
      RequestSpecBuilder request = new RequestSpecBuilder().setConfig(config)
        .setBasePath(this.basePath)
        .setContentType(this.defaultContentType)
        .setRelaxedHTTPSValidation();
      if (!isBlank(this.baseUri)) {
        request.setBaseUri(this.baseUri);
      }
      if (!isBlank(this.port)) {
        request.setPort(this.port);
      }
      if (!isBlank(this.proxy)) {
        request.setProxy(this.proxy);
      }
      return request;
    }

    void addHeader(String key, Object value) {
      this.headers.put(key, value);
    }

    void addHeaders(Map<String, Object> headers) {
      this.headers.putAll(headers);
    }

    void addParameter(String key, Object value) {
      this.parameters.put(key, value);
    }

    void addParameters(Map<String, Object> parameters) {
      this.parameters.putAll(parameters);
    }

    void addPathParameter(String key, Object value) {
      this.paths.put(key, value);
    }

    void addPathParameters(Map<String, Object> paths) {
      this.paths.putAll(paths);
    }

    void addForm(String key, Object value) {
      this.forms.put(key, value);
    }

    void addForms(Map<String, Object> forms) {
      this.forms.putAll(forms);
    }

    ContentType getDefaultContentType(ApiModel model) {
      String contentType = ContentType.ANY.toString();
      if (model != null) {
        if (!isBlank(model.getHeader())) {
          Optional<Map.Entry<String, Object>> entry = model.getHeader()
            .entrySet()
            .stream()
            .filter(header -> header.getKey()
              .trim()
              .equalsIgnoreCase(CONTENT_TYPE.trim()))
            .findFirst();
          if (entry.isPresent()) {
            contentType = isBlank(entry.get()
              .getValue()) ?
              contentType :
              entry.get()
                .getValue()
                .toString();
          }
        }
      }

      ContentType result;
      try {
        result = ContentType.fromContentType(contentType);
      } catch (Exception ignored) {
        result = ContentType.ANY;
      }

      return result;
    }

    RequestSpecBuilder getRequest() {
      return this.requestSpecBuilder;
    }

    Map<String, Object> getParameters() {
      return this.parameters;
    }

    Map<String, Object> getHeaders() {
      return this.headers;
    }

    Map<String, Object> getPaths() {
      return this.paths;
    }

    Map<String, Object> getForms() {
      return this.forms;
    }

    /**
     * Clear request state but keep RequestSpecBuilder for reuse (consistent caching)
     */
    void clearRequestState(RestAssuredConfig config) {
      this.parameters.clear();
      this.headers.clear();
      this.forms.clear();
      this.paths.clear();
      if (config != null) {
        this.requestSpecBuilder = rebuild(config);
      }
    }

    @Override
    public void close() throws Exception {
      clearRequestState(null);
      VirtualRestAssured.close();
      this.requestSpecBuilder = null; // Only null when fully destroying
      log.trace("RequestInternal fully closed and destroyed");
    }
  }
}