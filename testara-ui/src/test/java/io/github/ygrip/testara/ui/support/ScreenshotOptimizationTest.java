package io.github.ygrip.testara.ui.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.ui.config.EngineProperties;

class ScreenshotOptimizationTest {

  @Test
  void exposesScreenshotQualityPresetsWithStandardAsDefault() throws Exception {
    Class<?> qualityType = qualityType();

    assertArrayEquals(
      new String[] {"HIGH", "STANDARD", "LOW"},
      Arrays.stream(qualityType.getEnumConstants()).map(String::valueOf).toArray(String[]::new)
    );

    Method getter = requiredMethod(EngineProperties.class, "getScreenshotQuality");
    assertEquals("STANDARD", String.valueOf(getter.invoke(new EngineProperties())));
  }

  @Test
  void usesRequestedCompressionLevelsForStandardAndLow() throws Exception {
    Class<?> qualityType = qualityType();
    Method jpegQuality = requiredMethod(qualityType, "jpegQuality");

    assertEquals(0.90f, ((Number) jpegQuality.invoke(enumValue(qualityType, "HIGH"))).floatValue());
    assertEquals(0.60f, ((Number) jpegQuality.invoke(enumValue(qualityType, "STANDARD"))).floatValue());
    assertEquals(0.40f, ((Number) jpegQuality.invoke(enumValue(qualityType, "LOW"))).floatValue());
  }

  @Test
  void usesSmallerResolutionCapsForEveryQualityPreset() throws Exception {
    Class<?> qualityType = qualityType();
    Method maxLongEdge = requiredMethod(qualityType, "maxLongEdge");

    assertEquals(1920, ((Number) maxLongEdge.invoke(enumValue(qualityType, "HIGH"))).intValue());
    assertEquals(1280, ((Number) maxLongEdge.invoke(enumValue(qualityType, "STANDARD"))).intValue());
    assertEquals(960, ((Number) maxLongEdge.invoke(enumValue(qualityType, "LOW"))).intValue());
  }

  @Test
  void exposesQualityAwareViewportCaptureWithoutBreakingRawPngCapture() throws Exception {
    Class<?> screenshotCapture = Class.forName(
      "io.github.ygrip.testara.ui.capability.ObservationCapability$ScreenshotCapture"
    );
    Method optimizedCapture = requiredMethod(screenshotCapture, "visibleOnViewPort", qualityType());

    assertEquals(
      "io.github.ygrip.testara.ui.model.CapturedScreenshot",
      optimizedCapture.getReturnType().getName()
    );
    assertEquals(byte[].class, requiredMethod(screenshotCapture, "visibleOnViewPort").getReturnType());
  }

  @Test
  void compressesLargeScreenshotsAndKeepsPresetOrderingMeaningful() throws Exception {
    byte[] original = randomPng(1600, 1000, 42L);
    Class<?> qualityType = qualityType();
    Method optimize = requiredMethod(Screenshots.class, "optimize", byte[].class, qualityType);

    Object high = optimize.invoke(null, original, enumValue(qualityType, "HIGH"));
    Object standard = optimize.invoke(null, original, enumValue(qualityType, "STANDARD"));
    Object low = optimize.invoke(null, original, enumValue(qualityType, "LOW"));

    byte[] highBytes = bytes(high);
    byte[] standardBytes = bytes(standard);
    byte[] lowBytes = bytes(low);

    assertEquals("image/jpeg", mimeType(high));
    assertEquals("image/jpeg", mimeType(standard));
    assertEquals("image/jpeg", mimeType(low));
    assertTrue(highBytes.length < original.length);
    assertTrue(standardBytes.length < highBytes.length);
    assertTrue(lowBytes.length < standardBytes.length);

    BufferedImage standardImage = ImageIO.read(new java.io.ByteArrayInputStream(standardBytes));
    assertEquals(1280, standardImage.getWidth());
    assertEquals(800, standardImage.getHeight());
  }

  @Test
  void skipsSmallScreenshotsToAvoidUnnecessaryCpuWork() throws Exception {
    byte[] original = randomPng(80, 60, 7L);
    Class<?> qualityType = qualityType();
    Method optimize = requiredMethod(Screenshots.class, "optimize", byte[].class, qualityType);

    Object result = optimize.invoke(null, original, enumValue(qualityType, "STANDARD"));

    assertEquals("image/png", mimeType(result));
    assertArrayEquals(original, bytes(result));
  }

  private static Class<?> qualityType() {
    try {
      return Class.forName("io.github.ygrip.testara.ui.model.ScreenshotQuality");
    } catch (ClassNotFoundException e) {
      throw new AssertionError("ScreenshotQuality enum is missing", e);
    }
  }

  private static Method requiredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
    try {
      return type.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(type.getSimpleName() + "." + name + " is missing", e);
    }
  }

  private static Object enumValue(Class<?> type, String name) {
    return Arrays.stream(type.getEnumConstants())
      .filter(value -> name.equals(String.valueOf(value)))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Missing screenshot quality preset " + name));
  }

  private static byte[] bytes(Object result) throws Exception {
    return (byte[]) requiredMethod(result.getClass(), "bytes").invoke(result);
  }

  private static String mimeType(Object result) throws Exception {
    return (String) requiredMethod(result.getClass(), "mimeType").invoke(result);
  }

  private static byte[] randomPng(int width, int height, long seed) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(seed);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
