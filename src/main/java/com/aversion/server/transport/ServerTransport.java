package com.aversion.server.transport;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interface for MCP server transport layers.
 * <p>
 * Transport implementations handle the actual communication
 * between the MCP server and clients.
 */
public interface ServerTransport {

  /**
   * Sets the message handler for processing incoming messages.
   *
   * @param handler A {@code Function} that takes a message {@code String} and returns a {@code CompletableFuture<String>}
   *                representing the asynchronous processing of the message and its response.
   */
  void onMessage(Function<String, CompletableFuture<String>> handler);

  /**
   * Start the transport layer.
   */
  void start();

  /**
   * Stop the transport layer.
   */
  void stop();

  /**
   * Send a message to the client.
   *
   * @param message The message to send
   * @return CompletableFuture that completes when message is sent
   */
  CompletableFuture<Void> sendMessage(String message);

  /**
   * Check if the transport is running.
   *
   * @return true if running, false otherwise
   */
  boolean isRunning();

}
