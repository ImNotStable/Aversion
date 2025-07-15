package com.aversion.server.modules.database;

import com.aversion.server.modules.BaseModule;
import com.aversion.server.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Database module for MCP server.
 *
 * <p>Provides tools for interacting with databases including SQLite, MySQL, and PostgreSQL.
 */
public final class DatabaseModule extends BaseModule {

  private static final String MODULE_NAME = "database-module";
  private static final String MODULE_VERSION = "1.0.0";
  private static final String MODULE_DESCRIPTION =
    "A set of database interaction tools supporting SQLite, MySQL, and PostgreSQL.";

  private final DatabaseConnectionManager connectionManager;

  public DatabaseModule() {
    this.connectionManager = new DatabaseConnectionManager();
  }

  @Override
  public ModuleConfig getConfig() {
    return new ModuleConfig(MODULE_NAME, MODULE_VERSION, MODULE_DESCRIPTION);
  }

  /**
   * Returns the {@link DatabaseConnectionManager} instance used by this module.
   *
   * @return The DatabaseConnectionManager instance.
   */
  public DatabaseConnectionManager getConnectionManager() {
    return connectionManager;
  }

  // Tool handlers

  @com.aversion.server.tools.ToolDefinition(name = "connect_database", description = "Connect to a SQL database (SQLite, MySQL, or PostgreSQL) with connection pooling")
  private Map<String, Object> handleConnectDatabase(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    JsonNode configNode = JsonUtil.getObjectField(args, "config");

    if (connectionManager.hasConnection(connectionId)) {
      throw new IllegalArgumentException("Connection '" + connectionId + "' already exists");
    }

    DatabaseConfig config = parseDatabaseConfig(configNode);
    connectionManager.connect(connectionId, config);

    return createTextResponse("Successfully connected to " + config.type() + " database: " + connectionId);
  }

