package io.github.ygrip.testara.engine.support;

import io.github.ygrip.testara.engine.descriptor.TestaraNodeDescriptor;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestTag;

/**
 * Utility class for extracting order information from TestDescriptor instances.
 * This provides a centralized way to handle @Order tag parsing across the system.
 */
public final class TestDescriptorOrderUtils {

  private TestDescriptorOrderUtils() {

  }

  /**
   * Extract the order value from @Order=N tags on a TestDescriptor
   *
   * @param descriptor The test descriptor to examine
   * @return The order value if found, Integer.MAX_VALUE otherwise
   */
  public static Integer getOrder(TestDescriptor descriptor) {
    if (descriptor == null || descriptor.getTags() == null) {
      return Integer.MAX_VALUE;
    }

    return descriptor.getTags().stream().map(TestTag::getName).filter(tag -> {
      // Case insensitive pattern matching for @Order=d+ format
      // where d+ is a non-negative integer
      String lowerTag = tag.toLowerCase();
      return lowerTag.startsWith("order=");
    }).findFirst().map(tag -> {
      String lowerTag = tag.toLowerCase();
      String value = lowerTag.substring(6); // Remove "order="
      try {
        int orderValue = Integer.parseInt(value);
        return orderValue >= 0 ? orderValue : Integer.MAX_VALUE;
      } catch (NumberFormatException e) {
        return Integer.MAX_VALUE; // Not a valid integer
      }
    }).orElse(Integer.MAX_VALUE);
  }


  /**
   * Get the count of exclusive resources for a test descriptor
   * For individual tests, return the direct count
   * For containers (features), return the sum of all child exclusive resources
   */
  public static Integer getExclusiveResourceCount(TestDescriptor descriptor) {
    if (descriptor.isTest()) {
      // For individual tests (scenarios), get direct exclusive resource count
      if (descriptor instanceof TestaraNodeDescriptor) {
        return ((TestaraNodeDescriptor) descriptor).getExclusiveResources().size();
      }
      return 0;
    } else if (descriptor.getChildren().isEmpty()) {
      // Empty container has no exclusive resources
      return 0;
    } else {
      // For containers (features), sum up all child exclusive resources
      return descriptor.getChildren()
          .stream()
          .map(TestDescriptorOrderUtils::getExclusiveResourceCount)
          .mapToInt(Integer::intValue)
          .sum();
    }
  }
} 