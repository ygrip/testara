package io.github.ygrip.testara.reporter.formatter;

import static io.github.ygrip.testara.core.file.FileHelper.openFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.base.Stopwatch;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.Element;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.parser.CucumberReportParser;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.support.ObjectMapperHelper;

public class CucumberJsonFormatter {
  private static final ObjectMapper MAPPER = ObjectMapperHelper.mapper();
  private final Logger log = Logger.getLogger(CucumberJsonFormatter.class.getName());
  private final String DIR = System.getProperty("user.dir");
  private final String FULLPATH;
  private boolean overwite;

  CucumberJsonFormatter(String path) {
    FULLPATH = DIR + path;
    overwite = true;
  }

  public static CucumberJsonFormatter fromTargetLocation(String path) {
    return new CucumberJsonFormatter(path);
  }

  public CucumberJsonFormatter overwrite(boolean overwite) {
    this.overwite = overwite;
    return this;
  }

  public void rewriteScenarioWithOutlines() throws Exception {
    Stopwatch stopwatch = Stopwatch.createStarted();
    List<String> filePath = CucumberReportReader.getReportPaths(FULLPATH);
    for (String path : filePath) {
      try {
        List<Feature> features =
            CucumberReportParser.parseJsonFiles(Collections.singletonList(path));
        for (Feature feature : features) {
          try {
            List<Element> elements = feature.getElements();
            for (Element element : elements) {
              if (element.isScenarioOutline()) {
                String[] splitted = element.getId().split(";");
                String input = splitted[1];
                String output = convertToId(element.getName());
                if (!input.equals(output)) {
                  splitted[1] = output;
                }
                element.setId(String.join(";", splitted));
              }
            }
          } catch (Exception err) {
            log.warning(String.format("Error parsing : %s", path));
            throw err;
          }
        }
        rewriteFeatures(path, features);
      } catch (Exception err) {
        log.warning(String.format("Error parsing : %s", path));
        throw err;
      }
    }

    log.info(String.format("Processing %s reports took %s ms",
        filePath.size(),
        stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)));
  }

  private void rewriteFeatures(String filePath, List<Feature> features) {
    try {
      String path = overwite ?
          filePath :
          getReportOutputDirectory() + "/custom-report/" + getFileName(filePath);
      FileHelper.writeToFile(MAPPER.writeValueAsString(features), path);
    } catch (Exception err) {
      log.warning("Unable to overwrite report for " + filePath);
      err.printStackTrace();
    }
  }

  private String getFileName(String path) {
    return openFile(path).getName();
  }

  private String getReportOutputDirectory() {
    File check = openFile(FULLPATH);
    if (check.exists()) {
      if (check.isDirectory()) {
        return FULLPATH;
      } else {
        return check.getParentFile().getAbsolutePath();
      }
    } else {
      return DIR + "/target/destination/";
    }
  }

  private String convertToId(String name) {
    return name.replaceAll("[\\s'_,!]", "-").toLowerCase();
  }
}
