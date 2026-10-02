package io.github.ygrip.testara.reporter.reader;

import static io.github.ygrip.testara.core.file.FileHelper.openFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.parser.CucumberReportParser;

public class CucumberReportReader {

  private static final Logger LOG = Logger.getLogger(CucumberReportReader.class.getName());

  private static boolean multipleReportsExists(String fullPath) {
    File check = openFile(fullPath);
    return check.exists() && check.isDirectory();
  }

  public static List<Feature> readReports(String fullPath) throws Exception {
    return CucumberReportParser.parseJsonFiles(getReportPaths(fullPath));
  }

  public static List<String> getReportPaths(String fullPath) throws Exception {
    List<File> files = getReportFiles(fullPath);
    return files.stream().map(File::getAbsolutePath).collect(Collectors.toList());
  }

  /**
   * Report files at {@code fullPath}: every {@code .json} file of a directory, the file itself, or
   * nothing when the path does not exist (e.g. the run wrote no report). Returning the missing path
   * would make formatters write an empty report there and turn the report directory into a file.
   */
  public static List<File> getReportFiles(String fullPath) throws Exception {
    File report = openFile(fullPath);
    if (!report.exists()) {
      LOG.warning("No cucumber report found at " + fullPath);
      return Collections.emptyList();
    }
    if (multipleReportsExists(fullPath)) {
      return FileHelper.openFiles(fullPath, ".json");
    }
    return Collections.singletonList(report);
  }
}
