package com.aversion.server.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.aversion.server.utils.JsonUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * File system module for MCP server.
 * <p>
 * Provides tools for file and directory operations including
 * reading, writing, managing files and directories, and executing system commands.
 */
public class FileSystemModule extends BaseModule {

  @Override
  public ModuleConfig getConfig() {
    return new ModuleConfig(
      "filesystem",
      "1.0.0",
      "File system operations module for reading, writing, managing files and directories"
    );
  }

  

  // Tool handlers

  /**
   * Handles the "read_file" tool call. Reads the content of a specified file.
   *
   * @param args JsonNode containing the tool arguments:
   *             - "path" (String): The absolute path to the file to read.
   *             - "encoding" (String, optional, default: "UTF-8"): The character encoding to use for reading the file.
   * @return A Map containing the file content as a text response.
   * @throws Exception if the file cannot be read or the path is invalid.
   */
  @com.aversion.server.tools.ToolDefinition(name = "read_file", description = "Read the contents of a file")
  private Map<String, Object> handleReadFile(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");
    String encoding = JsonUtil.getStringField(args, "encoding", "UTF-8");

    Path path = Paths.get(pathStr).toAbsolutePath();
    String content = Files.readString(path, java.nio.charset.Charset.forName(encoding));

    return createTextResponse("File content:\n" + content);
  }

  @com.aversion.server.tools.ToolDefinition(name = "write_file", description = "Write content to a file")
  private Map<String, Object> handleWriteFile(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");
    String content = JsonUtil.getStringField(args, "content");
    String encoding = JsonUtil.getStringField(args, "encoding", "UTF-8");
    boolean createDirectory = JsonUtil.getBooleanField(args, "createDirectory", false);

    Path path = Paths.get(pathStr).toAbsolutePath();

    if (createDirectory) {
      Files.createDirectories(path.getParent());
    }

    Files.writeString(path, content, java.nio.charset.Charset.forName(encoding));

    return createTextResponse(String.format("Successfully wrote %d characters to %s",
      content.length(), path));
  }

  @com.aversion.server.tools.ToolDefinition(name = "list_directory", description = "List the contents of a directory")
  private Map<String, Object> handleListDirectory(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");
    boolean detailed = JsonUtil.getBooleanField(args, "detailed", false);

    Path path = Paths.get(pathStr).toAbsolutePath();

    if (!Files.isDirectory(path)) {
      throw new IllegalArgumentException("Path is not a directory: " + path);
    }

    StringBuilder result = new StringBuilder("Directory contents:\n");

    if (detailed) {
      result.append(String.format("%-10s %10s %-24s %s%n", "Type", "Size", "Modified", "Name"));
      result.append("-".repeat(60)).append("\n");
    }

    try (Stream<Path> entries = Files.list(path)) {
      entries.sorted().forEach(entry -> {
        try {
          if (detailed) {
            BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
            String type = attrs.isDirectory() ? "directory" : "file";
            String size = attrs.isRegularFile() ? String.valueOf(attrs.size()) : "-";
            String modified = attrs.lastModifiedTime().toString();
            String name = entry.getFileName().toString();

            result.append(String.format("%-10s %10s %-24s %s%n", type, size, modified, name));
          } else {
            result.append(entry.getFileName().toString()).append("\n");
          }
        } catch (IOException e) {
          result.append("Error reading: ").append(entry.getFileName().toString()).append("\n");
        }
      });
    }

    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "create_directory", description = "Create a new directory")
  private Map<String, Object> handleCreateDirectory(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");
    boolean recursive = JsonUtil.getBooleanField(args, "recursive", true);

    Path path = Paths.get(pathStr).toAbsolutePath();

    if (recursive) {
      Files.createDirectories(path);
    } else {
      Files.createDirectory(path);
    }

    return createTextResponse("Successfully created directory: " + path);
  }

