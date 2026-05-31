package io.github.ygrip.testara.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Skill: explain and generate Testara API configuration and request specification artifacts.
 *
 * Modes:
 *  explain      — explain API config structure, RequestBuilder, response mapping
 *  config       — generate api.service.* + spec.api.* properties block
 *  request-spec — generate a request specification JSON file
 *  review       — check a feature file for steps that should use request specs
 */
public class TestaraApiSkill implements AgentSkill<TestaraApiSkill.Input, String> {

  public record Input(String mode, String domain, String flow, String method, String endpoint) {}

  @Override
  public String name() { return "testara-api"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String mode = input.mode() != null ? input.mode() : "explain";
    boolean concise = "concise".equals(context.options().get("format"));
    boolean write = "true".equals(context.options().get("write"));

    return switch (mode) {
      case "config"        -> generateApiConfig(input.domain(), context.projectRoot(), write, concise);
      case "request-spec"  -> generateRequestSpec(input.domain(), input.flow(), input.method(), input.endpoint(), context.projectRoot(), write, concise);
      case "explain"       -> explainApi(concise);
      default              -> explainApi(concise);
    };
  }

  private String explainApi(boolean concise) {
    if (concise) {
      return """
          testara-api concepts:
          - api.service.{name}.host/basePath/default_specification — service config
          - spec.api.{name}.header.* — default headers applied to all requests for service
          - request spec JSON: src/test/resources/files/{domain}/request/{flow}.json
          - feature step: [api] process request to "files/{domain}/request/{flow}"
          - direct step: [api] try GET request to "/url" — use only for simple no-payload/no-param requests
          - spec fields map to CreateRequestSpecification: specification, httpMethod, url, contentType, cookies, queryParameters, formParameters, headers, pathParameters, multiPartData, payload, requestLog, responseLog, autoCloseConnection
          - response: [api] response statusCode should be 200 | [api] assign previous response data to alias
          - validations: [api] do these validations with response($['alias']) / request($['alias'])
          - properties() for all URLs, credentials, and test data values
          """;
    }
    return """
        # Testara API Guide

        ## Service Config
        ```properties
        api.service.{name}.host=properties({name}.host)
        api.service.{name}.basePath=properties({name}.basePath)
        api.service.{name}.default_specification={name}
        spec.api.{name}.header.Content-Type=application/json
        spec.api.{name}.header.Accept=application/json
        ```

        ## When to use request spec vs direct step
        - **Direct step** — simple GET without payload/params:
          `When [api] try GET request to "properties({name}.health-endpoint)"`
        - **Request spec** — any request with payload, path params, headers, or reuse:
          `When [api] process request to "files/{domain}/request/{flow}"`

        ## Request Spec JSON structure (`CreateRequestSpecification`)
        ```json
        {
          "specification": "{name}",
          "httpMethod": "POST",
          "url": "properties({name}.endpoint)",
          "contentType": "application/json",
          "headers": { "X-Request-Id": "uuid()" },
          "queryParameters": { "include": "details" },
          "pathParameters": { "id": "properties(test.{domain}.id)" },
          "multiPartData": { "file": "properties(test.{domain}.upload-file)" },
          "payload": { "field": "properties(test.{domain}.field)" },
          "autoCloseConnection": true
        }
        ```

        ## Built-in API flow
        ```gherkin
        Given [api] using service with alias {domain}-api
        And [api] prepare request data payload from template "{TemplateName}" with value
          | id                           | field                           |
          | properties(test.{domain}.id) | properties(test.{domain}.field) |
        And [api] prepare body request with value "request($['payload'])"
        When [api] process request to "files/{domain}/request/{flow}"
        Then [api] response statusCode should be 200
        Then [api] assign previous response data to {domain}Response
        Then [api] do these validations
          | actual                          | validation | expectation |
          | response($['{domain}Response']) | NOT_EMPTY  | true        |
        ```

        ## Response steps
        ```gherkin
        Then [api] response statusCode should be 200
        Then [api] response success should be true
        Then [api] assign previous response data to {domain}Response
        Then [api] assign previous response headers to {domain}Headers
        ```
        """;
  }

