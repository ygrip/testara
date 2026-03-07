package io.github.ygrip.testara.ui.driver;

import java.io.File;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.error.UnrecognizedApplicationException;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.DeviceDimension;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.model.EmulationModel;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class AbstractDriver<D, O> {
  @Getter
  private final Class<D> driverType;
  @Getter
  private final Class<O> optionsType;
  private final DriverMetadata metadata;
  private final String driverName;
  private String owner;
  @Getter
  private DeviceType deviceType;
  @Getter
  private AvailableProxy proxyType;
  @Getter
  private boolean isHeadless;
  @Getter
  private EmulationModel emulationModel;
  @Getter
  private String userAgent;
  @Getter
  private List<String> arguments;
  @Getter
  private String binaryPath;

  public AbstractDriver() {
    this.metadata = this.getClass()
      .getAnnotation(DriverMetadata.class);
    Map.Entry<Class<D>, Class<O>> parameterizedInfo = constructParameterizedInfo();
    this.driverType = parameterizedInfo.getKey();
    this.optionsType = parameterizedInfo.getValue();
    this.driverName = this.metadata.name()
      .trim()
      .toLowerCase();
  }

  public AbstractDriver<D, O> withBinaryPath(String binaryPath) {
    this.binaryPath = binaryPath;
    return this;
  }

  public AbstractDriver<D, O> forDevice(DeviceType deviceType) {
    if (!Arrays.asList(this.metadata.platforms())
      .contains(deviceType)) {
      throw new UnrecognizedApplicationException(String.format(
        "Driver with name %s (%s), is not compatible with %s platform. Supported platform : %s",
        this.driverName,
        this.getClass()
          .getName(),
        deviceType.name(),
        supportedDeviceAsString(this.metadata.platforms())
      ));
    }
    this.deviceType = deviceType;
    return this;
  }

  public AbstractDriver<D, O> withUserAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  public AbstractDriver<D, O> withOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public AbstractDriver<D, O> withEmulation(EmulationModel emulationModel) {
    this.emulationModel = emulationModel;
    return this;
  }

  public AbstractDriver<D, O> withArguments(List<String> arguments) {
    this.arguments = arguments;
    return this;
  }

  public AbstractDriver<D, O> withProxyType(AvailableProxy proxy) {
    this.proxyType = proxy;
    return this;

  }

  private String supportedDeviceAsString(DeviceType[] deviceTypes) {
    return Arrays.stream(deviceTypes)
      .map(Enum::name)
      .collect(Collectors.joining(", "));
  }

  public AbstractDriver<D, O> headless(boolean isHeadless) {
    this.isHeadless = isHeadless;
    return this;
  }

  public abstract D create(O options);

  /**
   * <p>proxyOptions.</p>
   *
   * @return a O object.
   */
  public abstract O proxyOptions();

  /**
   * <p>mobileOptions.</p>
   *
   * @return a O object.
   */
  public abstract O mobileOptions();

  /**
   * <p>defaultOptions.</p>
   *
   * @return a O object.
   */
  public abstract O defaultOptions();

  /**
   * <p>isJavaScriptEnabled.</p>
   *
   * @return a boolean.
   */
  protected abstract boolean isJavaScriptEnabled();

  public DeviceDimension getDimension(EmulationModel emulation) {
    DeviceDimension dimension = Optional.ofNullable(emulation)
      .map(EmulationModel::getDimension)
      .orElse(new DeviceDimension());
    DeviceType deviceType = Optional.ofNullable(getDeviceType())
      .orElse(DeviceType.DEFAULT);

    Integer height = Optional.of(dimension)
      .map(DeviceDimension::getHeight)
      .filter(ObjectUtils::isNotEmpty)
      .filter(val -> val > 0)
      .orElseGet(() -> {
        if (deviceType.equals(DeviceType.DEFAULT) || deviceType.equals(DeviceType.DESKTOP)) {
          return 720;
        } else {
          return 640;
        }
      });
    Integer width = Optional.of(dimension)
      .map(DeviceDimension::getWidth)
      .filter(ObjectUtils::isNotEmpty)
      .filter(val -> val > 0)
      .orElseGet(() -> {
        if (deviceType.equals(DeviceType.DEFAULT) || deviceType.equals(DeviceType.DESKTOP)) {
          return 1280;
        } else {
          return 360;
        }
      });
    Double pixelRatio = Optional.of(dimension)
      .map(DeviceDimension::getPixelRatio)
      .filter(ObjectUtils::isNotEmpty)
      .filter(val -> val > 0.0)
      .orElse(0.0);
    dimension.setHeight(height);
    dimension.setWidth(width);
    dimension.setPixelRatio(pixelRatio);
    return dimension;
  }

  /**
   * <p>getDefaultDownloadLocation.</p>
   *
   * @return a {@link java.lang.String} object.
   */
  protected String getDownloadLocation() {
    StringBuilder builder = new StringBuilder();
    builder.append(System.getProperty("user.dir"));
    builder.append("/target/downloads/");
    if (!CommonHelper.isBlank(this.deviceType)) {
      builder.append(this.deviceType.name()
        .toLowerCase());
      builder.append(File.separator);
    }
    if (!CommonHelper.isBlank(this.metadata.name())) {
      builder.append(this.metadata.name()
        .trim()
        .toLowerCase());
      builder.append(File.separator);
    }
    String path = builder.toString();
    try {
      Files.createDirectories(Paths.get(path));
      return path;
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * <p>getDownloadedFiles.</p>
   *
   * @return a {@link java.util.List} object.
   */
  public List<File> getDownloadedFiles() {
    return FileHelper.openFiles(getDownloadLocation());
  }

  @SuppressWarnings("unchecked")
  private Map.Entry<Class<D>, Class<O>> constructParameterizedInfo() {
    Class<?> clazz = getClass();
    while (!Modifier.isAbstract(clazz.getSuperclass()
      .getModifiers())) {
      clazz = clazz.getSuperclass();
    }
    Class<?> finalClazz = clazz;
    Class<D> driver = (Class<D>) resolveParameterType(CommonHelper.getParameterizedType(finalClazz, 0));
    Class<O> option = (Class<O>) resolveParameterType(CommonHelper.getParameterizedType(finalClazz, 1));
    return new AbstractMap.SimpleEntry<>(driver, option);
  }

  private Class<?> resolveParameterType(Type type) {
    if (type instanceof ParameterizedType) {
      return ((ParameterizedType) type).getRawType()
        .getClass();
    } else {
      return (Class<?>) type;
    }
  }

  /**
   * <p>Getter for the field <code>driverName</code>.</p>
   *
   * @return a {@link java.lang.String} object.
   */
  public String driverName() {
    return this.driverName;
  }

  public DriverMetadata metadata() {
    return this.metadata;
  }

  protected Map<String, Object> getDeviceMetrics() {
    Map<String, Object> deviceMetrics;
    EmulationModel emulation = getEmulationModel();
    deviceMetrics = MapperHelper.toObject(
      getDimension(getEmulationModel()), new TypeReference<>() {
      }
    );

    Map<String, Object> mobileEmulation = new HashMap<>();
    String deviceName = Optional.ofNullable(emulation)
      .map(EmulationModel::getDeviceName)
      .orElse(null);
    if (StringUtils.isNotBlank(deviceName)) {
      mobileEmulation.put("deviceName", deviceName);
    } else if (ObjectUtils.isNotEmpty(deviceMetrics)) {
      mobileEmulation.put("deviceMetrics", deviceMetrics);
      if (StringUtils.isNotBlank(getUserAgent())) {
        mobileEmulation.put("userAgent", getUserAgent());
      }
    }

    return mobileEmulation;
  }

  public String owner() {
    return owner;
  }
}