  @com.aversion.server.tools.ToolDefinition(name = "delete_file", description = "Delete a file")
  private Map<String, Object> handleDeleteFile(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");

    Path path = Paths.get(pathStr).toAbsolutePath();
    Files.delete(path);

    return createTextResponse("Successfully deleted file: " + path);
  }

  @com.aversion.server.tools.ToolDefinition(name = "delete_directory", description = "Delete a directory")
  private Map<String, Object> handleDeleteDirectory(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");
    boolean recursive = JsonUtil.getBooleanField(args, "recursive", false);

    Path path = Paths.get(pathStr).toAbsolutePath();

    if (recursive) {
      deleteDirectoryRecursively(path);
    } else {
      Files.delete(path);
    }

    return createTextResponse("Successfully deleted directory: " + path);
  }

  @com.aversion.server.tools.ToolDefinition(name = "file_stats", description = "Get detailed information about a file or directory")
  private Map<String, Object> handleFileStats(JsonNode args) throws Exception {
    String pathStr = JsonUtil.getStringField(args, "path");

    Path path = Paths.get(pathStr).toAbsolutePath();
    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

    String info = "File/Directory Stats:\n" + "path: " + path + "\n" +
      "name: " + path.getFileName() + "\n" +
      "type: " + (attrs.isDirectory() ? "directory" : "file") + "\n" +
      "size: " + attrs.size() + "\n" +
      "created: " + attrs.creationTime() + "\n" +
      "modified: " + attrs.lastModifiedTime() + "\n" +
      "accessed: " + attrs.lastAccessTime() + "\n" +
      "isReadable: " + Files.isReadable(path) + "\n" +
      "isWritable: " + Files.isWritable(path) + "\n" +
      "isExecutable: " + Files.isExecutable(path) + "\n";

    return createTextResponse(info);
  }

  @com.aversion.server.tools.ToolDefinition(name = "file_exists", description = "Check if a file or directory exists")
  private Map<String, Object> handleFileExists(JsonNode args) {
    String pathStr = JsonUtil.getStringField(args, "path");

    Path path = Paths.get(pathStr).toAbsolutePath();
    boolean exists = Files.exists(path);

    String message = exists ?
      "Path exists: " + path :
      "Path does not exist: " + path;

    return createTextResponse(message);
  }

  @com.aversion.server.tools.ToolDefinition(name = "copy_file", description = "Copy a file from source to destination")
  private Map<String, Object> handleCopyFile(JsonNode args) throws Exception {
    String sourceStr = JsonUtil.getStringField(args, "source");
    String destinationStr = JsonUtil.getStringField(args, "destination");
    boolean createDirectory = JsonUtil.getBooleanField(args, "createDirectory", false);

    Path source = Paths.get(sourceStr).toAbsolutePath();
    Path destination = Paths.get(destinationStr).toAbsolutePath();

    if (createDirectory) {
      Files.createDirectories(destination.getParent());
    }

    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

    return createTextResponse(String.format("Successfully copied file from %s to %s", source, destination));
  }

  @com.aversion.server.tools.ToolDefinition(name = "move_file", description = "Move/rename a file from source to destination")
  private Map<String, Object> handleMoveFile(JsonNode args) throws Exception {
    String sourceStr = JsonUtil.getStringField(args, "source");
    String destinationStr = JsonUtil.getStringField(args, "destination");
    boolean createDirectory = JsonUtil.getBooleanField(args, "createDirectory", false);

    Path source = Paths.get(sourceStr).toAbsolutePath();
    Path destination = Paths.get(destinationStr).toAbsolutePath();

    if (createDirectory) {
      Files.createDirectories(destination.getParent());
    }

    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

    return createTextResponse(String.format("Successfully moved file from %s to %s", source, destination));
  }

