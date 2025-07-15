package com.aversion.server.modules;

import com.aversion.server.AversionServer;
import com.aversion.server.modules.database.DatabaseModule;
import com.aversion.server.tools.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the DatabaseModule.
 * <p>
 * Tests all database operations including connection pooling,
 * query execution, transactions, and performance monitoring.
 */
class DatabaseModuleTest {

  private DatabaseModule module;
  private ObjectMapper objectMapper;
  private AversionServer testServer;

  @BeforeEach
  void setUp() {
    testServer = new AversionServer("test-server", "1.0.0");
    module = new DatabaseModule();
    objectMapper = new ObjectMapper();

    // Initialize the module with the test server
    module.initialize(testServer);
  }

  @AfterEach
  void tearDown() {
    module.getConnectionManager().shutdown();
  }

  private JsonNode createConnectArgs(String connectionId, Map<String, Object> config) {
    JsonNode configNode = objectMapper.valueToTree(config);
    return objectMapper.createObjectNode()
      .put("connectionId", connectionId)
      .set("config", configNode);
  }

  private void setupTestDatabase(Path dbFile) throws Exception {
    // Connect to database
    JsonNode connectArgs = createConnectArgs("test-conn", Map.of(
      "type", "sqlite",
      "file", dbFile.toString()
    ));
    executeToolDirectly("connect_database", connectArgs);

    // Create test table
    JsonNode createTableArgs = objectMapper.createObjectNode()
      .put("connectionId", "test-conn")
      .put("query", "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
    executeToolDirectly("execute_query", createTableArgs);

    // Insert test data
    ObjectNode insertArgs = objectMapper.createObjectNode()
      .put("connectionId", "test-conn")
      .put("query", "INSERT INTO users (id, name, email) VALUES (?, ?, ?)");
    JsonNode params = objectMapper.createArrayNode().add("1").add("Test User").add("test@example.com");
    insertArgs.set("params", params);
    executeToolDirectly("execute_query", insertArgs);
  }

  private void setupTestDatabaseWithManyRows(Path dbFile) throws Exception {
    setupTestDatabase(dbFile);

    // Insert many rows for limit testing
    for (int i = 2; i <= 20; i++) {
      ObjectNode insertArgs = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("query", "INSERT INTO users (id, name, email) VALUES (?, ?, ?)");
      JsonNode params = objectMapper.createArrayNode()
        .add(String.valueOf(i))
        .add("User " + i)
        .add("user" + i + "@example.com");
      insertArgs.set("params", params);
      executeToolDirectly("execute_query", insertArgs);
    }
  }

  private void executeTestQuery(String connectionId, String query) throws Exception {
    JsonNode args = objectMapper.createObjectNode()
      .put("connectionId", connectionId)
      .put("query", query);
    executeToolDirectly("execute_query", args);
  }

  private String extractTextContent(Map<String, Object> result) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    return (String) content.getFirst().get("text");
  }

  private Map<String, Object> extractDataContent(Map<String, Object> result) throws Exception {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    String jsonString = (String) content.getFirst().get("text");
    return objectMapper.readValue(jsonString, Map.class);
  }

