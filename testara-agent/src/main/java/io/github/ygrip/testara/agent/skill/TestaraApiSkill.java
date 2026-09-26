package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.safety.OutputValidator;

import java.io.IOException;
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
    boolean write = context.allowsWrite() && "true".equals(context.options().get("write"));

    return switch (mode) {
      case "config"        -> generateApiConfig(input.domain(), context.projectRoot(), write, concise);
      case "request-spec"  -> generateRequestSpec(input.domain(), input.flow(), input.method(), input.endpoint(),
          context.projectRoot(), write, ArtifactFiles.overwrite(context), concise);
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
          - property files: use ${ENV:fallback} values; do not nest properties(...) as property values
          - feature/request values: use properties(key) for application.properties values, or commands like uuid()/random()/oneOf() for dynamic data
          - DataTable for header/form/query param steps: MUST use |key|value| headers (each row = one entry)
          - DataTable for template binding (prepare request data ... from template): horizontal multi-column (column names = JSONPath keys in template)
          """;
    }
    return """
        # Testara API Guide

        ## Service Config
        ```properties
        api.service.{name}.host=${API_SERVICE_HOST:http://localhost:8080}
        api.service.{name}.basePath=${API_SERVICE_BASE_PATH:/api/v1}
        api.service.{name}.default_specification={name}
        spec.api.{name}.header.Content-Type=application/json
        spec.api.{name}.header.Accept=application/json
        ```

        ## When to use request spec vs direct step
        - **Direct step** — simple GET without payload/params:
          `When [api] try GET request to "properties({name}.api.health-endpoint)"`
        - **Request spec** — any request with payload, path params, headers, or reuse:
          `When [api] process request to "files/{domain}/request/{flow}"`

        ## Request Spec JSON structure (`CreateRequestSpecification`)
        ```json
        {
          "specification": "{name}",
          "httpMethod": "POST",
          "url": "properties({name}.api.endpoint)",
          "contentType": "application/json",
          "headers": { "X-Request-Id": "uuid()" },
          "queryParameters": { "include": "details" },
          "pathParameters": { "id": "properties(test.{domain}.id)" },
          "multiPartData": { "file": "properties(test.{domain}.upload-file)" },
          "payload": { "field": "properties(test.{domain}.field)" },
          "autoCloseConnection": true
        }
        ```

        ## DataTable formats for inline API steps
        Steps that add headers, query params, path params, or form params to the current request
        all use TransformerService.toMap() — they REQUIRE the |key|value| header format.
        Each row after the header becomes one map entry.
        ```gherkin
        And [api] prepare headers with data
          | key           | value            |
          | Content-Type  | application/json |
          | X-Request-Id  | uuid()           |

        And [api] prepare queryParams with data
          | key     | value                       |
          | include | details                     |
          | page    | 1                           |

        And [api] prepare formParams with data
          | key    | value                          |
          | userId | properties(test.{domain}.id)   |
        ```
        Single values: `[api] prepare header "X-Request-Id" with value "uuid()"`,
        `[api] prepare queryParam "page" with value "1"`, `[api] prepare pathParam for id with value "properties(test.{domain}.id)"`.

        Template binding (DataManipulationSteps) uses horizontal multi-column — column names are JSONPath keys:
        ```gherkin
        And [api] prepare request data payload from template "{TemplateName}" with value
          | id                           | field                           |
          | properties(test.{domain}.id) | properties(test.{domain}.field) |
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

  /** Runtime service config for {@code using service with alias <domain>-api} (configuration.properties). */
  static String serviceConfigBlock(String domain) {
    String alias = PropertyKeys.apiAlias(domain);
    String env = PropertyKeys.toEnvKey(alias);
    return """
        # API service — %s
        api.service.%s.host=${%s_HOST:http://localhost:8080}
        api.service.%s.basePath=${%s_BASE_PATH:/api/v1}
        api.service.%s.default_specification=%s
        spec.api.%s.header.Content-Type=application/json
        spec.api.%s.header.Accept=application/json
        api.enable-request-log=true
        api.enable-response-log=true
        # Request specs live under src/test/resources/files/...
        %s
        """.formatted(domain, alias, env, alias, env, alias, alias, alias, alias, PropertyKeys.scriptFolderEntry());
  }

  private String generateApiConfig(String domain, Path projectRoot, boolean write, boolean concise) {
    if (domain == null) domain = "sample";
    String d = domain;
    String configBlock = serviceConfigBlock(d);

    String applicationBlock = """
        # Application values referenced by features/request specs
        %s=/%s/{id}
        %s=00000000-0000-0000-0000-000000000001
        %s=sample-value
        %s=details
        """.formatted(PropertyKeys.apiEndpoint(d), d, PropertyKeys.testData(d, "id"),
        PropertyKeys.testData(d, "field"), PropertyKeys.testData(d, "include"));

    if (write) {
      try {
        ArtifactFiles.PropertyMerge config = ArtifactFiles.mergeProperties(projectRoot, ArtifactFiles.CONFIGURATION_PROPERTIES, configBlock);
        ArtifactFiles.PropertyMerge values = ArtifactFiles.mergeProperties(projectRoot, ArtifactFiles.APPLICATION_PROPERTIES, applicationBlock);
        String changes = config.line() + "\n" + values.line();
        String scriptFolderWarning = ArtifactFiles.scriptFolderWarning(projectRoot);
        if (scriptFolderWarning != null) changes += "\n" + scriptFolderWarning;
        return concise ? "api config for '" + d + "':\n" + changes
            : "## API Config Written\n\n- " + changes.replace("\n", "\n- ")
                + "\n\nRuntime config (`" + config.path() + "`):\n```properties\n" + configBlock
                + "```\n\nEnvironment values (`" + values.path() + "`):\n```properties\n" + applicationBlock + "```\n";
      } catch (IOException e) {
        return "Error writing api config for '" + d + "': " + e.getMessage();
      }
    }
    return concise
        ? "add_to_src/test/resources/configuration.properties:\n" + configBlock
            + "\nadd_to_src/test/resources/application.properties:\n" + applicationBlock
        : "## API Config — " + d + "\n\n**Add to `src/test/resources/configuration.properties`:**\n```properties\n"
            + configBlock + "```\n\n**Add to `src/test/resources/application.properties`:**\n```properties\n"
            + applicationBlock + "```\n";
  }

  private String generateRequestSpec(String domain, String flow, String method, String endpoint,
      Path projectRoot, boolean write, boolean overwrite, boolean concise) {
    if (domain == null) domain = "sample";
    if (flow == null) flow = "sample-request";
    if (method == null) method = "POST";
    if (endpoint == null) endpoint = "properties(" + PropertyKeys.apiEndpoint(domain) + ")";
    String d = domain, f = flow, m = method.toUpperCase(Locale.ROOT), e = endpoint;

    boolean payloadMethod = List.of("POST", "PUT", "PATCH").contains(m);
    String spec = payloadMethod ? """
        {
          "specification": "%s",
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
        """.formatted(PropertyKeys.apiAlias(d), m, e, d, d) : """
        {
          "specification": "%s",
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
        """.formatted(PropertyKeys.apiAlias(d), m, e, d, d);

    String path = "src/test/resources/files/" + d + "/request/" + f + ".json";
    var validation = OutputValidator.validateJson(spec);
    if (!validation.valid()) {
      return "Generated request spec is invalid: " + String.join("; ", validation.errors());
    }
    String featureStep = "When [api] process request to \"files/" + d + "/request/" + f + "\"";
    if (write) {
      try {
        ArtifactFiles.Written written = ArtifactFiles.writeJson(projectRoot, path, spec, overwrite);
        String scriptFolderWarning = ArtifactFiles.scriptFolderWarning(projectRoot);
        String warningLine = "";
        if (scriptFolderWarning != null) warningLine = "\n" + scriptFolderWarning;
        if (!written.changed()) {
          return "exists: " + path + " (pass overwrite=true to replace)\nstep: " + featureStep + warningLine;
        }
        return concise
            ? "written: " + path + "\nstep: " + featureStep + warningLine
            : "## Request Spec Written\n\n`" + path + "`\n\n```json\n" + spec + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n" + warningLine;
      } catch (IOException ex) {
        return "Error writing " + path + ": " + ex.getMessage();
      }
    }
    if (concise) return "path: " + path + "\nstep: " + featureStep + "\n" + spec;
    return "## Request Spec — " + f + "\n\n**Path:** `" + path + "`\n\n```json\n" + spec + "```\n\n**Feature step:**\n```gherkin\n" + featureStep + "\n```\n";
  }
}
