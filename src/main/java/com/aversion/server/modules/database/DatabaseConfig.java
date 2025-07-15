package com.aversion.server.modules.database;

/**
 * Database configuration sealed interface with implementations for different database types.
 */
public sealed interface DatabaseConfig
  permits DatabaseConfig.SQLiteConfig,
  DatabaseConfig.MySQLConfig,
  DatabaseConfig.PostgreSQLConfig {

  String type();

  /**
   * SQLite database configuration.
   */
  record SQLiteConfig(String file) implements DatabaseConfig {
    @Override
    public String type() {
      return "sqlite";
    }
  }

  /**
   * MySQL database configuration.
   */
  record MySQLConfig(
    String host,
    int port,
    String database,
    String username,
    String password
  ) implements DatabaseConfig {
    @Override
    public String type() {
      return "mysql";
    }
  }

  /**
   * PostgreSQL database configuration.
   */
  record PostgreSQLConfig(
    String host,
    int port,
    String database,
    String username,
    String password
  ) implements DatabaseConfig {
    @Override
    public String type() {
      return "postgresql";
    }
  }
}
