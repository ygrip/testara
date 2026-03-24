package io.github.ygrip.testara.ui.driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DriverInstances {
  private final ConcurrentHashMap<String, DriverSession<?>> driverMap = new ConcurrentHashMap<>();
  private final ThreadLocal<Set<String>> driversUsedInCurrentThread = new ThreadLocal<>();
  private String currentDriver;
  private DriverSession<?> currentActiveDriver;

  DriverInstances() {

  }

  public DriverSession<?> getCurrentDriver() {
    if (this.currentDriver == null) {
      return null;
    }
    return this.driverMap.get(this.currentDriver);
  }

  public DriverSession<?> getDriver(String driverName) {
    if (driverName == null) {
      return null;
    }
    return this.driverMap.get(driverName);
  }

  public String getCurrentDriverName() {
    return this.currentDriver == null ? "" : this.currentDriver;
  }

  public void closeCurrentDriver() {
    if (this.getCurrentDriver() != null) {
      this.closeAndQuit(this.getCurrentDriver());
      this.driverMap.remove(this.currentDriver);
      this.currentDriver = null;
      this.clearCurrentActiveDriver();
    }
  }

  public void closeDriver(String driverName) {
    if (driverName == null || !this.driverMap.containsKey(driverName)) {
      return;
    }
    DriverSession<?> closedDriver = this.driverMap.get(driverName);
    this.closeAndQuit(closedDriver);
    this.driverMap.remove(driverName);
    if (driverName.equals(this.currentDriver)) {
      this.currentDriver = null;
      this.clearCurrentActiveDriver();
    }
  }

  private void closeAndQuit(DriverSession<?> driver) {
    try {
      driver.close();
    } catch (Exception ignored) {

    }
  }

  public void clearCurrentActiveDriver() {
    this.currentActiveDriver = null;
  }

  public void setCurrentActiveDriver(DriverSession<?> driver) {
    Optional<Map.Entry<String, DriverSession<?>>> matching = this.driverMap.entrySet()
      .stream()
      .filter(entry -> matchingDriver(entry.getValue(), driver))
      .findAny();
    if (matching.isPresent()) {
      this.currentActiveDriver = matching.get()
        .getValue();
      this.currentDriver = matching.get()
        .getKey();
    }
  }

  public String getDriverName(DriverSession<?> driver) {
    if (driver == null) {
      return null;
    }
    Optional<Map.Entry<String, DriverSession<?>>> matching = this.driverMap.entrySet()
      .stream()
      .filter(entry -> matchingDriver(entry.getValue(), driver))
      .findAny();
    return matching.map(Map.Entry::getKey)
      .orElse(null);
  }

  private boolean matchingDriver(DriverSession<?> mappedDriver, DriverSession<?> driver) {
    return mappedDriver.equals(driver);
  }

  public DriverInstances.InstanceRegistration registerDriver(String driverName) {
    return new DriverInstances.InstanceRegistration(driverName);
  }

  public List<DriverSession<?>> getCurrentDrivers() {
    return this.currentActiveDriver == null ?
      new ArrayList<>(this.driverMap.values()) :
      List.of(this.currentActiveDriver);
  }

  public ConcurrentHashMap<String, DriverSession<?>> getActiveDriverMap() {
    ConcurrentHashMap<String, DriverSession<?>> activeDrivers = new ConcurrentHashMap<>();
    this.driverMap.entrySet()
      .stream()
      .filter((entry) -> entry.getValue()
        .isActive())
      .forEach((entry) -> {
        activeDrivers.put(entry.getKey(), entry.getValue());
      });
    return activeDrivers;
  }

  public void closeAllDrivers() {
    this.driverMap.forEach((key, value) -> this.closeAndQuit(value));

    this.driverMap.clear();
    this.clearDriversInCurrentThread();
    this.currentDriver = null;
    this.clearCurrentActiveDriver();
  }

  private void clearDriversInCurrentThread() {
    (this.driversUsedInCurrentThread.get()).clear();
  }

  public void closeCurrentDrivers() {
    this.closeCurrentDriver();

    this.driversUsedInCurrentThread.get()
      .forEach(driverName -> {
        DriverSession<?> openDriver = this.driverMap.get(driverName);
        if (openDriver.isActive()) {
          this.closeAndQuit(openDriver);
        }
      });

    this.currentDriver = null;
  }

  public final class InstanceRegistration {
    private final String driverName;

    public InstanceRegistration(String driverName) {
      this.driverName = driverName;
    }

    public void forDriver(DriverSession<?> driver) {
      DriverInstances.this.driverMap.put(this.driverName, driver);
    }
  }
}
