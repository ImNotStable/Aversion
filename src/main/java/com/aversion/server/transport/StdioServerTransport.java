package com.aversion.server.transport;

import com.aversion.server.utils.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Standard I/O transport implementation for MCP server.
 * <p>
 * This transport reads from stdin and writes to stdout for
 * command-line integration with Aversion clients.
 */
public class StdioServerTransport implements ServerTransport {
  private static final com.aversion.server.utils.Logger logger = com.aversion.server.utils.Logger.getInstance();

  private final BufferedReader inputReader;
  private final PrintWriter outputWriter;
  private final ExecutorService executor;

  private Function<String, CompletableFuture<String>> messageHandler;
  private volatile boolean running = false;

  public StdioServerTransport() {
    this.inputReader = new BufferedReader(new InputStreamReader(System.in));
    this.outputWriter = new PrintWriter(System.out, true);
    this.executor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "stdio-transport");
      t.setDaemon(true);
      return t;
    });
  }

  @Override
  public void onMessage(Function<String, CompletableFuture<String>> handler) {
    this.messageHandler = handler;
  }

  @Override
  public void start() {
    if (running) {
      throw new IllegalStateException("Transport is already running");
    }

    if (messageHandler == null) {
      throw new IllegalStateException("Message handler must be set before starting");
    }

    running = true;

    // Start reading from stdin
    executor.submit(this::readLoop);

    logger.info("Stdio transport started");
  }

  @Override
  public void stop() {
    running = false;
    executor.shutdown();
    logger.info("Stdio transport stopped");
  }

  @Override
  public CompletableFuture<Void> sendMessage(String message) {
    return CompletableFuture.runAsync(() -> {
      synchronized (outputWriter) {
        outputWriter.println(message);
        outputWriter.flush();
      }
    }, executor);
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * Main loop for reading from stdin.
   */
  private void readLoop() {
    try {
      String line;
      while (running && (line = inputReader.readLine()) != null) {
        final String message = line.trim();

        if (!message.isEmpty()) {
          // Process message asynchronously
          messageHandler.apply(message)
            .thenAccept(response -> {
              if (response != null && !response.isEmpty()) {
                sendMessage(response);
              }
            })
            .exceptionally(throwable -> {
              logger.error("Error processing message", throwable);
              return null;
            });
        }
      }
    } catch (IOException e) {
      if (running) {
        logger.error("Error reading from stdin", e);
      }
    } finally {
      running = false;
    }
  }
}
