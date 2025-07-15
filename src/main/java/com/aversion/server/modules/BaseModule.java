package com.aversion.server.modules;

import com.aversion.server.AversionServer;
import com.aversion.server.tools.Tool;
import com.aversion.server.utils.ReflectionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Base class for all MCP modules.
 *
 * <p>Provides common functionality and enforces a consistent interface
 * for all modules in the system.
 */
public abstract class BaseModule {

  protected static final com.aversion.server.utils.Logger LOGGER = com.aversion.server.utils.Logger.getInstance();
  private static final String ERROR_PREFIX = "Error: ";
  private static final String JSON_SCHEMA_VERSION = "http://json-schema.org/draft-07/schema#";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final String id;
  private final Map<String, Tool> tools = new ConcurrentHashMap<>();
  private AversionServer server;
  private boolean initialized = false;

  protected BaseModule() {
    String id = getClass().getSimpleName().toLowerCase();
    int cutoff = id.length() - 6; // Length of "Module"
    this.id = id.substring(0, cutoff);
  }

  public String getId() {
    return id;
  }

  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Get module configuration information.
   *
   * @return The module's configuration
   */
  public abstract ModuleConfig getConfig();

  /**
   * Initialize the module with the MCP server instance.
   *
   * @param server The MCP server instance to register tools with
   * @throws IllegalStateException if the module is already initialized
   */
  public void initialize(@NotNull AversionServer server) {
    if (initialized)
      throw new IllegalStateException("Module is already initialized");

    ModuleConfig config = getConfig();
    LOGGER.info("Initializing module: {} v{}", config.name(), config.version());

    this.server = server;
    this.initialized = true;

    onLoad();
    registerTools();

    logInitializationComplete(config);
  }

  /**
   * Performs the actual initialization steps.
   * This method is intentionally left empty as the initialization logic is handled in {@link #initialize(AversionServer)}.
   */
  private void performInitialization() {
  }

  /**
   * Logs completion of module initialization.
   */
  private void logInitializationComplete(ModuleConfig config) {
    int toolCount = tools.size();
    LOGGER.info("Module loaded: {} v{} with {} tools",
      config.name(), config.version(), toolCount);
  }

  /**
   * Register all tools for this module.
   * Must be implemented by subclasses.
   */
  protected void registerTools() {
    List<Tool> discoveredTools = ReflectionUtil.getTools(this);
    for (Tool tool : discoveredTools) {
      this.tools.put(tool.name(), tool);
      server.registerTool(tool);
    }
  }

  /**
   * Optional lifecycle method called when module is loaded.
   * Override in subclasses to perform custom initialization logic.
   */
  protected void onLoad() {
    // Override in subclasses if needed
  }

  /**
   * Optional lifecycle method called when module is unloaded.
   * Override in subclasses to perform custom cleanup logic.
   */
  protected void onUnload() {
    // Override in subclasses if needed
  }

  /**
   * Helper method to register a tool with the Aversion server.
   * 
   * <p>This method wraps the tool handler with error handling and monitoring,
   * validates the module is initialized, and registers the tool with the server.
   *
   * @param tool The tool to register, containing name, description, input schema, and handler
   */
  protected void registerTool(Tool tool) {
    validateInitialized();

    Tool.ToolHandler wrappedHandler = createWrappedHandler(tool.name(), tool.handler(), tool.inputSchema());

    server.registerTool(new Tool(tool.name(), tool.description(), tool.inputSchema(), wrappedHandler));
  }

  /**
   * Validates that the module is initialized before tool registration.
   */
  private void validateInitialized() {
    if (!initialized)
      throw new IllegalStateException("Module must be initialized before registering tools");
  }

  /**
   * Creates a wrapped handler with error handling and monitoring.
   */
  private Tool.ToolHandler createWrappedHandler(String name, @NotNull Tool.ToolHandler handler, Map<String, Object> schema) {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    JsonNode schemaNode = objectMapper.convertValue(schema, JsonNode.class);
    JsonSchema jsonSchema = factory.getSchema(schemaNode);

    return (arguments) -> {
      long startTime = System.currentTimeMillis();

      try {
        // Validate arguments against the schema
        Set<ValidationMessage> errors = jsonSchema.validate(arguments);
        if (!errors.isEmpty()) {
          String errorMessages = errors.stream()
            .map(ValidationMessage::getMessage)
            .collect(Collectors.joining(", "));
          throw new IllegalArgumentException("Input validation failed: " + errorMessages);
        }

        LOGGER.debug("Executing tool: {} with args: {}", name, arguments);
        Map<String, Object> result = handler.handle(arguments);

        long responseTime = System.currentTimeMillis() - startTime;
        LOGGER.debug("Tool executed successfully: {} in {}ms", name, responseTime);

        return result;

      } catch (Exception error) {
        long responseTime = System.currentTimeMillis() - startTime;
        LOGGER.error("Tool execution failed: {} after {}ms", name, responseTime, error);
        return createErrorResponse(error.getMessage());
      }
    };
  }


  /**
   * Helper method to create a standardized text response.
   *
   * @param text The text content to include in the response
   * @return A properly formatted response map
   */
  protected Map<String, Object> createTextResponse(String text) {
    return Map.of(
      "content", java.util.List.of(
        Map.of(
          "type", "text",
          "text", text
        )
      ),
      "isError", false
    );
  }

  /**
   * Helper method to create a standardized error response.
   *
   * @param error The error message to include in the response
   * @return A properly formatted error response map
   */
  protected Map<String, Object> createErrorResponse(String error) {
    return Map.of(
      "content", java.util.List.of(
        Map.of(
          "type", "text",
          "text", ERROR_PREFIX + error
        )
      ),
      "isError", true
    );
  }

  /**
   * Helper method to create a JSON schema from a basic structure.
   *
   * @return A basic JSON schema object
   */
  protected Map<String, Object> createJsonSchema() {
    return Map.of(
      "type", "object",
      "additionalProperties", true,
      "$schema", JSON_SCHEMA_VERSION
    );
  }

  /**
   * Get all registered tools for this module.
   *
   * @return Map of tool names to their definitions
   */
  public Map<String, Tool> getTools() {
    return Map.copyOf(tools);
  }

  /**
   * Reset the module state for testing purposes.
   * WARNING: This method is intended for testing only.
   */
  @ApiStatus.Internal
  public void resetForTesting() {
    initialized = false;
    server = null;
    tools.clear();
  }


  /**
   * Module configuration record.
   */
  public record ModuleConfig(
    String name,
    String version,
    java.util.Optional<String> description
  ) {
    public ModuleConfig(String name, String version) {
      this(name, version, java.util.Optional.empty());
    }

    public ModuleConfig(String name, String version, String description) {
      this(name, version, java.util.Optional.of(description));
    }
  }
}
