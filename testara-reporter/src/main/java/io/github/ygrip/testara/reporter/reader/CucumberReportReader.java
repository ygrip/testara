package io.github.ygrip.testara.reporter.reader;

import static io.github.ygrip.testara.core.file.FileHelper.openFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.parser.CucumberReportParser;

public class CucumberReportReader {

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

  public static List<File> getReportFiles(String fullPath) throws Exception {
    return multipleReportsExists(fullPath) ?
        FileHelper.openFiles(fullPath, ".json") :
        Collections.singletonList(openFile(fullPath));
  }
}
