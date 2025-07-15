package com.aversion.server.modules.database;

import com.aversion.server.utils.Logger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages database connections for the Aversion server with connection pooling.
 * <p>
 * Supports multiple database types with HikariCP connection pooling,
 * performance monitoring, and enhanced error handling.
 */
public class DatabaseConnectionManager {

  private static final com.aversion.server.utils.Logger logger = com.aversion.server.utils.Logger.getInstance();
  private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
  private final Map<String, DatabaseConfig> configurations = new ConcurrentHashMap<>();
  private final AtomicLong totalQueries = new AtomicLong(0);
  private final AtomicLong totalErrors = new AtomicLong(0);

  /**
   * Connect to a database with the given configuration using connection pooling.
   *
   * @param connectionId Unique identifier for this connection
   * @param config       Database configuration
   * @throws Exception if connection fails
   */
  public void connect(String connectionId, DatabaseConfig config) throws Exception {
    if (dataSources.containsKey(connectionId)) {
      throw new IllegalArgumentException("Connection '" + connectionId + "' already exists");
    }

    HikariConfig hikariConfig = createHikariConfig(connectionId, config);
    HikariDataSource dataSource = new HikariDataSource(hikariConfig);

    // Test the connection
    try (Connection testConnection = dataSource.getConnection()) {
      if (!testConnection.isValid(5)) {
        throw new SQLException("Database connection validation failed");
      }
    }

    dataSources.put(connectionId, dataSource);
    configurations.put(connectionId, config);

    logger.info("Database connection established",
      "connectionId", connectionId,
      "type", config.type(),
      "poolSize", hikariConfig.getMaximumPoolSize()
    );
  }

  /**
   * Inserts data into a specified table.
   *
   * @param connectionId Unique identifier for the database connection.
   * @param tableName The name of the table to insert data into.
   * @param data A map where keys are column names and values are the data to be inserted.
   * @return The number of rows affected by the insert operation.
   * @throws Exception if the insertion fails.
   */
  public int insertData(String connectionId, String tableName, Map<String, Object> data) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    if (data.isEmpty()) {
      throw new IllegalArgumentException("Data for insertion cannot be empty.");
    }

    // Build the INSERT statement
    StringBuilder columns = new StringBuilder();
    StringBuilder valuesPlaceholder = new StringBuilder();
    List<Object> params = new ArrayList<>();

