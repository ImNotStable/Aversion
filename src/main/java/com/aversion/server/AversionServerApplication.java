package com.aversion.server;

import com.aversion.server.modules.ModuleManager;
import com.aversion.server.transport.StdioServerTransport;
import com.aversion.server.utils.ProductionUtils;


import java.util.concurrent.CompletableFuture;

/**
 * Main MCP server application entry point.
 *
 * <p>This class initializes and configures the MCP server with all modules
 * and establishes stdio transport for command-line integration.
 */
public final class AversionServerApplication {
  private static final com.aversion.server.utils.Logger LOGGER = com.aversion.server.utils.Logger.getInstance();
  private static final String SERVER_NAME = "aversion-mcp-server";
  private static final String SERVER_VERSION = "1.0.0";
  private static final int EXIT_CODE_FAILURE = 1;

  private final AversionServer server;
  private final ModuleManager moduleManager;

  public AversionServerApplication() {
    this.server = new AversionServer(SERVER_NAME, SERVER_VERSION);
    this.moduleManager = new ModuleManager(server);
  }

  public AversionServer getServer() {
    return server;
  }

  public ModuleManager getModuleManager() {
    return moduleManager;
  }

  /**
   * Main entry point for the Aversion server application.
   *
   * @param args Command line arguments passed to the application.
   */
  public static void main(String[] args) {
    try {
      LOGGER.info("Starting MCP server initialization");
      validateEnvironment();

      AversionServerApplication app = new AversionServerApplication();
      app.start().join();

    } catch (Exception error) {
      LOGGER.error("Failed to start MCP server", error);
      System.exit(EXIT_CODE_FAILURE);
    }
  }

  /**
   * Validates the environment before starting the server.
   */
  private static void validateEnvironment() {
    ProductionUtils.validateEnvironment();
  }

  /**
   * Start the MCP server asynchronously.
   *
   * @return CompletableFuture that completes when the server is ready
   */
  public CompletableFuture<Void> start() {
    return CompletableFuture.runAsync(() -> {
      try {
        initializeModules();
        setupTransport();
        logStartupComplete();

      } catch (Exception error) {
        LOGGER.error("Server startup failed", error);
        throw new RuntimeException("Failed to start Aversion server", error);
      }
    });
  }

  /**
   * Initialize and register all built-in modules.
   */
  private void initializeModules() {
    try {
      moduleManager.registerKnownModules();
    } catch (Exception error) {
      throw new RuntimeException("Failed to initialize modules", error);
    }
  }

  /**
   * Setup and start the transport layer.
   */
  private void setupTransport() {
    StdioServerTransport transport = new StdioServerTransport();
    logModuleInfo();
    server.connect(transport);
  }

  /**
   * Log successful startup completion.
   */
  private void logStartupComplete() {
    LOGGER.info("Aversion server connected and ready",
      "pid", ProcessHandle.current().pid(),
      "javaVersion", System.getProperty("java.version"),
      "environment", System.getProperty("ENV", "production")
    );
  }

  /**
   * Log detailed information about registered modules.
   */
  private void logModuleInfo() {
    var moduleInfo = moduleManager.getModuleInfo();

    LOGGER.info("Aversion server starting with {} modules", moduleManager.getModuleCount());

    moduleInfo.forEach(module -> LOGGER.info("Registered module: {} v{}",
      module.name(),
      module.version(),
      "description", module.description().orElse("No description")
    ));
  }

}