  private String generateApiConfig(String domain, Path projectRoot, boolean write, boolean concise) {
    if (domain == null) domain = "sample";
    String d = domain;
    String configBlock = """
        # API service — %s
        api.service.%s-api.host=properties(%s.api.host)
        api.service.%s-api.basePath=properties(%s.api.basePath)
        api.service.%s-api.default_specification=%s-api
        spec.api.%s-api.header.Content-Type=application/json
        spec.api.%s-api.header.Accept=application/json
        api.enable-request-log=true
        api.enable-response-log=true
        """.formatted(d, d, d, d, d, d, d, d, d);

    String applicationBlock = """
        # Environment values
        %s.api.host=http://localhost:8080
        %s.api.basePath=/api/v1
        %s.api.endpoint=/sample/{id}
        test.%s.id=00000000-0000-0000-0000-000000000001
        test.%s.field=sample-value
        test.%s.include=details
        """.formatted(d, d, d, d, d, d);

    String block = configBlock + "\n" + applicationBlock;

    if (write) {
      appendToProperties(projectRoot, configBlock,
          List.of("src/test/resources/configuration.properties", "configuration.properties"));
      appendToProperties(projectRoot, applicationBlock,
          List.of("src/test/resources/application.properties", "application.properties"));
      return concise ? "appended api config for '" + d + "' to configuration.properties and application.properties"
          : "## API Config Written\n\nAppended runtime config to `configuration.properties`:\n```properties\n"
              + configBlock + "```\n\nAppended environment values to `application.properties`:\n```properties\n"
              + applicationBlock + "```\n";
    }
    return concise ? block : "## API Config — " + d + "\n\n```properties\n" + block + "```\n";
  }

  private String generateRequestSpec(String domain, String flow, String method, String endpoint,
      Path projectRoot, boolean write, boolean concise) {
    if (domain == null) domain = "sample";
    if (flow == null) flow = "sample-request";
    if (method == null) method = "POST";
    if (endpoint == null) endpoint = "properties(" + domain + ".api.endpoint)";
    String d = domain, f = flow, m = method.toUpperCase(Locale.ROOT), e = endpoint;

    boolean payloadMethod = List.of("POST", "PUT", "PATCH").contains(m);
    String spec = payloadMethod ? """
        {
          "specification": "%s-api",
          "httpMethod": "%s",
          "url": "%s",
          "contentType": "application/json",
          "headers": {
            "X-Request-Id": "uuid()"
          },
          "pathParameters": {
            "id": "properties(test.%s.id)"
          },
          "payload": {
            "field": "properties(test.%s.field)"
          },
          "autoCloseConnection": true
        }
        """.formatted(d, m, e, d, d) : """
        {
          "specification": "%s-api",
          "httpMethod": "%s",
          "url": "%s",
          "contentType": "application/json",
          "pathParameters": {
            "id": "properties(test.%s.id)"
          },
          "queryParameters": {
            "include": "properties(test.%s.include)"
          },
          "autoCloseConnection": true
        }
        """.formatted(d, m, e, d, d);

    String path = "src/test/resources/files/" + d + "/request/" + f + ".json";
    if (write) {
      try {
        Path target = projectRoot.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, spec, StandardCharsets.UTF_8);
        String featureStep = "When [api] process request to \"files/" + d + "/request/" + f + "\"";
        return concise
            ? "written: " + path + "\nstep: " + featureStep
            : "## Request Spec Written\n\n`" + path + "`\n\n```json\n" + spec + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n";
      } catch (IOException ex) {
        return "Error writing " + path + ": " + ex.getMessage();
      }
    }
    String featureStep = "When [api] process request to \"files/" + d + "/request/" + f + "\"";
    if (concise) return "path: " + path + "\nstep: " + featureStep + "\n" + spec;
    return "## Request Spec — " + f + "\n\n**Path:** `" + path + "`\n\n```json\n" + spec + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n";
  }

  private void appendToProperties(Path root, String block, List<String> candidates) {
    for (String c : candidates) {
      Path p = root.resolve(c);
      if (Files.exists(p)) {
        try { Files.writeString(p, Files.readString(p, StandardCharsets.UTF_8) + "\n" + block, StandardCharsets.UTF_8); }
        catch (IOException ignored) {}
        return;
      }
    }
  }
}
