package io.github.ygrip.testara.core;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;
import java.util.List;

public class CustomTestNameGenerator extends DisplayNameGenerator.Standard {

  private static String replaceCapitals(String name) {
    // Simple regex to insert a space before every uppercase letter
    return name.replaceAll("([A-Z])", " $1").toLowerCase();
  }

  private static String replaceUnderscores(String name) {
    return name.replace('_', ' ');
  }

  private static String prettifyName(String name) {
    name = replaceUnderscores(replaceCapitals(name)).trim();
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  @Override
  public String generateDisplayNameForClass(Class<?> testClass) {
    String name = testClass.getSimpleName();
    return prettifyName(name);
  }

  @Override
  public String generateDisplayNameForNestedClass(List<Class<?>> enclosingInstanceTypes, Class<?> nestedClass) {
    String fullName = nestedClass.getName();
    String[] names = fullName.split("\\.");
    return prettifyName(names[names.length - 2]) + " " + prettifyName(names[names.length - 1]);
  }

  @Override
  public String generateDisplayNameForMethod(List<Class<?>> enclosingInstanceTypes,
      Class<?> testClass,
      Method testMethod) {
    String name = testMethod.getName();
    return prettifyName(name);
  }
}