  @com.aversion.server.tools.ToolDefinition(name = "execute_query", description = "Execute a SQL query against a connected database")
  private Map<String, Object> handleExecuteQuery(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String query = JsonUtil.getStringField(args, "query");
    JsonNode paramsNode = args.get("params");
    int limit = JsonUtil.getIntField(args, "limit", 1000);

    List<Object> params = new ArrayList<>();
    if (paramsNode != null && paramsNode.isArray()) {
      paramsNode.forEach(param -> params.add(JsonUtil.convertJsonValue(param)));
    }

    QueryResult result = connectionManager.executeQuery(connectionId, query, params, limit);

    Map<String, Object> response = Map.of(
      "rowCount", result.rowCount(),
      "columns", result.columns(),
      "rows", result.rows(),
      "affectedRows", result.affectedRows(),
      "query", query.length() > 100 ? query.substring(0, 100) + "..." : query
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "execute_transaction", description = "Execute multiple SQL statements as a transaction with automatic rollback on failure")
  private Map<String, Object> handleExecuteTransaction(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    JsonNode queriesNode = JsonUtil.getArrayField(args, "queries");

    List<DatabaseConnectionManager.QueryWithParams> queries = new ArrayList<>();
    for (JsonNode queryNode : queriesNode) {
      String query = JsonUtil.getStringField(queryNode, "query");
      JsonNode paramsNode = queryNode.get("params");

      List<Object> params = new ArrayList<>();
      if (paramsNode != null && paramsNode.isArray()) {
        paramsNode.forEach(param -> params.add(JsonUtil.convertJsonValue(param)));
      }

      queries.add(new DatabaseConnectionManager.QueryWithParams(query, params));
    }

    List<QueryResult> results = connectionManager.executeTransaction(connectionId, queries);

    List<Map<String, Object>> resultList = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      QueryResult result = results.get(i);
      resultList.add(Map.of(
        "queryIndex", i,
        "rowCount", result.rowCount(),
        "affectedRows", result.affectedRows()
      ));
    }

    Map<String, Object> response = Map.of(
      "transactionComplete", true,
      "queryCount", queries.size(),
      "results", resultList
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "get_table_schema", description = "Get detailed schema information for a specific table including primary keys and constraints")
  private Map<String, Object> handleGetTableSchema(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");

    List<Map<String, Object>> schema = connectionManager.getTableSchema(connectionId, tableName);

    Map<String, Object> response = Map.of(
      "tableName", tableName,
      "columns", schema
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "list_tables", description = "List all tables in the connected database")
  private Map<String, Object> handleListTables(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");

    List<Map<String, Object>> tables = connectionManager.listTables(connectionId);

    Map<String, Object> response = Map.of(
      "tableCount", tables.size(),
      "tables", tables
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "disconnect_database", description = "Disconnect from a previously connected database")
  private Map<String, Object> handleDisconnectDatabase(JsonNode args) {
    String connectionId = JsonUtil.getStringField(args, "connectionId");

    connectionManager.disconnect(connectionId);

    return createTextResponse("Successfully disconnected from database: " + connectionId);
  }

  @com.aversion.server.tools.ToolDefinition(name = "get_database_metrics", description = "Get performance metrics for database connections including query counts and pool statistics")
  private @NotNull Map<String, Object> handleGetMetrics(@NotNull JsonNode args) {
    Map<String, Object> metrics = connectionManager.getMetrics();
    return createTextResponse(JsonUtil.formatJson(metrics));
  }

  @com.aversion.server.tools.ToolDefinition(name = "insert_data", description = "Insert new data into a specified table")
  private Map<String, Object> handleInsertData(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");
    JsonNode dataNode = JsonUtil.getObjectField(args, "data");

    Map<String, Object> data = JsonUtil.getObjectMapper().convertValue(dataNode, Map.class);

    int affectedRows = connectionManager.insertData(connectionId, tableName, data);

    Map<String, Object> response = Map.of(
      "tableName", tableName,
      "affectedRows", affectedRows
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "update_data", description = "Update existing data in a specified table")
  private Map<String, Object> handleUpdateData(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");
    JsonNode dataNode = JsonUtil.getObjectField(args, "data");
    String whereClause = JsonUtil.getStringField(args, "where");
    JsonNode paramsNode = args.get("params");

    Map<String, Object> data = JsonUtil.getObjectMapper().convertValue(dataNode, Map.class);

    List<Object> params = new ArrayList<>();
    if (paramsNode != null && paramsNode.isArray()) {
      paramsNode.forEach(param -> params.add(JsonUtil.convertJsonValue(param)));
    }

    int affectedRows = connectionManager.updateData(connectionId, tableName, data, whereClause, params);

    Map<String, Object> response = Map.of(
      "tableName", tableName,
      "affectedRows", affectedRows
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "delete_data", description = "Delete data from a specified table")
  private Map<String, Object> handleDeleteData(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");
    String whereClause = JsonUtil.getStringField(args, "where");
    JsonNode paramsNode = args.get("params");

    List<Object> params = new ArrayList<>();
    if (paramsNode != null && paramsNode.isArray()) {
      paramsNode.forEach(param -> params.add(JsonUtil.convertJsonValue(param)));
    }

    int affectedRows = connectionManager.deleteData(connectionId, tableName, whereClause, params);

    Map<String, Object> response = Map.of(
      "tableName", tableName,
      "affectedRows", affectedRows
    );

    return createTextResponse(JsonUtil.formatJson(response));
  }

  @com.aversion.server.tools.ToolDefinition(name = "create_table", description = "Create a new table in the database")
  private Map<String, Object> handleCreateTable(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");
    JsonNode columnsNode = JsonUtil.getArrayField(args, "columns");

    List<Map<String, Object>> columns = new ArrayList<>();
    for (JsonNode columnNode : columnsNode) {
      columns.add(JsonUtil.getObjectMapper().convertValue(columnNode, Map.class));
    }

    connectionManager.createTable(connectionId, tableName, columns);

    return createTextResponse("Table '" + tableName + "' created successfully.");
  }

  @com.aversion.server.tools.ToolDefinition(name = "drop_table", description = "Drop an existing table from the database")
  private Map<String, Object> handleDropTable(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");

    connectionManager.dropTable(connectionId, tableName);

    return createTextResponse("Table '" + tableName + "' dropped successfully.");
  }

  @com.aversion.server.tools.ToolDefinition(name = "alter_table", description = "Alter an existing table (add or drop columns)")
  private Map<String, Object> handleAlterTable(JsonNode args) throws Exception {
    String connectionId = JsonUtil.getStringField(args, "connectionId");
    String tableName = JsonUtil.getStringField(args, "tableName");
    String action = JsonUtil.getStringField(args, "action"); // "add_column" or "drop_column"

    if ("add_column".equalsIgnoreCase(action)) {
      JsonNode columnNode = JsonUtil.getObjectField(args, "columnDefinition");
      Map<String, Object> columnDefinition = JsonUtil.getObjectMapper().convertValue(columnNode, Map.class);
      connectionManager.addColumn(connectionId, tableName, columnDefinition);
      return createTextResponse("Column added to table '" + tableName + "' successfully.");
    } else if ("drop_column".equalsIgnoreCase(action)) {
      String columnName = JsonUtil.getStringField(args, "columnName");
      connectionManager.dropColumn(connectionId, tableName, columnName);
      return createTextResponse("Column dropped from table '" + tableName + "' successfully.");
    } else {
      throw new IllegalArgumentException("Invalid alter table action: " + action);
    }
  }

  // JSON Schema creation methods

  public Map<String, Object> createConnectDatabaseSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Unique identifier for this database connection",
          "pattern", "^[a-zA-Z0-9_-]+$"
        ),
        "config", Map.of(
          "type", "object",
          "description", "Database configuration",
          "properties", Map.of(
            "type", Map.of(
              "type", "string",
              "enum", List.of("sqlite", "mysql", "postgresql"),
              "description", "Database type"
            ),
            "file", Map.of(
              "type", "string",
              "description", "SQLite database file path"
            ),
            "host", Map.of(
              "type", "string",
              "description", "Database host",
              "default", "localhost"
            ),
            "port", Map.of(
              "type", "integer",
              "description", "Database port"
            ),
            "database", Map.of(
              "type", "string",
              "description", "Database name"
            ),
            "username", Map.of(
              "type", "string",
              "description", "Database username"
            ),
            "password", Map.of(
              "type", "string",
              "description", "Database password"
            )
          ),
          "required", List.of("type")
        )
      ),
      "required", List.of("connectionId", "config")
    );
  }

  public Map<String, Object> createExecuteQuerySchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Database connection identifier"
        ),
        "query", Map.of(
          "type", "string",
          "description", "SQL query to execute",
          "minLength", 1
        ),
        "params", Map.of(
          "type", "array",
          "description", "Query parameters for prepared statements",
          "items", Map.of("type", "string")
        ),
        "limit", Map.of(
          "type", "integer",
          "description", "Maximum rows to return",
          "minimum", 1,
          "maximum", 10000,
          "default", 1000
        )
      ),
      "required", List.of("connectionId", "query")
    );
  }

  public Map<String, Object> createExecuteTransactionSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Database connection identifier"
        ),
        "queries", Map.of(
          "type", "array",
          "description", "Array of queries to execute in transaction",
          "minItems", 1,
          "maxItems", 100,
          "items", Map.of(
            "type", "object",
            "properties", Map.of(
              "query", Map.of(
                "type", "string",
                "description", "SQL query"
              ),
              "params", Map.of(
                "type", "array",
                "description", "Query parameters",
                "items", Map.of("type", "string")
              )
            ),
            "required", List.of("query")
          )
        )
      ),
      "required", List.of("connectionId", "queries")
    );
  }

  public Map<String, Object> createGetTableSchemaSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Database connection identifier"
        ),
        "tableName", Map.of(
          "type", "string",
          "description", "Name of the table to describe",
          "minLength", 1
        )
      ),
      "required", List.of("connectionId", "tableName")
    );
  }

  public Map<String, Object> createListTablesSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Database connection identifier"
        )
      ),
      "required", List.of("connectionId")
    );
  }

  public Map<String, Object> createDisconnectDatabaseSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "properties", Map.of(
        "connectionId", Map.of(
          "type", "string",
          "description", "Database connection identifier"
        )
      ),
      "required", List.of("connectionId")
    );
  }

  public Map<String, Object> createGetMetricsSchema() {
    return Map.of(
      "type", "object",
      "$schema", "http://json-schema.org/draft-07/schema#",
      "description", "Get database performance metrics"
    );
  }

  // Utility methods

  private DatabaseConfig parseDatabaseConfig(JsonNode configNode) {
    String type = JsonUtil.getStringField(configNode, "type");

    return switch (type.toLowerCase()) {
      case "sqlite" -> new DatabaseConfig.SQLiteConfig(
        JsonUtil.getStringField(configNode, "file")
      );
      case "mysql" -> new DatabaseConfig.MySQLConfig(
        JsonUtil.getStringField(configNode, "host", "localhost"),
        JsonUtil.getIntField(configNode, "port", 3306),
        JsonUtil.getStringField(configNode, "database"),
        JsonUtil.getStringField(configNode, "username"),
        JsonUtil.getStringField(configNode, "password")
      );
      case "postgresql" -> new DatabaseConfig.PostgreSQLConfig(
        JsonUtil.getStringField(configNode, "host", "localhost"),
        JsonUtil.getIntField(configNode, "port", 5432),
        JsonUtil.getStringField(configNode, "database"),
        JsonUtil.getStringField(configNode, "username"),
        JsonUtil.getStringField(configNode, "password")
      );
      default -> throw new IllegalArgumentException("Unsupported database type: " + type);
    };
  }

}
