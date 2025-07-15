package com.aversion.server.utils;

import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for the MCP server.
 * <p>
 * Provides structured logging capabilities with consistent formatting
 * and automatic context enrichment.
 */
public class Logger {

  private static Logger instance;
  private final org.slf4j.Logger slf4jLogger;

  private Logger() {
    this.slf4jLogger = LoggerFactory.getLogger("Aversion");
  }

  /**
   * Get the singleton logger instance.
   *
   * @return The logger instance
   */
  public static synchronized Logger getInstance() {
    if (instance == null)
      instance = new Logger();
    return instance;
  }

  /**
   * Log an info message.
   *
   * @param message The message template
   * @param args    Arguments for the message template
   */
  public void info(String message, Object... args) {
    slf4jLogger.info(message, args);
  }

  /**
   * Log a debug message.
   *
   * @param message The message template
   * @param args    Arguments for the message template
   */
  public void debug(String message, Object... args) {
    slf4jLogger.debug(message, args);
  }

  /**
   * Log a warning message.
   *
   * @param message The message template
   * @param args    Arguments for the message template
   */
  public void warn(String message, Object... args) {
    slf4jLogger.warn(message, args);
  }

  /**
   * Log an error message.
   *
   * @param message The message template
   * @param args    Arguments for the message template
   */
  public void error(String message, Object... args) {
    slf4jLogger.error(message, args);
  }

  /**
   * Log an error message with exception.
   *
   * @param message   The message
   * @param throwable The exception
   */
  public void error(String message, Throwable throwable) {
    slf4jLogger.error(message, throwable);
  }

  /**
   * Log an error message with exception and arguments.
   *
   * @param message   The message template
   * @param arg1      First argument
   * @param arg2      Second argument
   * @param throwable The exception
   */
  public void error(String message, Object arg1, Object arg2, Throwable throwable) {
    slf4jLogger.error(message, arg1, arg2, throwable);
  }

}