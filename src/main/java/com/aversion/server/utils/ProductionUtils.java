package com.aversion.server.utils;

/**
 * Production utilities for environment validation and server setup.
 */
public class ProductionUtils {

  private static final com.aversion.server.utils.Logger logger = com.aversion.server.utils.Logger.getInstance();

  /**
   * Validate the environment and setup production configuration.
   */
  public static void validateEnvironment() {
    String env = System.getProperty("ENV", "production");
    logger.info("Environment validation started for: {}", env);

    // Validate Java version
    String javaVersion = System.getProperty("java.version");
    if (!isValidJavaVersion(javaVersion)) {
      logger.warn("Java version {} may not be supported. Recommended: Java 21+", javaVersion);
    }

    // Setup system properties
    setupSystemProperties(env);

    // Setup graceful shutdown
    setupGracefulShutdown();

    logger.info("Environment validation completed");
  }

  /**
   * Check if the Java version is valid.
   *
   * @param version Java version string
   * @return true if the version is supported
   */
  private static boolean isValidJavaVersion(String version) {
    try {
      String[] parts = version.split("\\.");
      int majorVersion = Integer.parseInt(parts[0]);
      return majorVersion >= 21;
    } catch (Exception e) {
      logger.warn("Could not parse Java version: {}", version);
      return true; // Assume it's valid if we can't parse
    }
  }

  /**
   * Setup system properties based on environment.
   *
   * @param env Environment name
   */
  private static void setupSystemProperties(String env) {
    if ("development".equals(env)) {
      System.setProperty("logging.level.com.aversion", "DEBUG");
    } else {
      System.setProperty("logging.level.com.aversion", "INFO");
    }
  }

  /**
   * Setup graceful shutdown handling.
   */
  private static void setupGracefulShutdown() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      logger.info("Graceful shutdown initiated");
      // Cleanup logic can be added here
    }));
  }

}
