package io.github.ygrip.testara.validation.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ValidatorInfo {
  private final List<String> aliases;
  private final String name;

  private final boolean overwrite;

  public ValidatorInfo(Class<?> validatorClass) {
    List<String> aliases = new ArrayList<>();
    String name = null;
    ValidationTag tag = validatorClass.getAnnotation(ValidationTag.class);
    if (tag != null) {
      String[] alias = tag.alias();
      name = tag.command().trim().toLowerCase();
      if (alias != null && alias.length > 0) {
        aliases = Arrays.stream(alias).map(item -> item.toLowerCase().trim()).distinct().collect(Collectors.toList());
      }
      this.overwrite = tag.overwrite();
    } else {
      this.overwrite = false;
    }
    this.name = name;
    this.aliases = aliases;
  }

  public String name() {
    return this.name;
  }

  public List<String> aliases() {
    return this.aliases;
  }

  public boolean overwrite() {
    return this.overwrite;
  }
}
