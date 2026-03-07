package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@CommandTag(command = "increment", overwrite = true)
public class IncrementCommand implements CommandLogic<Integer> {
  private final ThreadLocal<AtomicInteger> increment = ThreadLocal.withInitial(() -> new AtomicInteger(0));

  @Override
  public boolean preProcessParameters() {
    return false;
  }

  @Override
  public Integer execute(List<Object> parameters) throws Exception {
    return increment.get().incrementAndGet();
  }
}
