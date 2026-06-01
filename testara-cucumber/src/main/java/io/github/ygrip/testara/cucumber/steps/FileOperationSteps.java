package io.github.ygrip.testara.cucumber.steps;


import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.file.CsvHelper;
import io.github.ygrip.testara.core.file.ExcelHelper;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.UpdateExcelData;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author yunaz.ramadhan on 6/26/2020
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class FileOperationSteps {

  @When("{file} create excel file with name {string} and data")
  public void createExcelFileWithName(String identifier, String fileName, DataTable table) throws Throwable {
    List<Map<String, Object>> results =
        MapperHelper.toObject(new TransformerService().sourceData(table.cells()).toList(Map.class),
            new TypeReference<List<Map<String, Object>>>() {
            });
    String createdFilePath = null;
    try {
      createdFilePath = ExcelHelper.writeDataToExcelDocument(results,
          System.getProperty("user.dir") + "/src/test/resources/" + fileName);
    } catch (Exception e) {
      log.warn("Error when create excel", e);
    }
    assertThat("Fail to create file with name " + fileName, CommonHelper.isBlank(createdFilePath), equalTo(false));
  }

  @When("{file} create excel file with name {string} and without header and data")
  public void createExcelFileWithoutHeaderWithName(String identifier, String fileName, DataTable table)
      throws Throwable {
    List<List<Object>> results = MapperHelper.toObject(new TransformerService().sourceData(table.cells()).toCells(),
        new TypeReference<List<List<Object>>>() {
        });
    String createdFilePath = null;
    try {
      createdFilePath = ExcelHelper.writeDataToExcelDocumentWithoutHeader(results,
          System.getProperty("user.dir") + "/src/test/resources/" + fileName);
    } catch (Exception e) {
      log.warn("Error when create excel", e);
    }
    assertThat("Fail to create file with name " + fileName, CommonHelper.isBlank(createdFilePath), equalTo(false));
  }

  @When("{file} create csv file with name {string} and delimiter {word} and data")
  public void createCsvFileWithHeaderWithName(String identifier, String fileName, String delimiter, DataTable table)
      throws Throwable {
    delimiter = !delimiter.equals(",") && !delimiter.equals(";") ? "," : delimiter;
    List<List<String>> data = MapperHelper.toObject(new TransformerService().sourceData(table.cells()).toCells(),
        new TypeReference<List<List<String>>>() {
        });
    char finalDelimiter = delimiter.toCharArray()[0];
    String createdFilePath = null;
    try {
      createdFilePath = CsvHelper.write()
          .fromListOfObject(data)
          .withSeparator(finalDelimiter)
          .withHeader()
          .into(System.getProperty("user.dir") + "/src/test/resources/" + fileName);
    } catch (Exception e) {
      log.warn("Error when create csv file", e);
    }
    assertThat("Fail to create file with name " + fileName, CommonHelper.isBlank(createdFilePath), equalTo(false));
  }

  @When("{file} create csv file with name {string} and delimiter {word} without header and data")
  public void createCsvFileWithoutHeaderWithName(String identifier, String fileName, String delimiter, DataTable table)
      throws Throwable {
    delimiter = !delimiter.equals(",") && !delimiter.equals(";") ? "," : delimiter;
    List<List<String>> data = MapperHelper.toObject(new TransformerService().sourceData(table.cells()).toCells(),
        new TypeReference<List<List<String>>>() {
        });
    char finalDelimiter = delimiter.toCharArray()[0];
    String createdFilePath = null;
    try {
      createdFilePath = CsvHelper.write()
          .fromListOfObject(data)
          .withSeparator(finalDelimiter)
          .withoutHeader()
          .into(System.getProperty("user.dir") + "/src/test/resources/" + fileName);
    } catch (Exception e) {
      log.warn("Error when create csv file", e);
    }
    assertThat("Fail to create file with name " + fileName, CommonHelper.isBlank(createdFilePath), equalTo(false));
  }

  @When("{file} create json file with name {string} and data")
  public void createJsonFileWithName(String identifier, String fileName, DataTable table) throws Throwable {
    List<Map<String, Object>> results =
        MapperHelper.toObject(new TransformerService().sourceData(table.cells()).toList(Map.class),
            new TypeReference<List<Map<String, Object>>>() {
            });
    String createdFilePath = null;
    try {
      createdFilePath =
          FileHelper.writeJson(results, System.getProperty("user.dir") + "/src/test/resources/" + fileName);
    } catch (Exception e) {
      log.warn("Error when create json file", e);
    }
    assertThat("Fail to create file with name " + fileName, CommonHelper.isBlank(createdFilePath), equalTo(false));
  }

  @When("{file} delete file with name {string}")
  public void deleteFile(String identifier, String fileName) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    boolean deleted = FileHelper.deleteFile(System.getProperty("user.dir") + "/" + fileName);
    assertThat("Fail to delete file with name " + fileName, deleted, equalTo(true));
  }

  @When("{file} update excel file with name {string} with new data")
  public void updateExcelFile(String identifier, String fileName, DataTable table) throws Throwable {
    List<UpdateExcelData> results = new TransformerService().sourceData(table.cells()).toList(UpdateExcelData.class);
    boolean updated =
        ExcelHelper.updateExcelData(System.getProperty("user.dir") + "/src/test/resources/" + fileName, results);
    assertThat("Fail to update excel file with name " + fileName, updated, equalTo(true));
  }

  @Then("{file} file {string} should exist")
  public void fileExists(String identifier, String fileName) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    File file = new File(System.getProperty("user.dir") + "/" + fileName);
    assertThat("File " + fileName + " does not exist", file.exists(), equalTo(true));
  }

  @Then("{file} file {string} should not exist")
  public void fileNotExists(String identifier, String fileName) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    File file = new File(System.getProperty("user.dir") + "/" + fileName);
    assertThat("File " + fileName + " should not exist", file.exists(), equalTo(false));
  }

  @Then("{file} file {string} should have size {long} bytes")
  public void fileSizeValidation(String identifier, String fileName, long expectedSize) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    File file = new File(System.getProperty("user.dir") + "/" + fileName);
    assertThat("File " + fileName + " does not exist", file.exists(), equalTo(true));
    assertThat("File " + fileName + " size is not as expected", file.length(), equalTo(expectedSize));
  }

  @Then("{file} file {string} content should be equal to file {string}")
  public void fileContentComparison(String identifier, String file1, String file2) throws Throwable {
    file1 = TestFramework.context().converter().convert(file1);
    file2 = TestFramework.context().converter().convert(file2);

    Path path1 = Paths.get(System.getProperty("user.dir") + "/" + file1);
    Path path2 = Paths.get(System.getProperty("user.dir") + "/" + file2);

    assertThat("File " + file1 + " does not exist", Files.exists(path1), equalTo(true));
    assertThat("File " + file2 + " does not exist", Files.exists(path2), equalTo(true));

    byte[] content1 = Files.readAllBytes(path1);
    byte[] content2 = Files.readAllBytes(path2);

    assertThat("File contents are not equal", content1, equalTo(content2));
  }

  @Then("{file} file {string} should contain text {string}")
  public void fileContainsText(String identifier, String fileName, String expectedText) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    expectedText = TestFramework.context().converter().convert(expectedText);

    Path path = Paths.get(System.getProperty("user.dir") + "/" + fileName);
    assertThat("File " + fileName + " does not exist", Files.exists(path), equalTo(true));

    String content = new String(Files.readAllBytes(path));
    assertThat("File " + fileName + " does not contain expected text", content.contains(expectedText), equalTo(true));
  }

  @Then("{file} file {string} should have extension {string}")
  public void fileExtensionValidation(String identifier, String fileName, String expectedExtension) throws Throwable {
    fileName = TestFramework.context().converter().convert(fileName);
    expectedExtension = TestFramework.context().converter().convert(expectedExtension);

    String actualExtension = "";
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      actualExtension = fileName.substring(lastDotIndex + 1);
    }

    assertThat("File extension is not as expected", actualExtension, equalTo(expectedExtension));
  }

  @When("{file} create directory with name {string}")
  public void createDirectory(String identifier, String dirName) throws Throwable {
    dirName = TestFramework.context().converter().convert(dirName);
    Path dirPath = Paths.get(System.getProperty("user.dir") + "/" + dirName);

    try {
      Files.createDirectories(dirPath);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create directory: " + dirName, e);
    }

    assertThat("Directory " + dirName + " was not created", Files.exists(dirPath), equalTo(true));
  }

  @When("{file} delete directory with name {string}")
  public void deleteDirectory(String identifier, String dirName) throws Throwable {
    dirName = TestFramework.context().converter().convert(dirName);
    Path dirPath = Paths.get(System.getProperty("user.dir") + "/" + dirName);

    if (Files.exists(dirPath)) {
      Files.walk(dirPath).sorted((a, b) -> b.compareTo(a)) // Reverse order for deleting
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              throw new RuntimeException("Failed to delete: " + path, e);
            }
          });
    }

    assertThat("Directory " + dirName + " still exists", Files.exists(dirPath), equalTo(false));
  }

  @Then("{file} directory {string} should exist")
  public void directoryExists(String identifier, String dirName) throws Throwable {
    dirName = TestFramework.context().converter().convert(dirName);
    Path dirPath = Paths.get(System.getProperty("user.dir") + "/" + dirName);
    assertThat("Directory " + dirName + " does not exist", Files.exists(dirPath), equalTo(true));
    assertThat("Path " + dirName + " is not a directory", Files.isDirectory(dirPath), equalTo(true));
  }

  @Then("{file} directory {string} should contain {int} files")
  public void directoryFileCount(String identifier, String dirName, int expectedCount) throws Throwable {
    dirName = TestFramework.context().converter().convert(dirName);
    Path dirPath = Paths.get(System.getProperty("user.dir") + "/" + dirName);

    assertThat("Directory " + dirName + " does not exist", Files.exists(dirPath), equalTo(true));

    List<Path> files = Files.list(dirPath).filter(Files::isRegularFile).collect(Collectors.toList());

    assertThat("Directory " + dirName + " does not contain expected number of files",
        files.size(),
        equalTo(expectedCount));
  }

  @When("{file} copy file from {string} to {string}")
  public void copyFile(String identifier, String sourcePath, String destinationPath) throws Throwable {
    sourcePath = TestFramework.context().converter().convert(sourcePath);
    destinationPath = TestFramework.context().converter().convert(destinationPath);

    Path source = Paths.get(System.getProperty("user.dir") + "/" + sourcePath);
    Path destination = Paths.get(System.getProperty("user.dir") + "/" + destinationPath);

    assertThat("Source file " + sourcePath + " does not exist", Files.exists(source), equalTo(true));

    try {
      // Create parent directories if they don't exist
      Files.createDirectories(destination.getParent());
      Files.copy(source, destination);
    } catch (IOException e) {
      throw new RuntimeException("Failed to copy file from " + sourcePath + " to " + destinationPath, e);
    }

    assertThat("File was not copied to " + destinationPath, Files.exists(destination), equalTo(true));
  }

  @When("{file} move file from {string} to {string}")
  public void moveFile(String identifier, String sourcePath, String destinationPath) throws Throwable {
    sourcePath = TestFramework.context().converter().convert(sourcePath);
    destinationPath = TestFramework.context().converter().convert(destinationPath);

    Path source = Paths.get(System.getProperty("user.dir") + "/" + sourcePath);
    Path destination = Paths.get(System.getProperty("user.dir") + "/" + destinationPath);

    assertThat("Source file " + sourcePath + " does not exist", Files.exists(source), equalTo(true));

    try {
      // Create parent directories if they don't exist
      Files.createDirectories(destination.getParent());
      Files.move(source, destination);
    } catch (IOException e) {
      throw new RuntimeException("Failed to move file from " + sourcePath + " to " + destinationPath, e);
    }

    assertThat("File was not moved to " + destinationPath, Files.exists(destination), equalTo(true));
    assertThat("Source file " + sourcePath + " still exists", Files.exists(source), equalTo(false));
  }
}
