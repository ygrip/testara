package io.github.ygrip.testara.core.function;

import lombok.extern.log4j.Log4j2;

import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;

@Log4j2
public class MethodInvocation {
  private final MethodHandle method;
  private final WeakReference<Object> targetRef;
  private final Object[] args;
  private final String description;
  private final String classRefernce;

  public MethodInvocation(Object instance, MethodHandle method, Object[] args, String description) {
    this.method = method;
    if (instance == null) {
      throw new IllegalStateException("Cannot store null instance!");
    }
    this.classRefernce = instance.getClass().getSimpleName();
    this.targetRef = new WeakReference<>(instance);
    this.args = args != null ? args.clone() : new Object[0];
    this.description = description;
  }

  public Object invoke() throws Throwable {
    Object target = targetRef != null ? targetRef.get() : null;
    if (target == null && targetRef != null) {
      throw new IllegalStateException("Target instance on " + getClassReference() + " was GC'd");
    }
    log.trace("Invoking : {} on class {}", getMethodDescription(), getClassReference());

    // Since the interceptor now bypasses during execution mode, we can safely use MethodHandle
    // The interceptor will detect execution mode and call superCall.call() directly
    return method.bindTo(target).invokeWithArguments(args);
  }

  public String getClassReference() {
    return this.classRefernce;
  }

  public String getMethodDescription() {
    return this.description;
  }
}
