package com.aversion.server.modules.filesystem;

import com.aversion.server.modules.BaseModule;
import com.aversion.server.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A module for interacting with the file system, providing functionalities like listing, reading, writing, creating, deleting, moving, copying, and searching files and directories.
 */
public class FileSystemModule extends BaseModule {

  private static final String MODULE_NAME = "filesystem-module";
  private static final String MODULE_VERSION = "1.0.0";
  private static final String MODULE_DESCRIPTION = "A module for interacting with the file system.";

  // TODO: Implement security for allowed paths from application.yml

  @Override
  public ModuleConfig getConfig() {
    return new ModuleConfig(MODULE_NAME, MODULE_VERSION, MODULE_DESCRIPTION);
  }

  @com.aversion.server.tools.ToolDefinition(name = "list_directory", description = "Lists the contents of a directory.")
  private Map<String, Object> handleListDirectory(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    Path path = Paths.get(pathString);

    if (!Files.isDirectory(path)) {
      throw new IllegalArgumentException("Path is not a directory: " + pathString);
    }

    List<Map<String, Object>> contents = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
      for (Path entry : stream) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", entry.getFileName().toString());
        item.put("isDirectory", Files.isDirectory(entry));
        item.put("isFile", Files.isRegularFile(entry));
        contents.add(item);
      }
    }

    return createTextResponse(JsonUtil.formatJson(contents));
  }

  @com.aversion.server.tools.ToolDefinition(name = "read_file", description = "Reads the content of a file.")
  private Map<String, Object> handleReadFile(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    Path path = Paths.get(pathString);

    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Path is not a regular file: " + pathString);
    }

    String content = Files.readString(path);
    return createTextResponse(content);
  }

  @com.aversion.server.tools.ToolDefinition(name = "write_file", description = "Writes content to a file.")
  private Map<String, Object> handleWriteFile(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    String content = JsonUtil.getStringField(args, "content");
    Path path = Paths.get(pathString);

    Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    return createTextResponse("File written successfully: " + pathString);
  }

  @com.aversion.server.tools.ToolDefinition(name = "create_directory", description = "Creates a new directory.")
  private Map<String, Object> handleCreateDirectory(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    Path path = Paths.get(pathString);

    Files.createDirectories(path);
    return createTextResponse("Directory created successfully: " + pathString);
  }

  @com.aversion.server.tools.ToolDefinition(name = "delete_path", description = "Deletes a file or directory.")
  private Map<String, Object> handleDeletePath(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    Path path = Paths.get(pathString);

    if (!Files.exists(path)) {
      throw new IllegalArgumentException("Path does not exist: " + pathString);
    }

    if (Files.isDirectory(path)) {
      try (Stream<Path> walk = Files.walk(path)) {
        walk.sorted(java.util.Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.delete(p);
            } catch (IOException e) {
              throw new RuntimeException("Failed to delete: " + p, e);
            }
          });
      }
    } else {
      Files.delete(path);
    }
    return createTextResponse("Path deleted successfully: " + pathString);
  }

  @com.aversion.server.tools.ToolDefinition(name = "move_path", description = "Moves or renames a file or directory.")
  private Map<String, Object> handleMovePath(JsonNode args) throws IOException {
    String sourcePathString = JsonUtil.getStringField(args, "sourcePath");
    String destinationPathString = JsonUtil.getStringField(args, "destinationPath");
    Path sourcePath = Paths.get(sourcePathString);
    Path destinationPath = Paths.get(destinationPathString);

    Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    return createTextResponse("Path moved successfully from " + sourcePathString + " to " + destinationPathString);
  }

  @com.aversion.server.tools.ToolDefinition(name = "copy_path", description = "Copies a file or directory.")
  private Map<String, Object> handleCopyPath(JsonNode args) throws IOException {
    String sourcePathString = JsonUtil.getStringField(args, "sourcePath");
    String destinationPathString = JsonUtil.getStringField(args, "destinationPath");
    Path sourcePath = Paths.get(sourcePathString);
    Path destinationPath = Paths.get(destinationPathString);

    if (Files.isDirectory(sourcePath)) {
      try (Stream<Path> walk = Files.walk(sourcePath)) {
        walk.forEach(source -> {
          Path destination = destinationPath.resolve(sourcePath.relativize(source));
          try {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            throw new RuntimeException("Failed to copy: " + source, e);
          }
        });
      }
    } else {
      Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }
    return createTextResponse("Path copied successfully from " + sourcePathString + " to " + destinationPathString);
  }

  @com.aversion.server.tools.ToolDefinition(name = "get_file_metadata", description = "Gets metadata for a file or directory.")
  private Map<String, Object> handleGetFileMetadata(JsonNode args) throws IOException {
    String pathString = JsonUtil.getStringField(args, "path");
    Path path = Paths.get(pathString);

    if (!Files.exists(path)) {
      throw new IllegalArgumentException("Path does not exist: " + pathString);
    }

    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("size", attrs.size());
    metadata.put("creationTime", attrs.creationTime().toString());
    metadata.put("lastModifiedTime", attrs.lastModifiedTime().toString());
    metadata.put("lastAccessTime", attrs.lastAccessTime().toString());
    metadata.put("isDirectory", attrs.isDirectory());
    metadata.put("isRegularFile", attrs.isRegularFile());
    metadata.put("isSymbolicLink", attrs.isSymbolicLink());
    metadata.put("isOther", attrs.isOther());

    return createTextResponse(JsonUtil.formatJson(metadata));
  }

  @com.aversion.server.tools.ToolDefinition(name = "search_files", description = "Searches for files by name or content within a directory.")
  private Map<String, Object> handleSearchFiles(JsonNode args) throws IOException {
    String directoryString = JsonUtil.getStringField(args, "directory");
    String fileNamePattern = JsonUtil.getStringField(args, "fileNamePattern", null);
    String contentPattern = JsonUtil.getStringField(args, "contentPattern", null);
    Path directory = Paths.get(directoryString);

    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("Path is not a directory: " + directoryString);
    }

    List<String> matchingFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(directory)) {
      walk.filter(Files::isRegularFile)
        .forEach(file -> {
          boolean nameMatches = true;
          if (fileNamePattern != null) {
            nameMatches = file.getFileName().toString().matches(fileNamePattern);
          }

          boolean contentMatches = true;
          if (contentPattern != null) {
            try {
              contentMatches = Files.readString(file).contains(contentPattern);
            } catch (IOException e) {
              // Log error but continue search
              LOGGER.warn("Could not read file for content search: {}", file, e);
              contentMatches = false;
            }
          }

          if (nameMatches && contentMatches) {
            matchingFiles.add(file.toString());
          }
        });
    }

    return createTextResponse(JsonUtil.formatJson(matchingFiles));
  }
}