    boolean first = true;
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      if (!first) {
        columns.append(", ");
        valuesPlaceholder.append(", ");
      }
      columns.append(entry.getKey());
      valuesPlaceholder.append("?");
      params.add(entry.getValue());
      first = false;
    }

    String query = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, valuesPlaceholder);

    try (Connection connection = dataSource.getConnection();
         PreparedStatement stmt = connection.prepareStatement(query)) {
      setParameters(stmt, params);
      int affectedRows = stmt.executeUpdate();
      logQuerySuccess(connectionId, query, startTime, affectedRows);
      return affectedRows;
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  /**
   * Updates data in a specified table based on a WHERE clause.
   *
   * @param connectionId Unique identifier for the database connection.
   * @param tableName The name of the table to update.
   * @param data A map where keys are column names and values are the new data.
   * @param whereClause The SQL WHERE clause to filter rows (e.g., "id = ?"). Can be empty.
   * @param params A list of parameters for the WHERE clause.
   * @return The number of rows affected by the update operation.
   * @throws Exception if the update fails.
   */
    public int updateData(String connectionId, String tableName, Map<String, Object> data, String whereClause, List<Object> params) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    if (data.isEmpty()) {
      throw new IllegalArgumentException("Data for update cannot be empty.");
    }

    // Build the UPDATE statement
    StringBuilder setClause = new StringBuilder();
    List<Object> updateParams = new ArrayList<>();

    boolean first = true;
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      if (!first) {
        setClause.append(", ");
      }
      setClause.append(entry.getKey()).append(" = ?");
      updateParams.add(entry.getValue());
      first = false;
    }

    String query = String.format("UPDATE %s SET %s %s", tableName, setClause, whereClause != null && !whereClause.isEmpty() ? "WHERE " + whereClause : "");

    // Combine update parameters and where clause parameters
    updateParams.addAll(params);

    try (Connection connection = dataSource.getConnection();
         PreparedStatement stmt = connection.prepareStatement(query)) {
      setParameters(stmt, updateParams);
      int affectedRows = stmt.executeUpdate();
      logQuerySuccess(connectionId, query, startTime, affectedRows);
      return affectedRows;
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  /**
   * Deletes data from a specified table based on a WHERE clause.
   *
   * @param connectionId Unique identifier for the database connection.
   * @param tableName The name of the table to delete from.
   * @param whereClause The SQL WHERE clause to filter rows (e.g., "id = ?"). Can be empty.
   * @param params A list of parameters for the WHERE clause.
   * @return The number of rows affected by the delete operation.
   * @throws Exception if the deletion fails.
   */
    public int deleteData(String connectionId, String tableName, String whereClause, List<Object> params) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    String query = String.format("DELETE FROM %s %s", tableName, whereClause != null && !whereClause.isEmpty() ? "WHERE " + whereClause : "");

    try (Connection connection = dataSource.getConnection();
         PreparedStatement stmt = connection.prepareStatement(query)) {
      setParameters(stmt, params);
      int affectedRows = stmt.executeUpdate();
      logQuerySuccess(connectionId, query, startTime, affectedRows);
      return affectedRows;
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  /**
   * Creates a new table in the database.
   *
   * @param connectionId Unique identifier for the database connection.
   * @param tableName The name of the table to create.
   * @param columns A list of maps, where each map defines a column (e.g., name, type, primaryKey, notNull, defaultValue).
   * @throws Exception if the table creation fails.
   */
  public void createTable(String connectionId, String tableName, List<Map<String, Object>> columns) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    if (columns.isEmpty()) {
      throw new IllegalArgumentException("Columns for table creation cannot be empty.");
    }

    StringBuilder columnDefinitions = new StringBuilder();
    boolean first = true;
    for (Map<String, Object> column : columns) {
      if (!first) {
        columnDefinitions.append(", ");
      }
      columnDefinitions.append(column.get("name")).append(" ").append(column.get("type"));
      if (column.containsKey("primaryKey") && (Boolean) column.get("primaryKey")) {
        columnDefinitions.append(" PRIMARY KEY");
      }
      if (column.containsKey("notNull") && (Boolean) column.get("notNull")) {
        columnDefinitions.append(" NOT NULL");
      }
      if (column.containsKey("defaultValue")) {
        columnDefinitions.append(" DEFAULT ").append(column.get("defaultValue"));
      }
      first = false;
    }

    String query = String.format("CREATE TABLE %s (%s)", tableName, columnDefinitions);

    try (Connection connection = dataSource.getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute(query);
      logQuerySuccess(connectionId, query, startTime, 0);
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  public void dropTable(String connectionId, String tableName) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    String query = String.format("DROP TABLE %s", tableName);

    try (Connection connection = dataSource.getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute(query);
      logQuerySuccess(connectionId, query, startTime, 0);
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  public void addColumn(String connectionId, String tableName, Map<String, Object> columnDefinition) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    String columnName = (String) columnDefinition.get("name");
    String columnType = (String) columnDefinition.get("type");
    Boolean notNull = (Boolean) columnDefinition.getOrDefault("notNull", false);
    Object defaultValue = columnDefinition.get("defaultValue");

    StringBuilder columnDef = new StringBuilder();
    columnDef.append(columnName).append(" ").append(columnType);
    if (notNull) {
      columnDef.append(" NOT NULL");
    }
    if (defaultValue != null) {
      columnDef.append(" DEFAULT ").append(defaultValue);
    }

    String query = String.format("ALTER TABLE %s ADD COLUMN %s", tableName, columnDef);

    try (Connection connection = dataSource.getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute(query);
      logQuerySuccess(connectionId, query, startTime, 0);
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  public void dropColumn(String connectionId, String tableName, String columnName) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    String query = String.format("ALTER TABLE %s DROP COLUMN %s", tableName, columnName);

    try (Connection connection = dataSource.getConnection();
         Statement stmt = connection.createStatement()) {
      stmt.execute(query);
      logQuerySuccess(connectionId, query, startTime, 0);
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  /**
   * Execute an SQL query with enhanced error handling and performance monitoring.
   *
   * @param connectionId Connection identifier
   * @param query        SQL query
   * @param params       Query parameters
   * @param limit        Maximum number of rows to return
   * @return Query result
   * @throws Exception if query execution fails
   */
  public QueryResult executeQuery(String connectionId, String query, List<Object> params, int limit) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    try (Connection connection = dataSource.getConnection()) {
      // Validate and optimize query
      validateQuery(query);

      try (PreparedStatement stmt = connection.prepareStatement(query)) {
        setParameters(stmt, params);

        if (stmt.execute()) {
          // SELECT query
          try (ResultSet rs = stmt.getResultSet()) {
            QueryResult result = buildQueryResult(rs, limit);
            logQuerySuccess(connectionId, query, startTime, result.rowCount());
            return result;
          }
        } else {
          // UPDATE/INSERT/DELETE query
          int affectedRows = stmt.getUpdateCount();
          QueryResult result = QueryResult.forUpdate(affectedRows);
          logQuerySuccess(connectionId, query, startTime, affectedRows);
          return result;
        }
      }
    } catch (Exception e) {
      totalErrors.incrementAndGet();
      logQueryError(connectionId, query, startTime, e);
      throw createDetailedException(connectionId, query, e);
    }
  }

  /**
   * Execute multiple queries in a transaction with enhanced error handling.
   *
   * @param connectionId Connection identifier
   * @param queries      List of queries with parameters
   * @return List of query results
   * @throws Exception if transaction fails
   */
  public List<QueryResult> executeTransaction(String connectionId, List<QueryWithParams> queries) throws Exception {
    long startTime = System.currentTimeMillis();
    totalQueries.incrementAndGet();

    HikariDataSource dataSource = getDataSource(connectionId);

    try (Connection connection = dataSource.getConnection()) {
      boolean autoCommit = connection.getAutoCommit();

      try {
        connection.setAutoCommit(false);
        List<QueryResult> results = new ArrayList<>();

        for (QueryWithParams queryWithParams : queries) {
          validateQuery(queryWithParams.query());

          try (PreparedStatement stmt = connection.prepareStatement(queryWithParams.query())) {
            setParameters(stmt, queryWithParams.params());

            if (stmt.execute()) {
              try (ResultSet rs = stmt.getResultSet()) {
                results.add(buildQueryResult(rs, 1000));
              }
            } else {
              results.add(QueryResult.forUpdate(stmt.getUpdateCount()));
            }
          }
        }

        connection.commit();
        logTransactionSuccess(connectionId, queries.size(), startTime);
        return results;

      } catch (Exception e) {
        connection.rollback();
        totalErrors.incrementAndGet();
        logTransactionError(connectionId, queries.size(), startTime, e);
        throw createDetailedException(connectionId, "Transaction", e);
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    }
  }

  /**
   * Get table schema information with enhanced metadata.
   *
   * @param connectionId Connection identifier
   * @param tableName    Table name
   * @return List of column information
   * @throws Exception if operation fails
   */
  public List<Map<String, Object>> getTableSchema(String connectionId, String tableName) throws Exception {
    HikariDataSource dataSource = getDataSource(connectionId);

    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();

      List<Map<String, Object>> columns = new ArrayList<>();

      try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
        while (rs.next()) {
          Map<String, Object> column = new HashMap<>();
          column.put("name", rs.getString("COLUMN_NAME"));
          column.put("type", rs.getString("TYPE_NAME"));
          column.put("size", rs.getInt("COLUMN_SIZE"));
          column.put("nullable", rs.getBoolean("NULLABLE"));
          column.put("defaultValue", rs.getString("COLUMN_DEF"));
          column.put("precision", rs.getInt("COLUMN_SIZE"));
          column.put("scale", rs.getInt("DECIMAL_DIGITS"));
          column.put("autoIncrement", rs.getBoolean("IS_AUTOINCREMENT"));
          columns.add(column);
        }
      }

      // Get primary key information
      try (ResultSet rs = metaData.getPrimaryKeys(null, null, tableName)) {
        Set<String> primaryKeys = new HashSet<>();
        while (rs.next()) {
          String columnName = rs.getString("COLUMN_NAME");
          primaryKeys.add(columnName);
        }

        // Mark primary key columns
        for (Map<String, Object> column : columns) {
          String columnName = (String) column.get("name");
          boolean isPrimaryKey = primaryKeys.contains(columnName);
          column.put("isPrimaryKey", isPrimaryKey);
        }
      }

      return columns;
    }
  }

  /**
   * List all tables in the database with additional metadata.
   *
   * @param connectionId Connection identifier
   * @return List of table names with metadata
   * @throws Exception if operation fails
   */
  public List<Map<String, Object>> listTables(String connectionId) throws Exception {
    HikariDataSource dataSource = getDataSource(connectionId);

    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();

      List<Map<String, Object>> tables = new ArrayList<>();

      try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
        while (rs.next()) {
          Map<String, Object> table = new HashMap<>();
          table.put("name", rs.getString("TABLE_NAME"));
          table.put("type", rs.getString("TABLE_TYPE"));
          table.put("schema", rs.getString("TABLE_SCHEM"));
          table.put("catalog", rs.getString("TABLE_CAT"));
          table.put("remarks", rs.getString("REMARKS"));
          tables.add(table);
        }
      }

      return tables;
    }
  }

  /**
   * Disconnect from a database and clean up resources.
   *
   * @param connectionId Connection identifier
   */
  public void disconnect(String connectionId) {
    HikariDataSource dataSource = dataSources.remove(connectionId);
    configurations.remove(connectionId);

    if (dataSource != null) {
      dataSource.close();
      logger.info("Database connection closed", "connectionId", connectionId);
    }
  }

  /**
   * Check if a connection exists.
   *
   * @param connectionId Connection identifier
   * @return true if connection exists
   */
  public boolean hasConnection(String connectionId) {
    return dataSources.containsKey(connectionId);
  }

  /**
   * Get performance metrics for the connection manager.
   *
   * @return Map containing performance metrics
   */
  public Map<String, Object> getMetrics() {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("totalQueries", totalQueries.get());
    metrics.put("totalErrors", totalErrors.get());
    metrics.put("activeConnections", dataSources.size());

    Map<String, Object> connectionMetrics = new HashMap<>();
    for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
      HikariDataSource ds = entry.getValue();
      Map<String, Object> connMetrics = new HashMap<>();
      connMetrics.put("activeConnections", ds.getHikariPoolMXBean().getActiveConnections());
      connMetrics.put("idleConnections", ds.getHikariPoolMXBean().getIdleConnections());
      connMetrics.put("totalConnections", ds.getHikariPoolMXBean().getTotalConnections());
      connectionMetrics.put(entry.getKey(), connMetrics);
    }
    metrics.put("connections", connectionMetrics);

    return metrics;
  }

  public void shutdown() {
    for (HikariDataSource dataSource : dataSources.values()) {
      dataSource.close();
    }
    dataSources.clear();
    configurations.clear();
    logger.info("DatabaseConnectionManager shutdown complete");
  }

  // Private helper methods

  private HikariDataSource getDataSource(String connectionId) throws SQLException {
    HikariDataSource dataSource = dataSources.get(connectionId);
    if (dataSource == null) {
      throw new SQLException("Connection not found: " + connectionId);
    }
    if (dataSource.isClosed()) {
      throw new SQLException("Connection pool is closed: " + connectionId);
    }
    return dataSource;
  }

  private HikariConfig createHikariConfig(String connectionId, DatabaseConfig config) {
    HikariConfig hikariConfig = new HikariConfig();

    // Set basic connection properties
    hikariConfig.setJdbcUrl(buildJdbcUrl(config));
    hikariConfig.setUsername(getUsername(config));
    hikariConfig.setPassword(getPassword(config));

    // Set connection pool properties
    hikariConfig.setPoolName("mcp-" + connectionId);
    hikariConfig.setMaximumPoolSize(10);
    hikariConfig.setMinimumIdle(2);
    hikariConfig.setConnectionTimeout(30000);
    hikariConfig.setIdleTimeout(600000);
    hikariConfig.setMaxLifetime(1800000);
    hikariConfig.setLeakDetectionThreshold(60000);

    // Set database-specific properties
    setDatabaseSpecificProperties(hikariConfig, config);

    return hikariConfig;
  }

  private String buildJdbcUrl(DatabaseConfig config) {
    return switch (config) {
      case DatabaseConfig.SQLiteConfig sqlite -> "jdbc:sqlite:" + sqlite.file();
      case DatabaseConfig.MySQLConfig mysql ->
        "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database() +
          "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
      case DatabaseConfig.PostgreSQLConfig postgres ->
        "jdbc:postgresql://" + postgres.host() + ":" + postgres.port() + "/" + postgres.database();
    };
  }

  private String getUsername(DatabaseConfig config) {
    return switch (config) {
      case DatabaseConfig.SQLiteConfig sqlite -> null;
      case DatabaseConfig.MySQLConfig mysql -> mysql.username();
      case DatabaseConfig.PostgreSQLConfig postgres -> postgres.username();
    };
  }

  private String getPassword(DatabaseConfig config) {
    return switch (config) {
      case DatabaseConfig.SQLiteConfig sqlite -> null;
      case DatabaseConfig.MySQLConfig mysql -> mysql.password();
      case DatabaseConfig.PostgreSQLConfig postgres -> postgres.password();
    };
  }

  private void setDatabaseSpecificProperties(HikariConfig config, DatabaseConfig dbConfig) {
    switch (dbConfig) {
      case DatabaseConfig.SQLiteConfig sqlite -> {
        // SQLite specific properties
        config.addDataSourceProperty("cache", "shared");
        config.addDataSourceProperty("mode", "memory");
      }
      case DatabaseConfig.MySQLConfig mysql -> {
        // MySQL specific properties
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
      }
      case DatabaseConfig.PostgreSQLConfig postgres -> {
        // PostgreSQL specific properties
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      }
    }
  }

  private void validateQuery(String query) throws IllegalArgumentException {
    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("Query cannot be null or empty");
    }

    // Basic SQL injection prevention - check for suspicious patterns
    String upperQuery = query.toUpperCase();
    if (upperQuery.contains("DROP ") || upperQuery.contains("TRUNCATE ") ||
      upperQuery.contains("ALTER ") || upperQuery.contains("CREATE ")) {
      logger.warn("Potentially dangerous SQL operation detected", "query", query);
    }
  }

  private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
    if (params == null) return;

    for (int i = 0; i < params.size(); i++) {
      Object param = params.get(i);
      switch (param) {
        case null -> stmt.setNull(i + 1, Types.NULL);
        case String s -> stmt.setString(i + 1, s);
        case Integer integer -> stmt.setInt(i + 1, integer);
        case Long l -> stmt.setLong(i + 1, l);
        case Double v -> stmt.setDouble(i + 1, v);
        case Boolean b -> stmt.setBoolean(i + 1, b);
        case java.util.Date date -> stmt.setTimestamp(i + 1, new Timestamp(date.getTime()));
        default -> stmt.setObject(i + 1, param);
      }
    }
  }

  private QueryResult buildQueryResult(ResultSet rs, int limit) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();

    List<String> columns = new ArrayList<>();
    for (int i = 1; i <= columnCount; i++) {
      columns.add(metaData.getColumnName(i));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    int rowCount = 0;

    while (rs.next() && rowCount < limit) {
      Map<String, Object> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        Object value = rs.getObject(i);
        // Handle special types
        if (value instanceof java.sql.Timestamp) {
          value = ((java.sql.Timestamp) value).toInstant().toString();
        } else if (value instanceof java.sql.Date) {
          value = ((java.sql.Date) value).toLocalDate().toString();
        }
        row.put(columns.get(i - 1), value);
      }
      rows.add(row);
      rowCount++;
    }

    return QueryResult.forSelect(columns, rows);
  }

  private void logQuerySuccess(String connectionId, String query, long startTime, int resultCount) {
    long duration = System.currentTimeMillis() - startTime;
    logger.debug("Query executed successfully",
      "connectionId", connectionId,
      "duration", duration,
      "resultCount", resultCount,
      "query", query.length() > 100 ? query.substring(0, 100) + "..." : query
    );
  }

  private void logQueryError(String connectionId, String query, long startTime, Exception error) {
    long duration = System.currentTimeMillis() - startTime;
    logger.error("Query execution failed",
      "connectionId", connectionId,
      "duration", duration,
      "error", error.getMessage(),
      "query", query.length() > 100 ? query.substring(0, 100) + "..." : query
    );
  }

  private void logTransactionSuccess(String connectionId, int queryCount, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    logger.debug("Transaction executed successfully",
      "connectionId", connectionId,
      "duration", duration,
      "queryCount", queryCount
    );
  }

  private void logTransactionError(String connectionId, int queryCount, long startTime, Exception error) {
    long duration = System.currentTimeMillis() - startTime;
    logger.error("Transaction execution failed",
      "connectionId", connectionId,
      "duration", duration,
      "queryCount", queryCount,
      "error", error.getMessage()
    );
  }

  private Exception createDetailedException(String connectionId, String operation, Exception original) {
    DatabaseConfig config = configurations.get(connectionId);
    String message = String.format("Database operation failed for %s database (connection: %s): %s",
      config != null ? config.type() : "unknown", connectionId, original.getMessage());
    return new SQLException(message, original);
  }

  /**
   * Record for holding a query with its parameters.
   */
  public record QueryWithParams(String query, List<Object> params) {
  }
}