  // Helper method to execute tools properly
  private Map<String, Object> executeToolDirectly(String toolName, JsonNode args) throws Exception {
    Tool tool = testServer.getTools().get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not found: " + toolName);
    }
    return tool.handler().handle(args);
  }

  // Helper methods

  @Nested
  class ConnectionTests {

    @Test
    void shouldConnectToSQLiteDatabase(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      JsonNode args = createConnectArgs("sqlite-test", Map.of(
        "type", "sqlite",
        "file", dbFile.toString()
      ));

      // When
      Map<String, Object> result = executeToolDirectly("connect_database", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      String content = extractTextContent(result);
      assertTrue(content.contains("Successfully connected to sqlite database: sqlite-test"));
    }

    @Test
    void shouldPreventDuplicateConnections(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      JsonNode args = createConnectArgs("duplicate-test", Map.of(
        "type", "sqlite",
        "file", dbFile.toString()
      ));

      // When - connect first time
      executeToolDirectly("connect_database", args);

      // Then - second connection should fail
      RuntimeException exception = assertThrows(RuntimeException.class, () -> executeToolDirectly("connect_database", args));
      assertInstanceOf(IllegalArgumentException.class, exception.getCause());
      assertTrue(exception.getCause().getMessage().contains("already exists"));
    }

    @Test
    void shouldValidateConnectionIdPattern() throws Exception {
      // Given
      JsonNode args = createConnectArgs("invalid@connection", Map.of(
        "type", "sqlite",
        "file", "test.db"
      ));

      // When
      RuntimeException exception = assertThrows(RuntimeException.class, () -> executeToolDirectly("connect_database", args));
      assertInstanceOf(IllegalArgumentException.class, exception.getCause());
      assertTrue(exception.getCause().getMessage().contains("does not match the regex pattern"));
    }
  }

  @Nested
  class QueryExecutionTests {

    @Test
    void shouldExecuteSelectQuery(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      ObjectNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("query", "SELECT * FROM users WHERE id = ?")
        .put("limit", 10);

      JsonNode paramsNode = objectMapper.createArrayNode().add("1");
      args.set("params", paramsNode);

      // When
      Map<String, Object> result = executeToolDirectly("execute_query", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertEquals(1, content.get("rowCount"));
      assertTrue(content.get("columns").toString().contains("id"));
      assertTrue(content.get("columns").toString().contains("name"));
      assertTrue(content.get("columns").toString().contains("email"));
    }

    @Test
    void shouldExecuteInsertQuery(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      ObjectNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("query", "INSERT INTO users (name, email) VALUES (?, ?)");

      JsonNode paramsNode = objectMapper.createArrayNode().add("John Doe").add("john@example.com");
      args.set("params", paramsNode);

      // When
      Map<String, Object> result = executeToolDirectly("execute_query", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertEquals(1, content.get("affectedRows"));
    }

    @Test
    void shouldHandleQueryWithLimit(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabaseWithManyRows(dbFile);

      JsonNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("query", "SELECT * FROM users")
        .put("limit", 5);

      // When
      Map<String, Object> result = executeToolDirectly("execute_query", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertEquals(5, content.get("rowCount"));
    }

    @Test
    void shouldValidateQueryParameters() throws Exception {
      // Given
      JsonNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("query", ""); // Empty query

      // When
      Map<String, Object> result = executeToolDirectly("execute_query", args);

      // Then
      assertTrue((Boolean) result.get("isError"));
      String content = extractTextContent(result);
      assertTrue(content.contains("Input validation failed: $.query: must have a minimum length of 1"));
    }
  }

  @Nested
  class TransactionTests {

    @Test
    void shouldExecuteTransaction(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      ObjectNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn");

      ArrayNode queriesNode = objectMapper.createArrayNode();

      // First query
      ObjectNode query1 = objectMapper.createObjectNode()
        .put("query", "INSERT INTO users (name, email) VALUES (?, ?)");
      JsonNode params1 = objectMapper.createArrayNode().add("Alice").add("alice@example.com");
      query1.set("params", params1);
      queriesNode.add(query1);

      // Second query
      ObjectNode query2 = objectMapper.createObjectNode()
        .put("query", "INSERT INTO users (name, email) VALUES (?, ?)");
      JsonNode params2 = objectMapper.createArrayNode().add("Bob").add("bob@example.com");
      query2.set("params", params2);
      queriesNode.add(query2);

      args.set("queries", queriesNode);

      // When
      Map<String, Object> result = executeToolDirectly("execute_transaction", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      String content = extractTextContent(result);
      assertTrue(content.contains("\"transactionComplete\":true"));
      assertTrue(content.contains("\"queryCount\":2"));
    }

    @Test
    void shouldRollbackTransactionOnError(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      ObjectNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn");

      ArrayNode queriesNode = objectMapper.createArrayNode();

      // Valid query
      ObjectNode query1 = objectMapper.createObjectNode()
        .put("query", "INSERT INTO users (name, email) VALUES (?, ?)");
      JsonNode params1 = objectMapper.createArrayNode().add("Alice").add("alice@example.com");
      query1.set("params", params1);
      queriesNode.add(query1);

      // Invalid query that will cause rollback
      ObjectNode query2 = objectMapper.createObjectNode()
        .put("query", "INSERT INTO nonexistent_table (name) VALUES (?)");
      JsonNode params2 = objectMapper.createArrayNode().add("Bob");
      query2.set("params", params2);
      queriesNode.add(query2);

      args.set("queries", queriesNode);

      // When
      Map<String, Object> result = executeToolDirectly("execute_transaction", args);

      // Then
      RuntimeException exception = assertThrows(RuntimeException.class, () -> executeToolDirectly("execute_transaction", args));
      assertInstanceOf(SQLException.class, exception.getCause());
      assertTrue(exception.getCause().getMessage().contains("Database operation failed"));
    }
  }

  @Nested
  class SchemaTests {

    @Test
    void shouldGetTableSchema(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      JsonNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn")
        .put("tableName", "users");

      // When
      Map<String, Object> result = executeToolDirectly("get_table_schema", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertEquals("users", content.get("tableName"));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> columns = (List<Map<String, Object>>) content.get("columns");
      assertTrue(columns.stream().anyMatch(col -> "id".equals(col.get("name"))));
      assertTrue(columns.stream().anyMatch(col -> "name".equals(col.get("name"))));
      assertTrue(columns.stream().anyMatch(col -> "email".equals(col.get("name"))));
      assertTrue(columns.stream().anyMatch(col -> "id".equals(col.get("name")) && (Boolean) col.get("isPrimaryKey")));
    }

    @Test
    void shouldListTables(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      JsonNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn");

      // When
      Map<String, Object> result = executeToolDirectly("list_tables", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertEquals(1, content.get("tableCount"));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> tables = (List<Map<String, Object>>) content.get("tables");
      assertEquals("users", tables.getFirst().get("name"));
      assertEquals("TABLE", tables.getFirst().get("type"));
    }
  }

  @Nested
  class MetricsTests {

    @Test
    void shouldGetDatabaseMetrics(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      // Execute some queries to generate metrics
      executeTestQuery("test-conn", "SELECT * FROM users");
      executeTestQuery("test-conn", "SELECT COUNT(*) FROM users");

      JsonNode args = objectMapper.createObjectNode();

      // When
      Map<String, Object> result = executeToolDirectly("get_database_metrics", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      Map<String, Object> content = extractDataContent(result);
      assertTrue(content.containsKey("totalQueries"));
      assertTrue(content.containsKey("activeConnections"));
      assertTrue(content.containsKey("connections"));
    }
  }

  @Nested
  class DisconnectionTests {

    @Test
    void shouldDisconnectDatabase(@TempDir Path tempDir) throws Exception {
      // Given
      Path dbFile = tempDir.resolve("test.db");
      setupTestDatabase(dbFile);

      JsonNode args = objectMapper.createObjectNode()
        .put("connectionId", "test-conn");

      // When
      Map<String, Object> result = executeToolDirectly("disconnect_database", args);

      // Then
      assertFalse((Boolean) result.get("isError"));
      String content = extractTextContent(result);
      assertTrue(content.contains("Successfully disconnected from database: test-conn"));
    }
  }

  @Nested
  class SchemaValidationTests {

    @Test
    void shouldValidateConnectDatabaseSchema() {
      // Given
      Map<String, Object> schema = module.getTools().get("connect_database").inputSchema();

      // Then
      assertEquals("object", schema.get("type"));
      assertTrue(schema.containsKey("properties"));
      assertTrue(schema.containsKey("required"));

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      assertTrue(properties.containsKey("connectionId"));
      assertTrue(properties.containsKey("config"));
    }

    @Test
    void shouldValidateExecuteQuerySchema() {
      // Given
      Map<String, Object> schema = module.getTools().get("execute_query").inputSchema();

      // Then
      assertEquals("object", schema.get("type"));
      assertTrue(schema.containsKey("properties"));
      assertTrue(schema.containsKey("required"));

      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      assertTrue(properties.containsKey("connectionId"));
      assertTrue(properties.containsKey("query"));
      assertTrue(properties.containsKey("params"));
      assertTrue(properties.containsKey("limit"));
    }
  }
}