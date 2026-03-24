package io.github.ygrip.testara.core.file;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;
import static java.util.Comparator.reverseOrder;

/**
 * <p>FileHelper class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
public class FileHelper {
  /**
   * Open file from the filepath
   *
   * @param filePath string of the file path user want to open
   * @return File object type
   */
  public static File openFile(String filePath) {
    return new File(filePath);
  }

  /**
   * Read file from filepath as a string
   *
   * @param filePath string of the file path user want to read
   * @return String object type
   */
  public static String readFile(String filePath) {
    byte[] encoded = new byte[0];
    try {
      encoded = Files.readAllBytes(Paths.get(filePath));
    } catch (IOException e) {
      log.error("Fail to read file ", e);
    }

    return new String(encoded);
  }

  /**
   * Write input of list of string as a file in the respective filepath
   *
   * @param data     as the data that user want to input in list of string
   * @param filePath is String of the file path stored
   * @param options  StandardOperation as the file write options
   */
  public static String writeFile(List<String> data, String filePath, StandardOpenOption... options) {
    Path path = Paths.get(filePath);
    File file = path.toFile();
    try {
      if (!file.exists()) {
        if (!path.getParent().toFile().exists()) {
          Files.createDirectories(path.getParent());
        }
        Files.createFile(path);
      }
      Files.write(path, data, options);
      return file.getAbsolutePath();
    } catch (IOException e) {
      log.error("Fail to write file ", e);
      return null;
    }
  }

  /**
   * Write input of list of string as a file in the respective filepath
   *
   * @param data     as the data that user want to input in in bytes array
   * @param filePath is String of the file path stored
   * @param options  StandardOperation as the file write options
   */
  public static String writeBytes(byte[] data, String filePath, StandardOpenOption... options) {
    Path path = Paths.get(filePath);
    File file = path.toFile();
    try {
      if (!file.exists()) {
        if (!path.getParent().toFile().exists()) {
          Files.createDirectories(path.getParent());
        }
        Files.createFile(path);
      }
      try (FileOutputStream fos = new FileOutputStream(file)) {
        fos.write(data);
      }
      return file.getAbsolutePath();
    } catch (IOException e) {
      log.error("Fail to write bytes to file ", e);
      return null;
    }
  }

  /**
   * Write input of list of string as a file in the respective filepath
   *
   * @param content  as the data that user want to input in list of string
   * @param filePath is String of the file path stored
   * @param options  StandardOperation as the file write options
   */
  public static String writeFile(String content, String filePath, StandardOpenOption... options) {
    Path path = Paths.get(filePath);
    File file = path.toFile();
    try {
      if (!file.exists()) {
        if (!path.getParent().toFile().exists()) {
          Files.createDirectories(path.getParent());
        }
        Files.createFile(path);
      }
      Files.write(path, content.getBytes(), options);
      return file.getAbsolutePath();
    } catch (IOException e) {
      log.error("Fail to write file ", e);
      return null;
    }
  }

  /**
   * Write input of list of string as a file in the respective filepath with default create
   * as it's StandardOperation
   *
   * @param content  as the data that user want to input in list of string
   * @param filePath is String of the file path stored
   */
  public static String writeFile(String content, String filePath) {
    return writeFile(content, filePath, StandardOpenOption.CREATE);
  }

  /**
   * Create a file from input stream return absolute path if success and null if fail
   *
   * @param content  InputStream from API response or another input stream
   * @param filePath where to save the file
   * @param options  standard copy option to temporary file before saving file
   * @return absolute path when success, null when fail
   */
  public static String writeFile(InputStream content, String filePath, StandardCopyOption... options) {
    Path path = Paths.get(filePath);
    File file = path.toFile();
    try {
      if (!file.exists()) {
        if (!path.getParent().toFile().exists()) {
          Files.createDirectories(path.getParent());
        }
        Files.createFile(path);
      }
      Files.copy(content, path, options);
      content.close();
      return file.getAbsolutePath();
    } catch (IOException e) {
      log.error("Fail to write file ", e);
      return null;
    }
  }


  /**
   * Write input of list of string as a file in the respective filepath with default create
   * as it's StandardOperation
   *
   * @param data     as the data that user want to input in list of string
   * @param filePath is String of the file path stored
   */
  public static String writeFile(List<String> data, String filePath) {
    return writeFile(data, filePath, StandardOpenOption.CREATE);
  }

  /**
   * Write input of object as json like format file
   *
   * @param obj      as the data that user want to input can be generic object
   * @param filePath is String of the file path stored
   * @return String test
   * @throws IOException ioexceptions
   */
  public static String writeJson(Object obj, String filePath) throws IOException {
    Files.createDirectories(Paths.get(filePath).getParent());
    Writer writer = new FileWriter(filePath);
    try {
      Gson gson = new GsonBuilder().enableComplexMapKeySerialization()
          .serializeSpecialFloatingPointValues()
          .disableInnerClassSerialization()
          .disableHtmlEscaping()
          .setPrettyPrinting()
          .serializeNulls()
          .create();
      gson.toJson(obj, writer);
    } catch (Exception e) {
      log.error("Fail to write json files ", e);
    } finally {
      writer.flush();
      writer.close();
    }
    File file = openFile(filePath);
    if (file.exists()) {
      return file.getAbsolutePath();
    }
    return null;
  }

  /**
   * Method to return list of files from a specified path
   *
   * @param path is the folder or file path to be opened
   * @return List of files from the specified path
   */
  public static List<File> openFiles(String path) {
    if (StringUtils.isNotBlank(path)) {
      File folder = new File(path);
      if (folder.isDirectory()) {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
          return new ArrayList<>();
        }
        Arrays.sort(files, comparingLong(File::lastModified).reversed());
        return Arrays.asList(files);
      } else {
        return Collections.singletonList(folder);
      }
    } else {
      return new ArrayList<>();
    }
  }

  /**
   * Method to return list of files from a specified path
   *
   * @param path              is the folder or file path to be opened
   * @param acceptedExtension are list of extensions that accepted
   * @return List of files from the specified path
   */
  public static List<File> openFiles(String path, String... acceptedExtension) {
    if (path != null && !path.trim().isEmpty()) {
      File folder = new File(path);
      if (folder.isDirectory()) {
        File[] files = folder.listFiles((dir, name) -> acceptedFileFormat(name, acceptedExtension));
        if (files != null && files.length != 0) {
          List<File> outputFiles = new ArrayList<>();

          for (File file : files) {
            BasicFileAttributes fileStatus = null;
            try {
              fileStatus = awaitFile(file.toPath(), 5000L);
            } catch (Exception ignored) {

            }
            if (fileStatus != null && fileStatus.creationTime() != null) {
              outputFiles.add(file);
            }
          }

          outputFiles.sort(comparingLong(File::lastModified).reversed());
          return outputFiles;
        } else {
          return Collections.emptyList();
        }
      } else {
        BasicFileAttributes fileStatus = null;
        try {
          fileStatus = awaitFile(folder.toPath(), 5000L);
        } catch (Exception ignored) {

        }
        if (fileStatus != null && fileStatus.creationTime() != null) {
          return acceptedFileFormat(folder.getName(), acceptedExtension) ?
              Collections.singletonList(folder) :
              Collections.emptyList();
        } else {
          return Collections.emptyList();
        }
      }
    } else {
      return Collections.emptyList();
    }
  }

  private static boolean acceptedFileFormat(String filename, String[] extensions) {
    boolean result = true;
    for (String extension : extensions) {
      if (!filename.toLowerCase().endsWith(extension)) {
        result = false;
        break;
      }
    }
    return result;
  }

  /**
   * <p>deleteFile.</p>
   *
   * @param path a {@link String} object.
   * @return a boolean.
   * @throws Exception if any.
   */
  public static boolean deleteFile(String path) throws Exception {
    File file = openFile(path);
    if (file.isDirectory()) {
      AtomicInteger failed = new AtomicInteger();
      try (Stream<Path> walkStream = Files.walk(file.toPath())) {
        walkStream.sorted(reverseOrder()).map(Path::toFile).forEach((deleted) -> {
          boolean current = deleted.exists() && deleted.delete();
          if (!current) {
            failed.getAndIncrement();
            log.warn("Failed to delete {}", deleted.getAbsolutePath());
          }
        });
      } catch (Exception ignored) {
        log.warn("Fail to locate specified path {}", file.toPath().toString());
      }
      return failed.get() == 0;
    } else {
      return file.exists() && file.delete();
    }
  }

  public static String writeToFile(String input, String filePath) {
    Path target = Paths.get(filePath);
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");

    try {
      Files.createDirectories(target.getParent());

      Files.writeString(temp, input, StandardCharsets.UTF_8);

      Files.move(
        temp,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      );

      return target.toAbsolutePath().toString();

    } catch (Exception e) {
      log.error("Failed to write file atomically: {}", filePath, e);
      File file = openFile(filePath);

      return file.exists() ? file.getAbsolutePath() : null;
    }
  }

  public static String copyFile(String sourcePath, String targetPath) throws Exception {
    FileReader reader = new FileReader(openFile(sourcePath));
    return writeToFile(CharStreams.toString(reader), targetPath);
  }

  public static List<String> copyFiles(String sourcePath, String targetPath) throws Exception {
    List<String> result = new ArrayList<>();
    List<File> files = openFiles(sourcePath);
    files.forEach(file -> {
      try {
        result.add(copyFile(file.getAbsolutePath(), targetPath + "/" + file.getName()));
      } catch (Exception ignored) {
      }
    });
    return result;
  }

  public static List<String> copyFiles(String sourcePath, String targetPath, String... extension)
    throws Exception {
    List<String> result = new ArrayList<>();
    List<File> files = openFiles(sourcePath, extension);
    files.forEach(file -> {
      try {
        result.add(copyFile(file.getAbsolutePath(), targetPath + "/" + file.getName()));
      } catch (Exception ignored) {
        log.warn("Fail to copy file " + file.getName());
      }
    });
    return result;
  }

  public static BasicFileAttributes awaitFile(Path target, long timeout) throws IOException, InterruptedException {
    final Path name = target.getFileName();
    final Path targetDir = target.getParent();

    // If path already exists, return early
    try {
      return Files.readAttributes(target, BasicFileAttributes.class);
    } catch (NoSuchFileException ignored) {
    }

    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
       WatchKey watchKey = targetDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
      // The file could have been created in the window between Files.readAttributes and Path.register
      try {
        return Files.readAttributes(target, BasicFileAttributes.class);
      } catch (NoSuchFileException ignored) {
      }
      // The file is absent: watch events in parent directory
      boolean valid = true;
      do {
        long t0 = System.currentTimeMillis();
        watchKey = watchService.poll(timeout, TimeUnit.MILLISECONDS);
        if (watchKey == null) {
          return null; // timed out
        }
        // Examine events associated with key
        for (WatchEvent<?> event : watchKey.pollEvents()) {
          Path path1 = (Path) event.context();
          if (path1.getFileName().equals(name)) {
            return Files.readAttributes(target, BasicFileAttributes.class);
          }
        }
        // Did not receive an interesting event; re-register key to queue
        long elapsed = System.currentTimeMillis() - t0;
        timeout = elapsed < timeout ? (timeout - elapsed) : 0L;
        valid = watchKey.reset();
      } while (valid);
    }

    return null;
  }
}
