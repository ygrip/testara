package io.github.ygrip.testara.core.file;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

public class FileOperationHelper {
  public Optional<Path> searchFile(Path rootDir, String pattern) throws IOException {
    Optional<Path> result;
    final Path[] paths = {null};
    FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) throws IOException {
        FileSystem fs = FileSystems.getDefault();
        PathMatcher matcher = fs.getPathMatcher(pattern);
        Path name = file.getFileName();
        if (matcher.matches(name)) {
          paths[0] = file;
          return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
      }
    };
    Files.walkFileTree(rootDir, matcherVisitor);
    result = Optional.ofNullable(paths[0]);
    return result;
  }
}
