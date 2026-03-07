package io.github.ygrip.testara.core.model;

import lombok.Getter;

import java.util.concurrent.TimeUnit;

@Getter
public class ValueUnit {
  private final long value;
  private final TimeUnit unit;

  public ValueUnit(long value, TimeUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  @Override
  public String toString() {
    return value + " " + unit;
  }
}
