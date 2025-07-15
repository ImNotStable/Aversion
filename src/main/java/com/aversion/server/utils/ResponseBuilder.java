package com.aversion.server.utils;

import java.util.List;
import java.util.Map;

/**
 * Utility class for building standardized responses for the Aversion server.
 * This class provides methods to create various types of responses, including
 * text, error, JSON, success, database operation, validation error, and warning responses,
 * ensuring consistent formatting and structure.
 */
public final class ResponseBuilder {

  private static final String CONTENT_KEY = "content";
  private static final String IS_ERROR_KEY = "isError";
  private static final String TYPE_KEY = "type";
  private static final String TEXT_KEY = "text";
  private static final String TEXT_TYPE = "text";
  private static final String ERROR_PREFIX = "Error: ";

  private ResponseBuilder() {
    // Utility class should not be instantiated
  }

  /**
   * Creates a successful text response.
   *
   * @param text The text content to include in the response
   * @return A properly formatted success response map
   */
  public static Map<String, Object> createTextResponse(String text) {
    return Map.of(
      CONTENT_KEY, List.of(
        Map.of(
          TYPE_KEY, TEXT_TYPE,
          TEXT_KEY, text
        )
      ),
      IS_ERROR_KEY, false
    );
  }

  /**
   * Creates an error response with a formatted error message.
   *
   * @param errorMessage The error message to include in the response
   * @return A properly formatted error response map
   */
  public static Map<String, Object> createErrorResponse(String errorMessage) {
    return Map.of(
      CONTENT_KEY, List.of(
        Map.of(
          TYPE_KEY, TEXT_TYPE,
          TEXT_KEY, ERROR_PREFIX + errorMessage
        )
      ),
      IS_ERROR_KEY, true
    );
  }

  /**
   * Creates a response with formatted JSON content.
   *
   * @param jsonContent The JSON content as a formatted string
   * @return A properly formatted JSON response map
   */
  public static Map<String, Object> createJsonResponse(String jsonContent) {
    return Map.of(
      CONTENT_KEY, List.of(
        Map.of(
          TYPE_KEY, TEXT_TYPE,
          TEXT_KEY, jsonContent
        )
      ),
      IS_ERROR_KEY, false
    );
  }

  /**
   * Creates a response indicating successful completion of an operation.
   *
   * @param operationName The name of the completed operation
   * @param details       Additional details about the operation
   * @return A properly formatted success response map
   */
  public static Map<String, Object> createSuccessResponse(String operationName, String details) {
    String message = String.format("Successfully completed %s: %s", operationName, details);
    return createTextResponse(message);
  }

  /**
   * Creates a response for database operations with metrics.
   *
   * @param operation     The database operation performed
   * @param rowsAffected  The number of rows affected
   * @param executionTime The execution time in milliseconds
   * @return A properly formatted database response map
   */
  public static Map<String, Object> createDatabaseResponse(
    String operation,
    int rowsAffected,
    long executionTime
  ) {
    String message = String.format(
      "%s completed successfully. Rows affected: %d, Execution time: %dms",
      operation, rowsAffected, executionTime
    );
    return createTextResponse(message);
  }

  /**
   * Creates a response for validation errors with field-specific information.
   *
   * @param fieldName The name of the field that failed validation
   * @param reason    The reason for the validation failure
   * @return A properly formatted validation error response map
   */
  public static Map<String, Object> createValidationErrorResponse(String fieldName, String reason) {
    String message = String.format("Validation failed for field '%s': %s", fieldName, reason);
    return createErrorResponse(message);
  }

  /**
   * Creates a response for operations that completed with warnings.
   *
   * @param operation The operation that completed
   * @param warnings  List of warning messages
   * @return A properly formatted warning response map
   */
  public static Map<String, Object> createWarningResponse(String operation, List<String> warnings) {
    StringBuilder message = new StringBuilder();
    message.append(operation).append(" completed with warnings:\n");
    for (String warning : warnings) {
      message.append("- ").append(warning).append("\n");
    }
    return createTextResponse(message.toString().trim());
  }
}
