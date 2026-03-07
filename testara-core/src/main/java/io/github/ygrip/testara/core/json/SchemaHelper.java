package io.github.ygrip.testara.core.json;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultProperties;
import com.google.gson.Gson;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.serialization.JsonMapperFactory;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static io.github.ygrip.testara.core.file.FileHelper.readFile;

/**
 * <p>SchemaValidatorImpl class.</p>
 *
 * @author yunaz.ramadhan on 12/8/2019
 * @version $Id: $Id
 */
@Log4j2
public final class SchemaHelper {
  private static final String DEFAULT_SCHEMA_FOLDER = loadSchemaFolder();
  private static final String DEFAULT_FORMAT = "json";
  private static final Gson gson = initGson();
  private static final JsonSchemaFactory factory = initJsonSchemaFactory();

  private static String loadSchemaFolder() {
    DefaultProperties properties = TestFramework.context().get(DefaultProperties.class);
    String schemaFolder = properties.getSchemaFolder();
    return String.format("%s%s",
        System.getProperty("user.dir"),
        StringUtils.isBlank(schemaFolder) ? "/src/test/resources/" : schemaFolder);
  }

  private static JsonSchemaFactory initJsonSchemaFactory() {
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  }

  private static Gson initGson() {
    return new Gson();
  }

  /**
   * {@inheritDoc}
   */
  public static Validator loadSchema(String schemaName) throws Exception {
    log.debug("#Load schema from {}", schemaName);
    String schemaPath = String.format("%s%s.%s", DEFAULT_SCHEMA_FOLDER, schemaName, DEFAULT_FORMAT);
    String schemaAsString = readFile(schemaPath);
    if (StringUtils.isBlank(schemaAsString)) {
      throw new Exception(String.format("Cannot find schema %s", schemaPath));
    }
    JsonNode schemaNode = JsonMapperFactory.getInstance().readTree(schemaAsString);
    JsonSchema schema = factory.getSchema(schemaNode);
    return new Validator(schema);
  }

  public static class Validator {
    private final JsonSchema schema;

    Validator(JsonSchema schema) {
      this.schema = schema;
    }

    /**
     * {@inheritDoc}
     */
    public void validate(Object obj) throws Exception {
      if (ObjectUtils.isEmpty(this.schema)) {
        throw new Exception("No json schema provided");
      }
      this.schema.initializeValidators();
      JsonNode node = JsonMapperFactory.getInstance().readTree(SchemaHelper.gson.toJson(obj));
      Set<ValidationMessage> errors = this.schema.validate(node);
      if (errors != null && !errors.isEmpty()) {
        throw new Exception("Object does not match with json schema, errors " + errors.stream()
            .map(ValidationMessage::toString)
            .collect(Collectors.joining("\n")));
      }
    }
  }
}
