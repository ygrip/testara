package io.github.ygrip.testara.agent.skill;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Resolves the Testara framework version generated projects should use.
 */
final class TestaraVersionResolver {

  private static final String VERSION_PROPERTY = "testara.agent.version";
  private static final String DEFAULT_VERSION = "2.0.4";
  private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+(?:\\.\\d+)+(?:[-.][A-Za-z0-9]+)*");

  String resolve(Path projectRoot) {
    return explicitVersion()
        .or(this::packagedAgentVersion)
        .or(() -> checkoutVersion(projectRoot))
        .or(this::localMavenVersion)
        .orElse(DEFAULT_VERSION);
  }

  private Optional<String> explicitVersion() {
    return valid(System.getProperty(VERSION_PROPERTY));
  }

  private Optional<String> packagedAgentVersion() {
    Package pkg = TestaraVersionResolver.class.getPackage();
    Optional<String> implementationVersion = pkg == null ? Optional.empty() : valid(pkg.getImplementationVersion());
    if (implementationVersion.isPresent()) {
      return implementationVersion;
    }

    return List.of(
            "/META-INF/maven/io.github.ygrip/testara-agent/pom.properties",
            "/META-INF/maven/io.github.ygrip/testara-agent-cli/pom.properties",
            "/META-INF/maven/io.github.ygrip/testara-agent-mcp/pom.properties")
        .stream()
        .map(this::readPomPropertiesVersion)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private Optional<String> readPomPropertiesVersion(String resource) {
    try (InputStream is = TestaraVersionResolver.class.getResourceAsStream(resource)) {
      if (is == null) {
        return Optional.empty();
      }
      Properties properties = new Properties();
      properties.load(is);
      return valid(properties.getProperty("version"));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Optional<String> checkoutVersion(Path projectRoot) {
    return candidateRoots(projectRoot).stream()
        .map(this::findTestaraRoot)
        .flatMap(Optional::stream)
        .distinct()
        .map(root -> readProjectVersion(root.resolve("pom.xml")))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private List<Path> candidateRoots(Path projectRoot) {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path root = projectRoot == null ? cwd : projectRoot.toAbsolutePath().normalize();
    return List.of(root, cwd);
  }

  private Optional<Path> findTestaraRoot(Path start) {
    Path current = start;
    while (current != null) {
      Path pom = current.resolve("pom.xml");
      if (Files.exists(pom) && isTestaraParentPom(pom)) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  private boolean isTestaraParentPom(Path pom) {
    try {
      Document document = parsePom(pom);
      Element project = document.getDocumentElement();
      return "io.github.ygrip".equals(childText(project, "groupId").orElse(null))
          && "testara-core-parent".equals(childText(project, "artifactId").orElse(null));
    } catch (Exception ignored) {
      return false;
    }
  }

  private Optional<String> readProjectVersion(Path pom) {
    try {
      Document document = parsePom(pom);
      Element project = document.getDocumentElement();
      return valid(childText(project, "version").orElse(null));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Document parsePom(Path pom) throws Exception {
    String xml = Files.readString(pom, StandardCharsets.UTF_8);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private Optional<String> childText(Element parent, String name) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element element && name.equals(element.getTagName())) {
        return Optional.ofNullable(element.getTextContent()).map(String::trim);
      }
    }
    return Optional.empty();
  }

  private Optional<String> localMavenVersion() {
    Path repo = Paths.get(System.getProperty("user.home"), ".m2", "repository", "io", "github", "ygrip",
        "testara-core-parent");
    if (!Files.isDirectory(repo)) {
      return Optional.empty();
    }
    try (var versions = Files.list(repo)) {
      return versions
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .filter(version -> RELEASE_VERSION.matcher(version).matches())
          .max(TestaraVersionResolver::compareVersions);
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Optional<String> valid(String version) {
    if (version == null || version.isBlank() || "unknown".equalsIgnoreCase(version)) {
      return Optional.empty();
    }
    return Optional.of(version.trim());
  }

  private static int compareVersions(String left, String right) {
    List<Integer> leftParts = versionKey(left);
    List<Integer> rightParts = versionKey(right);
    int max = Math.max(leftParts.size(), rightParts.size());
    for (int i = 0; i < max; i++) {
      int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
      int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
      int compared = Integer.compare(leftPart, rightPart);
      if (compared != 0) {
        return compared;
      }
    }
    return left.compareTo(right);
  }

  private static List<Integer> versionKey(String version) {
    return java.util.Arrays.stream(version.split("[.-]"))
        .map(TestaraVersionResolver::numericPart)
        .toList();
  }

  private static int numericPart(String part) {
    try {
      return Integer.parseInt(part);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }
}
