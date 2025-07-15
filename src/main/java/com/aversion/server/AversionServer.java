package com.aversion.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aversion.server.tools.Tool;
import com.aversion.server.transport.ServerTransport;
import com.aversion.server.utils.Logger;


import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core MCP server implementation.
 * <p>
 * This class manages tool registration, handles requests, and coordinates
 * communication with MCP clients through the transport layer.
 */
public class AversionServer {
  private static final com.aversion.server.utils.Logger logger = com.aversion.server.utils.Logger.getInstance();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final String name;

  public String getName() {
    return name;
  }
  private final String version;

  public String getVersion() {
    return version;
  }
  private final Map<String, Tool> tools = new ConcurrentHashMap<>();
  private ServerTransport transport;

  /**
   * Constructs a new {@code AversionServer} instance with the specified name and version.
   *
   * @param name The name of the server.
   * @param version The version of the server.
   */
  public AversionServer(String name, String version) {
    this.name = name;
    this.version = version;
  }

  /**
   * Register a tool with the server.
   *
   * @param tool The tool to register
   */
  public void registerTool(Tool tool) {
    if (tools.containsKey(tool.name())) {
      throw new IllegalArgumentException("Tool '" + tool.name() + "' is already registered");
    }

    tools.put(tool.name(), tool);

    logger.debug("Registered tool: {}", tool.name());
  }

  /**
   * Connect the server to a transport layer.
   *
   * @param transport The transport to use for communication
   */
  public void connect(ServerTransport transport) {
    this.transport = transport;

    // Setup message handlers
    transport.onMessage(this::handleMessage);

    // Start the transport
    transport.start();

    logger.info("Server connected via {}", transport.getClass().getSimpleName());
  }

  /**
   * Handle incoming messages from the transport layer.
   *
   * @param message The incoming message
   * @return CompletableFuture with the response
   */
  private CompletableFuture<String> handleMessage(String message) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        var jsonMessage = objectMapper.readTree(message);
        return processMessage(jsonMessage);

      } catch (JsonProcessingException e) {
        logger.error("Failed to parse message", e);
        return createErrorResponse("Invalid JSON message");
      } catch (Exception e) {
        logger.error("Error processing message", e);
        return createErrorResponse("Internal server error");
      }
    });
  }

  /**
   * Process a parsed JSON message.
   *
   * @param message The parsed message
   * @return JSON response string
   */
  private String processMessage(JsonNode message) throws JsonProcessingException {
    String method = message.path("method").asText();
    JsonNode id = message.path("id");

    return switch (method) {
      case "tools/list" -> handleToolsList(id);
      case "tools/call" -> handleToolCall(message);
      case "initialize" -> handleInitialize(message);
      default -> createErrorResponse("Unknown method: " + method);
    };
  }

  /**
   * Handle tools/list request.
   *
   * @param id Request ID
   * @return JSON response
   */
  private String handleToolsList(JsonNode id) throws JsonProcessingException {
    var toolsList = tools.values().stream()
      .map(tool -> Map.of(
        "name", tool.name(),
        "description", tool.description(),
        "inputSchema", tool.inputSchema()
      ))
      .toList();

    var response = Map.of(
      "jsonrpc", "2.0",
      "id", id,
      "result", Map.of("tools", toolsList)
    );

    return objectMapper.writeValueAsString(response);
  }

  /**
   * Handle tools/call request.
   *
   * @param message The call message
   * @return JSON response
   */
  private String handleToolCall(JsonNode message) {
    var id = message.path("id");
    var params = message.path("params");
    var toolName = params.path("name").asText();
    var arguments = params.path("arguments");

    var tool = tools.get(toolName);
    if (tool == null) {
      return createErrorResponse("Tool not found: " + toolName);
    }

    try {
      var result = tool.handler().handle(arguments);
      var response = Map.of(
        "jsonrpc", "2.0",
        "id", id,
        "result", result
      );

      return objectMapper.writeValueAsString(response);

    } catch (Exception e) {
      logger.error("Tool execution failed: {}", toolName, e);
      return createErrorResponse("Tool execution failed: " + e.getMessage());
    }
  }

  /**
   * Handle initialize request.
   *
   * @param message The initializing message
   * @return JSON response
   */
  private String handleInitialize(JsonNode message) throws JsonProcessingException {
    var id = message.path("id");

    var response = Map.of(
      "jsonrpc", "2.0",
      "id", id,
      "result", Map.of(
        "protocolVersion", "2024-11-05",
        "capabilities", Map.of(
          "tools", Map.of()
        ),
        "serverInfo", Map.of(
          "name", name,
          "version", version
        )
      )
    );

    return objectMapper.writeValueAsString(response);
  }

  /**
   * Create an error response.
   *
   * @param message Error message
   * @return JSON error response
   */
  private String createErrorResponse(String message) {
    try {
      var response = Map.of(
        "jsonrpc", "2.0",
        "error", Map.of(
          "code", -32000,
          "message", message
        )
      );

      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      logger.error("Failed to create error response", e);
      return "{\"error\":\"Failed to create error response\"}";
    }
  }

  /**
   * Get all registered tools.
   *
   * @return Map of tool names to definitions
   */
  public Map<String, Tool> getTools() {
    return Map.copyOf(tools);
  }
}