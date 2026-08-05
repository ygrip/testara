package io.github.ygrip.testara.reporter.parser;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.Element;
import io.github.ygrip.testara.reporter.cucumber.Feature;

public class CucumberReportParser {
  private static final Logger LOG = Logger.getLogger(CucumberReportParser.class.getName());
  private static final ObjectMapper MAPPER = initializeMapper();

  public static ObjectMapper getMapper(){
    return MAPPER;
  }

  private static ObjectMapper initializeMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
    mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    mapper.registerModule(new JavaTimeModule());
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  public static List<Feature> parseJsonFiles(List<String> jsonFiles) throws Exception {
    return parseJsonFiles(jsonFiles, true);
  }

  public static List<Feature> parseJsonFiles(List<String> jsonFiles, boolean allowEmptyFeatures) throws Exception {
    if (jsonFiles.isEmpty()) {
      throw new Exception("No report file was added!");
    } else {
      List<Feature> featureResults = new ArrayList<>();

      for (String jsonFile : jsonFiles) {
        File file = FileHelper.openFile(jsonFile);
        if (file.length() != 0L) {
          List<Feature> features = new ArrayList<>();
          try {
            features = parseForFeature(file);
          } catch (IOException exception) {
            if (!allowEmptyFeatures) {
              throw exception;
            }
          }
          LOG.log(Level.INFO, String.format("File '%s' contains %d feature(s)", jsonFile, features.size()));
          featureResults.addAll(features);
        }
      }

      //make sure no duplicate scenarios inside a feature file
      featureResults.forEach(feature -> {
        List<Element> distinct = feature.getElements()
            .stream()
            .collect(groupingBy(Element::getId,
                collectingAndThen(maxBy(Comparator.comparing(Element::getStartTime)),
                    employee -> employee.orElse(null))))
            .values()
            .stream()
            .sorted(Comparator.comparing(Element::getIndex))
            .collect(Collectors.toList());
        feature.setElements(distinct);
      });

      if (allowEmptyFeatures) {
        return featureResults;
      }
      if (featureResults.isEmpty()) {
        throw new Exception("Passed files have no features!");
      } else {
        return featureResults;
      }
    }
  }

  private static List<Feature> parseForFeature(File jsonFile) throws IOException {
    try {
      InputStreamReader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8);
      List<Feature> features;
      try {
        features = MAPPER.readValue(reader, new TypeReference<>() {
        });
        if (features.isEmpty()) {
          LOG.log(Level.INFO, () -> String.format("File '%s' does not contain features", jsonFile));
        }
      } catch (Throwable err) {
        try {
          reader.close();
        } catch (Throwable exc) {
          err.addSuppressed(exc);
        }
        throw err;
      }

      reader.close();
      return features;
    } catch (JsonMappingException err) {
      err.printStackTrace();
      LOG.log(Level.INFO, () -> String.format("File '%s' is not a valid Cucumber report!", jsonFile));
      throw err;
    }
  }
}
