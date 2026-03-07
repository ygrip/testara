package io.github.ygrip.testara.ui.driver;

public final class DriverSessionManager {
  private static final ThreadLocal<DriverInstances> DRIVER_INSTANCES_THREAD_LOCAL = new ThreadLocal<>();

  private DriverSessionManager(){

  }

  public static DriverInstances inThisTestThread() {
    if (DRIVER_INSTANCES_THREAD_LOCAL.get() == null) {
      DRIVER_INSTANCES_THREAD_LOCAL.set(new DriverInstances());
    }

    return DRIVER_INSTANCES_THREAD_LOCAL.get();
  }

  public static void tearDown() {
    inThisTestThread().clearCurrentActiveDriver();
    inThisTestThread().closeAllDrivers();
    DRIVER_INSTANCES_THREAD_LOCAL.remove();
  }
}
