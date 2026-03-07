package io.github.ygrip.testara.ui.selenium.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.client.ClientUtil;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarQueryParam;
import com.browserup.harreader.model.HarRequest;
import com.browserup.harreader.model.HarResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.TestaraProxyModel;
import io.github.ygrip.testara.ui.model.CreateHarRequest;
import io.github.ygrip.testara.ui.model.CreateProxyRequest;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;
import io.github.ygrip.testara.ui.proxy.ProxyHelper;
import org.openqa.selenium.Proxy;

import lombok.extern.log4j.Log4j2;

/**
 * <p>BrowserUpProxyUtility class.</p>
 *
 * @author yunaz.ramadhan on 6/15/2020
 * @version $Id: $Id
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class BrowserUpProxyUtility extends AbstractProxy<Proxy> {
  private final HttpClient client;
  private BrowserUpProxy embeddedProxy;

  /**
   * <p>Constructor for BrowserUpProxyUtility.</p>
   *
   * @param dataHolder a {@link DataHolder} object.
   */
  public BrowserUpProxyUtility(DataHolder dataHolder) {
    super(dataHolder);
    client = HttpClient.newHttpClient();
  }

  private ProxyHelper getRemoteProxy() throws Exception {
    return new ProxyHelper(this.client, getProxyAddress());
  }

  /** {@inheritDoc} */
  @Override
  public void start() {
    String host = "http://localhost";
    String proxyAddress = null;
    if (CommonHelper.isBlank(getProxy())) {
      if (!CommonHelper.isBlank(getProxyType())) {
        if (getProxyType().equals(AvailableProxy.STANDALONE)) {
          log.debug("#Starting standalone browser up proxy");
          try {
            if (!getRemoteProxy().isStarted(getPort())) {
              setPort(getRemoteProxy().startProxy(CreateProxyRequest.builder()
                  .trustAllServers(true)
                  .build()));
              TestaraProxyModel proxyModel = new TestaraProxyModel(getProxyAddress());
              InetSocketAddress remoteProxy =
                  new InetSocketAddress(proxyModel.getProxyHost(), proxyModel.getProxyPort());
              setProxy(ClientUtil.createSeleniumProxy(remoteProxy));
              proxyAddress = String.format("%s://%s:%s",
                  proxyModel.getProtocol(),
                  proxyModel.getProxyHost(),
                  getPort());
            }
          } catch (Exception ignored) {
          }
        } else {
          log.debug("#Starting embedded browser up proxy");
          if (CommonHelper.isBlank(this.embeddedProxy)) {
            this.embeddedProxy = new BrowserUpProxyServer();
          }
          if (!this.embeddedProxy.isStarted()) {
            this.embeddedProxy.start();
          }
          setPort(this.embeddedProxy.getPort());
          setProxy(ClientUtil.createSeleniumProxy(this.embeddedProxy));
          proxyAddress = String.format("%s:%s", host, getPort());
        }
        getProxy().setSslProxy(proxyAddress);
        getProxy().setFtpProxy(proxyAddress);
        getProxy().setHttpProxy(proxyAddress);
      }
    }
    log.info("#Browser up proxy is started at {}", proxyAddress);
  }

  /** {@inheritDoc} */
  @Override
  public void stop() {
    if (!CommonHelper.isBlank(getProxyType())) {
      if (getProxyType().equals(AvailableProxy.STANDALONE)) {
        try {
          if (getRemoteProxy().isStarted(getPort())) {
            log.info("#Stopping standalone browser up proxy");
            getRemoteProxy().stopProxy(getPort());
          }
        } catch (Exception ignored) {
        }
      } else {
        if (!CommonHelper.isBlank(this.embeddedProxy) && this.embeddedProxy.isStarted()) {
          log.info("#Stopping embedded browser up proxy");
          try {
            this.embeddedProxy.stop();
          } catch (Exception ignored) {

          }
        }
      }
    } else {
      log.info("#No active browser up proxy available, ignoring action...");
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<HarRequest> getRequestData() {
    List<HarEntry> entries = getHar().getLog().getEntries();
    List<HarRequest> result = new ArrayList<>();
    for (HarEntry entry : entries) {
      result.add(entry.getRequest());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public List<HarResponse> getResponseData() {
    List<HarEntry> entries = getHar().getLog().getEntries();
    List<HarResponse> result = new ArrayList<>();
    for (HarEntry entry : entries) {
      result.add(entry.getResponse());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isStarted() {
    if (!CommonHelper.isBlank(getProxyType()) && !CommonHelper.isBlank(getProxy())) {
      try {
        TestaraProxyModel proxyModel = new TestaraProxyModel(getProxy().getHttpProxy());
        return getPort().equals(proxyModel.getProxyPort());
      } catch (Exception ignored) {
        return false;
      }
    } else {
      return false;
    }
  }

  /**
   * <p>newHar.</p>
   */
  public void newHar() {
    newHar("Page 1");
  }

  /**
   * <p>newHar.</p>
   *
   * @param page a {@link String} object.
   */
  public void newHar(String page) {
    newHar(page, page);
  }

  /**
   * <p>newHar.</p>
   *
   * @param page a {@link String} object.
   * @param reference a {@link String} object.
   */
  public void newHar(String page, String reference) {
    if (!CommonHelper.isBlank(getProxyType())) {
      if (getProxyType().equals(AvailableProxy.STANDALONE)) {
        try {
          getRemoteProxy().createHar(
            CreateHarRequest.builder()
              .initialPageRef(reference)
              .initialPageTitle(page)
              .captureContent(true)
              .captureCookies(true)
              .captureHeaders(true)
              .captureBinaryContent(true)
              .build(), getPort());
        } catch (Exception ignored) {

        }
      } else {
        if (!CommonHelper.isBlank(this.embeddedProxy)) {
          this.embeddedProxy.newHar(reference, page);
        }
      }
    } else {
      log.warn("Proxy is not initialized for current driver");
    }
  }

  /**
   * <p>getHar.</p>
   *
   * @return a {@link com.browserup.harreader.model.Har} object.
   */
  public Har getHar() {
    if (!CommonHelper.isBlank(getProxyType())) {
      if (getProxyType().equals(AvailableProxy.STANDALONE)) {
        try {
          return getRemoteProxy().getHar(getPort());
        } catch (Exception ignored) {
          return null;
        }
      } else {
        if (!CommonHelper.isBlank(this.embeddedProxy)) {
          return this.embeddedProxy.getHar(true);
        } else {
          return null;
        }
      }
    } else {
      log.warn("Proxy is not initialized for current driver");
      return null;
    }
  }

  /**
   * <p>writeHarToFile.</p>
   *
   * @param fullpath a {@link String} object.
   * @throws IOException if any.
   */
  public void writeHarToFile(String fullpath) throws IOException {
    FileHelper.writeJson(MapperHelper.toString(getHar()), fullpath);
  }

  /**
   * <p>parseHarQueryParam.</p>
   *
   * @param queryParams a {@link List} object.
   * @return a {@link Map} object.
   */
  public Map<String, Object> parseHarQueryParam(List<HarQueryParam> queryParams) {
    Map<String, Object> result = new HashMap<>();
    final JavaType type = MapperHelper.getGenericType(new TypeReference<List<Object>>() {
    });
    for (HarQueryParam queryParam : queryParams) {
      if (result.containsKey(queryParam.getName())) {
        List<Object> dataset = new ArrayList<>();
        try {
          dataset.addAll(Objects.requireNonNull(MapperHelper.toObject(result.get(queryParam.getName()), type)));
        } catch (Exception ignored) {

        }
        dataset.add(queryParam.getValue());
        result.put(queryParam.getName(), dataset);
      } else {
        result.put(queryParam.getName(), queryParam.getValue());
      }
    }
    return result;
  }

  public boolean isNetworkError(HarEntry entry) {
    if (entry == null || entry.getResponse() == null) {
      return true; // No response indicates an error
    }

    HarResponse response = entry.getResponse();
    int status = response.getStatus();

    // HTTP error status codes (4xx and 5xx)
    if (status >= 400) {
      return true;
    }

    // Connection errors (status -1 or 0)
    if (status <= 0) {
      return true;
    }

    // Check for timeout errors in response text
    String responseText = response.getContent() != null ? response.getContent().getText() : "";
    if (responseText != null && (responseText.toLowerCase().contains("timeout") ||
        responseText.toLowerCase().contains("connection refused") ||
        responseText.toLowerCase().contains("network error"))) {
      return true;
    }

    return false;
  }

  public boolean isSpecificErrorType(HarEntry entry, String errorType) {
    if (!isNetworkError(entry)) {
      return false;
    }

    if (entry.getResponse() == null) {
      return errorType.equals("connection") || errorType.equals("timeout");
    }

    int status = entry.getResponse().getStatus();

    switch (errorType) {
      case "client":
      case "4xx":
        return status >= 400 && status < 500;
      case "server":
      case "5xx":
        return status >= 500 && status < 600;
      case "timeout":
        return status <= 0 || (entry.getResponse().getContent() != null &&
            entry.getResponse().getContent().getText() != null &&
            entry.getResponse().getContent().getText().toLowerCase().contains("timeout"));
      case "connection":
        return status <= 0;
      case "ssl":
      case "tls":
        return status <= 0 && entry.getRequest().getUrl().startsWith("https");
      case "dns":
        return status <= 0 && (entry.getResponse().getContent() != null &&
            entry.getResponse().getContent().getText() != null &&
            entry.getResponse().getContent().getText().toLowerCase().contains("dns"));
      default:
        return false;
    }
  }

  public Map<String, Object> harEntryToErrorMap(HarEntry entry) {
    Map<String, Object> errorMap = new HashMap<>();

    if (entry.getRequest() != null) {
      errorMap.put("url", entry.getRequest().getUrl());
      errorMap.put("method", entry.getRequest().getMethod().toString());
    }

    if (entry.getResponse() != null) {
      errorMap.put("status", entry.getResponse().getStatus());
      errorMap.put("statusText", entry.getResponse().getStatusText());

      if (entry.getResponse().getContent() != null) {
        errorMap.put("responseText", entry.getResponse().getContent().getText());
        errorMap.put("mimeType", entry.getResponse().getContent().getMimeType());
      }
    } else {
      errorMap.put("status", -1);
      errorMap.put("statusText", "Connection Error");
    }

    errorMap.put("startedDateTime", entry.getStartedDateTime());
    errorMap.put("time", entry.getTime());

    return errorMap;
  }
}