  @com.aversion.server.tools.ToolDefinition(name = "execute_command", description = "Execute a system command and return the output")
  private Map<String, Object> handleExecuteCommand(JsonNode args) throws Exception {
    String command = JsonUtil.getStringField(args, "command");
    JsonNode argsNode = args.get("args");
    String workingDirectory = JsonUtil.getStringField(args, "workingDirectory", null);
    int timeout = JsonUtil.getIntField(args, "timeout", 30000);

    List<String> commandList = new ArrayList<>();
    commandList.add(command);

    if (argsNode != null && argsNode.isArray()) {
      argsNode.forEach(arg -> commandList.add(arg.asText()));
    }

    ProcessBuilder processBuilder = new ProcessBuilder(commandList);

    if (workingDirectory != null) {
      processBuilder.directory(Paths.get(workingDirectory).toFile());
    }

    Process process = processBuilder.start();
    boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);

    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("Command timed out after " + timeout + "ms");
    }

    String stdout = new String(process.getInputStream().readAllBytes());
    String stderr = new String(process.getErrorStream().readAllBytes());
    int exitCode = process.exitValue();

    StringBuilder output = new StringBuilder();
    output.append("Command: ").append(String.join(" ", commandList)).append("\n");
    if (workingDirectory != null) {
      output.append("Working Directory: ").append(workingDirectory).append("\n");
    }
    output.append("Exit Code: ").append(exitCode).append("\n\n");

    if (!stdout.isEmpty()) {
      output.append("STDOUT:\n").append(stdout).append("\n");
    }

    if (!stderr.isEmpty()) {
      output.append("STDERR:\n").append(stderr).append("\n");
    }

    return createTextResponse(output.toString().trim());
  }

  @com.aversion.server.tools.ToolDefinition(name = "execute_command_stream", description = "Execute a system command with streaming support and stdin input")
  private Map<String, Object> handleExecuteCommandStream(JsonNode args) throws Exception {
    String command = JsonUtil.getStringField(args, "command");
    String workingDirectoryStr = JsonUtil.getStringField(args, "workingDirectory", null);
    JsonNode stdinNode = args.get("stdin");
    String stdin = (stdinNode != null && !stdinNode.isNull()) ? stdinNode.asText() : null;
    int timeout = JsonUtil.getIntField(args, "timeout", 30000);

    List<String> commandList = Arrays.asList(command.split("\\s+"));
    ProcessBuilder pb = new ProcessBuilder(commandList);

    if (workingDirectoryStr != null) {
      Path workingDirectory = Paths.get(workingDirectoryStr).toAbsolutePath();
      pb.directory(workingDirectory.toFile());
    }

    pb.redirectErrorStream(true);
    Process process = pb.start();

    // Handle stdin if provided
    if (stdin != null && !stdin.isEmpty()) {
      try (var writer = new java.io.OutputStreamWriter(process.getOutputStream())) {
        writer.write(stdin);
        writer.flush();
      }
    }

    boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);

    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("Command timed out after " + timeout + "ms");
    }

    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.exitValue();

    return createTextResponse(String.format("Command: %s\nExit Code: %d\nOutput:\n%s",
      command, exitCode, output));
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_read_files", description = "Read multiple files in batch operation")
  private Map<String, Object> handleBatchReadFiles(JsonNode args) {
    JsonNode pathsNode = args.get("paths");
    if (pathsNode == null || !pathsNode.isArray()) {
      throw new IllegalArgumentException("'paths' must be an array");
    }

    String encoding = JsonUtil.getStringField(args, "encoding", "UTF-8");
    java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encoding);

    StringBuilder result = new StringBuilder();
    result.append("Batch read operation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode pathNode : pathsNode) {
      String pathStr = pathNode.asText();
      try {
        Path path = Paths.get(pathStr).toAbsolutePath();
        String content = Files.readString(path, charset);
        result.append("✓ ").append(pathStr).append(" (").append(content.length()).append(" chars)\n");
        result.append("Content:\n").append(content).append("\n\n");
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(pathStr).append(" - Error: ").append(e.getMessage()).append("\n\n");
        errorCount++;
      }
    }

    result.append(String.format("Summary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_write_files", description = "Write multiple files in batch operation")
  private Map<String, Object> handleBatchWriteFiles(JsonNode args) {
    JsonNode filesNode = args.get("files");
    if (filesNode == null || !filesNode.isArray()) {
      throw new IllegalArgumentException("'files' must be an array");
    }

    String encoding = JsonUtil.getStringField(args, "encoding", "UTF-8");
    boolean createDirectories = JsonUtil.getBooleanField(args, "createDirectories", false);
    java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encoding);

    StringBuilder result = new StringBuilder();
    result.append("Batch write operation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode fileNode : filesNode) {
      String pathStr = JsonUtil.getStringField(fileNode, "path");
      String content = JsonUtil.getStringField(fileNode, "content");

      try {
        Path path = Paths.get(pathStr).toAbsolutePath();

        if (createDirectories && path.getParent() != null) {
          Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content, charset);
        result.append("✓ ").append(pathStr).append(" (").append(content.length()).append(" chars)\n");
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(pathStr).append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_delete_files", description = "Delete multiple files in batch operation")
  private Map<String, Object> handleBatchDeleteFiles(JsonNode args) {
    JsonNode pathsNode = args.get("paths");
    if (pathsNode == null || !pathsNode.isArray()) {
      throw new IllegalArgumentException("'paths' must be an array");
    }

    boolean force = JsonUtil.getBooleanField(args, "force", false);

    StringBuilder result = new StringBuilder();
    result.append("Batch delete operation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode pathNode : pathsNode) {
      String pathStr = pathNode.asText();
      try {
        Path path = Paths.get(pathStr).toAbsolutePath();

        if (Files.isDirectory(path)) {
          if (force) {
            deleteDirectoryRecursively(path);
            result.append("✓ ").append(pathStr).append(" (directory deleted recursively)\n");
          } else {
            throw new IllegalArgumentException("Path is a directory, use force=true to delete recursively");
          }
        } else {
          Files.delete(path);
          result.append("✓ ").append(pathStr).append(" (file deleted)\n");
        }
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(pathStr).append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_copy_files", description = "Copy multiple files in batch operation")
  private Map<String, Object> handleBatchCopyFiles(JsonNode args) {
    JsonNode operationsNode = args.get("operations");
    if (operationsNode == null || !operationsNode.isArray()) {
      throw new IllegalArgumentException("'operations' must be an array");
    }

    boolean createDirectories = JsonUtil.getBooleanField(args, "createDirectories", false);
    boolean overwrite = JsonUtil.getBooleanField(args, "overwrite", false);

    StringBuilder result = new StringBuilder();
    result.append("Batch copy operation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode opNode : operationsNode) {
      String sourcePath = JsonUtil.getStringField(opNode, "source");
      String destPath = JsonUtil.getStringField(opNode, "destination");

      try {
        Path source = Paths.get(sourcePath).toAbsolutePath();
        Path dest = Paths.get(destPath).toAbsolutePath();

        if (createDirectories && dest.getParent() != null) {
          Files.createDirectories(dest.getParent());
        }

        if (overwrite) {
          Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
          Files.copy(source, dest);
        }

        result.append("✓ ").append(sourcePath).append(" → ").append(destPath).append("\n");
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(sourcePath).append(" → ").append(destPath)
          .append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_move_files", description = "Move multiple files in batch operation")
  private Map<String, Object> handleBatchMoveFiles(JsonNode args) {
    JsonNode operationsNode = args.get("operations");
    if (operationsNode == null || !operationsNode.isArray()) {
      throw new IllegalArgumentException("'operations' must be an array");
    }

    boolean createDirectories = JsonUtil.getBooleanField(args, "createDirectories", false);
    boolean overwrite = JsonUtil.getBooleanField(args, "overwrite", false);

    StringBuilder result = new StringBuilder();
    result.append("Batch move operation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode opNode : operationsNode) {
      String sourcePath = JsonUtil.getStringField(opNode, "source");
      String destPath = JsonUtil.getStringField(opNode, "destination");

      try {
        Path source = Paths.get(sourcePath).toAbsolutePath();
        Path dest = Paths.get(destPath).toAbsolutePath();

        if (createDirectories && dest.getParent() != null) {
          Files.createDirectories(dest.getParent());
        }

        if (overwrite) {
          Files.move(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
          Files.move(source, dest);
        }

        result.append("✓ ").append(sourcePath).append(" → ").append(destPath).append("\n");
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(sourcePath).append(" → ").append(destPath)
          .append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_create_directories", description = "Create multiple directories in batch operation")
  private Map<String, Object> handleBatchCreateDirectories(JsonNode args) {
    JsonNode pathsNode = args.get("paths");
    if (pathsNode == null || !pathsNode.isArray()) {
      throw new IllegalArgumentException("'paths' must be an array");
    }

    boolean createParents = JsonUtil.getBooleanField(args, "createParents", true);

    StringBuilder result = new StringBuilder();
    result.append("Batch directory creation results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode pathNode : pathsNode) {
      String pathStr = pathNode.asText();
      try {
        Path path = Paths.get(pathStr).toAbsolutePath();

        if (createParents) {
          Files.createDirectories(path);
        } else {
          Files.createDirectory(path);
        }

        result.append("✓ ").append(pathStr).append("\n");
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(pathStr).append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  @com.aversion.server.tools.ToolDefinition(name = "batch_file_operations", description = "Execute mixed file operations in a single batch")
  private Map<String, Object> handleBatchFileOperations(JsonNode args) {
    JsonNode operationsNode = args.get("operations");
    if (operationsNode == null || !operationsNode.isArray()) {
      throw new IllegalArgumentException("'operations' must be an array");
    }

    StringBuilder result = new StringBuilder();
    result.append("Batch file operations results:\n\n");

    int successCount = 0;
    int errorCount = 0;

    for (JsonNode opNode : operationsNode) {
      String operation = JsonUtil.getStringField(opNode, "operation");
      String target = JsonUtil.getStringField(opNode, "target");

      try {
        Path targetPath = Paths.get(target);
        targetPath = targetPath.toAbsolutePath();
        switch (operation.toLowerCase()) {
          case "delete" -> {
            if (Files.isDirectory(targetPath)) {
              deleteDirectoryRecursively(targetPath);
            } else {
              Files.delete(targetPath);
            }
            result.append("✓ DELETE ").append(target).append("\n");
          }
          case "create_dir" -> {
            Files.createDirectories(targetPath);
            result.append("✓ CREATE_DIR ").append(target).append("\n");
          }
          case "copy" -> {
            String source = JsonUtil.getStringField(opNode, "source");
            Path sourcePath = Paths.get(source).toAbsolutePath();
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            result.append("✓ COPY ").append(source).append(" → ").append(target).append("\n");
          }
          case "move" -> {
            String moveSource = JsonUtil.getStringField(opNode, "source");
            Path moveSourcePath = Paths.get(moveSource).toAbsolutePath();
            Files.move(moveSourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            result.append("✓ MOVE ").append(moveSource).append(" → ").append(target).append("\n");
          }
          default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }
        successCount++;
      } catch (Exception e) {
        result.append("✗ ").append(operation.toUpperCase()).append(" ").append(target)
          .append(" - Error: ").append(e.getMessage()).append("\n");
        errorCount++;
      }
    }

    result.append(String.format("\nSummary: %d successful, %d failed", successCount, errorCount));
    return createTextResponse(result.toString());
  }

  // Utility methods

  private void deleteDirectoryRecursively(@NotNull Path path) throws IOException {
    Files.walkFileTree(path, new SimpleFileVisitor<>() {

      @Override
      public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

}
