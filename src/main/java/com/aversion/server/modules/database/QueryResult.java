package com.aversion.server.modules.database;

import java.util.List;
import java.util.Map;

/**
 * Represents the result of a database query execution.
 *
 * @param rowCount     The number of rows returned by a SELECT query, or 0 for UPDATE/INSERT/DELETE.
 * @param columns      A list of column names for SELECT queries. Empty for UPDATE/INSERT/DELETE.
 * @param rows         A list of maps, where each map represents a row and contains column-value pairs for SELECT queries. Empty for UPDATE/INSERT/DELETE.
 * @param affectedRows The number of rows affected by an UPDATE/INSERT/DELETE query, or 0 for SELECT.
 */
public record QueryResult(
  int rowCount,
  List<String> columns,
  List<Map<String, Object>> rows,
  int affectedRows
) {
  /**
   * Create a query result for SELECT operations.
   */
  public static QueryResult forSelect(List<String> columns, List<Map<String, Object>> rows) {
    return new QueryResult(rows.size(), columns, rows, 0);
  }

  /**
   * Create a query result for UPDATE/INSERT/DELETE operations.
   */
  public static QueryResult forUpdate(int affectedRows) {
    return new QueryResult(0, List.of(), List.of(), affectedRows);
  }
}
