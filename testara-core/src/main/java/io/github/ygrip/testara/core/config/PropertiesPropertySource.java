package io.github.ygrip.testara.core.config;

import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Property source implementation that mimics Spring's property resolution behavior.
 * <p>
 * Supports:
 * <ul>
 *   <li>{@code application.properties} - No prefix defaults to classpath:</li>
 *   <li>{@code classpath:path/to/file.properties} - Load single resource from classpath</li>
 *   <li>{@code classpath*:path/to/file.properties} - Load from all modules (outer wins)</li>
 *   <li>{@code classpath*:path/to/*.properties} - Load with wildcard pattern from all modules</li>
 *   <li>{@code file:/path/to/file.properties} - Load from file system</li>
 *   <li>{@code file:/path/to/*.properties} - Load with wildcard from file system</li>
 * </ul>
 * <p>
 * Module inheritance: When using {@code classpath*:}, resources are loaded from inner modules
 * (dependencies) first, then outer modules (client). This ensures outer module properties
 * override inner module properties (outer wins).
 */
public final class PropertiesPropertySource implements PropertySource {

  public PropertiesPropertySource() {
  }

  /**
   * Load properties from a location pattern.
   * <p>
   * Supported formats:
   * <ul>
   *   <li>{@code classpath:path} - Load single resource from classpath</li>
   *   <li>{@code classpath*:path} - Load from all modules (outer wins)</li>
   *   <li>{@code file:path} - Load from file system</li>
   *   <li>{@code path} - No prefix defaults to classpath:</li>
   * </ul>
   *
   * @param location the location pattern
   * @return list of Properties objects ordered for proper override (inner first, outer last)
   */
  public List<Properties> load(String location) {
    try {
      if (location.startsWith("classpath*:")) {
        return loadClasspath(location.substring("classpath*:".length()), true);
      }

      if (location.startsWith("classpath:")) {
        return loadClasspath(location.substring("classpath:".length()), false);
      }

      if (location.startsWith("file:")) {
        return loadFileSystem(location.substring("file:".length()));
      }

      // No prefix - default to classpath: behavior
      return loadClasspath(location, false);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load properties from " + location, e);
    }
  }

  /**
   * Load from classpath. When multiple=true, loads from all modules with outer winning.
   * <p>
   * ClassLoader.getResources() returns URLs in classpath order:
   * - Current module (outer) comes first
   * - Dependencies (inner) come after
   * <p>
   * To achieve "outer wins", we reverse this order so inner modules are loaded first
   * and outer modules are loaded last (overriding inner).
   */
  private List<Properties> loadClasspath(String path, boolean multiple) throws Exception {
    String normalized = normalizeClasspathPath(path);
    ClassLoader cl = getClassLoader();

    boolean hasWildcard = containsWildcard(normalized);

    if (hasWildcard) {
      return loadClasspathWithWildcard(normalized, multiple, cl);
    }

    // Direct resource loading (no wildcards)
    List<URL> urls = collectUrls(cl, normalized, multiple);

    // Reverse order: ClassLoader returns outer first, we want outer last (to win)
    Collections.reverse(urls);

    return loadFromUrls(urls);
  }

  /**
   * Load classpath resources matching a wildcard pattern.
   */
  private List<Properties> loadClasspathWithWildcard(String pattern, boolean multiple, ClassLoader cl)
      throws Exception {
    // Split pattern into directory and file pattern
    int lastSlash = pattern.lastIndexOf('/');
    String directory = lastSlash >= 0 ? pattern.substring(0, lastSlash) : "";
    String filePattern = lastSlash >= 0 ? pattern.substring(lastSlash + 1) : pattern;

    // Get all directory URLs
    List<URL> dirUrls = collectUrls(cl, directory.isEmpty() ? "" : directory, multiple);

    // Reverse order: inner modules first, outer modules last (outer wins)
    Collections.reverse(dirUrls);

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
    List<Properties> result = new ArrayList<>();
    Set<String> loadedPaths = new LinkedHashSet<>(); // Track loaded paths to avoid duplicates

    for (URL dirUrl : dirUrls) {
      if (!"file".equals(dirUrl.getProtocol())) {
        // Only support file protocol for directory scanning
        // This covers target/classes directories in Maven projects
        continue;
      }

      Path dirPath = Path.of(dirUrl.toURI());
      if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
        continue;
      }

      // Scan directory for matching files
      List<Path> matchedFiles = new ArrayList<>();
      Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          Path fileName = file.getFileName();
          if (fileName != null && matcher.matches(fileName)) {
            matchedFiles.add(file);
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          // Only scan the immediate directory, not subdirectories
          // unless we're at the root
          if (dir.equals(dirPath)) {
            return FileVisitResult.CONTINUE;
          }
          return FileVisitResult.SKIP_SUBTREE;
        }
      });

      // Sort files by name for consistent ordering within same module
      matchedFiles.sort((a, b) -> a.getFileName().compareTo(b.getFileName()));

      for (Path file : matchedFiles) {
        String relativePath = dirPath.relativize(file).toString();
        String fullPath = directory.isEmpty() ? relativePath : directory + "/" + relativePath;

        // Only load if we haven't loaded this path from another module
        // (first module to provide a path wins, which is the inner module)
        // But since we reversed order, outer module actually wins for same path
        if (!loadedPaths.contains(fullPath)) {
          loadedPaths.add(fullPath);
          result.add(loadFromPath(file));
        }
      }
    }

    return result;
  }

  /**
   * Load properties from file system, with optional wildcard support.
   */
  private List<Properties> loadFileSystem(String path) throws Exception {
    if (containsWildcard(path)) {
      return loadFileSystemWithWildcard(path);
    }

    // Direct file loading
    Path filePath = Path.of(path);
    if (!Files.exists(filePath)) {
      return Collections.emptyList();
    }
    return List.of(loadFromPath(filePath));
  }

  /**
   * Load files matching a wildcard pattern from file system.
   */
  private List<Properties> loadFileSystemWithWildcard(String pattern) throws Exception {
    Path patternPath = Path.of(pattern);
    Path basePath = patternPath.getParent();
    String filePattern = patternPath.getFileName().toString();

    if (basePath == null || !Files.exists(basePath)) {
      return Collections.emptyList();
    }

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
    List<Properties> result = new ArrayList<>();

    Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        Path fileName = file.getFileName();
        if (fileName != null && matcher.matches(fileName)) {
          try {
            result.add(loadFromPath(file));
          } catch (Exception ignored) {
            // Skip files that can't be loaded
          }
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        // Only scan immediate directory
        return dir.equals(basePath) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }
    });

    return result;
  }

  /**
   * Collect URLs from ClassLoader hierarchy.
   * <p>
   * When multiple=true, collects from all ClassLoaders in the hierarchy to ensure
   * we get resources from both outer modules (client) and inner modules (dependencies).
   */
  private List<URL> collectUrls(ClassLoader cl, String path, boolean multiple) throws Exception {
    Set<URL> urlSet = new LinkedHashSet<>(); // Use Set to avoid duplicates

    if (multiple) {
      // Collect from main ClassLoader
      collectUrlsFromClassLoader(cl, path, urlSet);

      // Also try System ClassLoader if different (may have different resources)
      ClassLoader systemCl = ClassLoader.getSystemClassLoader();
      if (systemCl != null && systemCl != cl) {
        collectUrlsFromClassLoader(systemCl, path, urlSet);
      }

      // Also try this class's ClassLoader (testara-core's resources)
      ClassLoader thisCl = PropertiesPropertySource.class.getClassLoader();
      if (thisCl != null && thisCl != cl && thisCl != systemCl) {
        collectUrlsFromClassLoader(thisCl, path, urlSet);
      }
    } else {
      // Single resource - just get first match
      URL url = cl.getResource(path);
      if (url != null) {
        urlSet.add(url);
      }
    }

    return new ArrayList<>(urlSet);
  }

  /**
   * Collect all URLs for a path from a specific ClassLoader.
   */
  private void collectUrlsFromClassLoader(ClassLoader cl, String path, Set<URL> urls) throws Exception {
    Enumeration<URL> enumUrls = cl.getResources(path);
    while (enumUrls.hasMoreElements()) {
      URL url = enumUrls.nextElement();
      if (url != null) {
        urls.add(url);
      }
    }
  }

  /**
   * Load Properties from a list of URLs.
   */
  private List<Properties> loadFromUrls(List<URL> urls) {
    List<Properties> result = new ArrayList<>();
    for (URL url : urls) {
      try {
        result.add(loadFromUrl(url));
      } catch (Exception ignored) {
        // Skip URLs that can't be loaded
      }
    }
    return result;
  }

  /**
   * Load Properties from a URL.
   */
  private Properties loadFromUrl(URL url) throws Exception {
    Properties props = new Properties();
    try (InputStream in = url.openStream()) {
      props.load(in);
    }
    return props;
  }

  /**
   * Load Properties from a file path.
   */
  private Properties loadFromPath(Path path) throws Exception {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      props.load(in);
    }
    return props;
  }

  /**
   * Get the appropriate ClassLoader that can see all modules (outer to inner).
   * <p>
   * Strategy:
   * 1. Try to get the caller's ClassLoader using StackWalker (sees outer module)
   * 2. Fall back to Thread's context ClassLoader
   * 3. Fall back to System ClassLoader (full application classpath)
   * 4. Last resort: this class's ClassLoader
   */
  private ClassLoader getClassLoader() {


    // 2. Try Thread's context ClassLoader
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null) {
      return contextClassLoader;
    }

    // 3. Try System ClassLoader (has full application classpath)
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    if (systemClassLoader != null) {
      return systemClassLoader;
    }

    // 4. Last resort: this class's ClassLoader
    return PropertiesPropertySource.class.getClassLoader();
  }

  /**
   * Find the ClassLoader of the external caller (outside this package).
   * This allows outer modules to have their resources discovered.
   */
  private ClassLoader findCallerClassLoader() {
    String thisPackage = PropertiesPropertySource.class.getPackageName();

    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
            .filter(clazz -> !clazz.getPackageName().startsWith(thisPackage))
            .filter(clazz -> !clazz.getName().startsWith("java."))
            .filter(clazz -> !clazz.getName().startsWith("jdk."))
            .findFirst()
            .map(Class::getClassLoader)
            .orElse(null));
  }

  /**
   * Normalize classpath path (remove leading slash if present).
   */
  private String normalizeClasspathPath(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }

  /**
   * Check if path contains wildcard characters.
   */
  private boolean containsWildcard(String path) {
    return path.contains("*") || path.contains("?");
  }

  @Override
  public Map<String, String> load(Map<String, String> properties) {
    Map<String, String> propertyFilesProperties = new HashMap<>();

    String propertyLocation = System.getProperty("configuration.location");
    if (StringUtils.isBlank(propertyLocation)) {
      propertyLocation = "classpath:*.properties";
    }

    // Preserve order of locations
    Set<String> locations = new LinkedHashSet<>(Arrays.asList(propertyLocation.split(",")));

    // Load property files in order
    // Later properties override earlier ones (last wins)
    for (String location : locations) {
      try {
        List<Properties> loadedProperties = load(location.trim());
        for (Properties props : loadedProperties) {
          props.forEach((k, v) -> propertyFilesProperties.put(String.valueOf(k), String.valueOf(v)));
        }
      } catch (Exception e) {
        // Log and continue with other locations
        System.err.println("Warning: Failed to load properties from " + location + ": " + e.getMessage());
      }
    }

    return combine(propertyFilesProperties, properties);
  }
}
