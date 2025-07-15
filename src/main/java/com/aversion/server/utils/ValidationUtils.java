package com.aversion.server.utils;

import com.fasterxml.jackson.databind.JsonNode;


/**
 * Utility class for validating input parameters.
 *
 * <p>Provides consistent validation logic across all modules
 * with clear error messages and proper exception handling.
 */

public class ValidationUtils {

  private static final String MISSING_FIELD_MESSAGE = "Required field '%s' is missing";
  private static final String INVALID_TYPE_MESSAGE = "Required field '%s' is not %s";

  /**
   * Extracts a required string field from a JsonNode.
   *
   * @param node      The JsonNode to extract from.
   * @param fieldName The name of the field to extract.
   * @return The string value of the field.
   * @throws IllegalArgumentException if field is missing or not a string.
   */
  public static String getRequiredStringField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull()) {
      throw new IllegalArgumentException(String.format(MISSING_FIELD_MESSAGE, fieldName));
    }
    return field.asText();
  }

  /**
   * Extracts an optional string field with a default value.
   *
   * @param node         The JsonNode to extract from
   * @param fieldName    The name of the field to extract
   * @param defaultValue The default value if field is missing
   * @return The string value of the field or default
   */
  public static String getOptionalStringField(JsonNode node, String fieldName, String defaultValue) {
    JsonNode field = node.get(fieldName);
    return (field == null || field.isNull()) ? defaultValue : field.asText();
  }

  /**
   * Extracts an optional integer field with a default value.
   *
   * @param node         The JsonNode to extract from
   * @param fieldName    The name of the field to extract
   * @param defaultValue The default value if field is missing
   * @return The integer value of the field or default
   */
  public static int getOptionalIntField(JsonNode node, String fieldName, int defaultValue) {
    JsonNode field = node.get(fieldName);
    return (field == null || field.isNull()) ? defaultValue : field.asInt();
  }

  /**
   * Extracts a required object field from a JsonNode.
   *
   * @param node      The JsonNode to extract from
   * @param fieldName The name of the field to extract
   * @return The JsonNode object
   * @throws IllegalArgumentException if field is missing or not an object
   */
  public static JsonNode getRequiredObjectField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull() || !field.isObject()) {
      throw new IllegalArgumentException(
        String.format(INVALID_TYPE_MESSAGE, fieldName, "an object"));
    }
    return field;
  }

  /**
   * Extracts a required array field from a JsonNode.
   *
   * @param node      The JsonNode to extract from
   * @param fieldName The name of the field to extract
   * @return The JsonNode array
   * @throws IllegalArgumentException if field is missing or not an array
   */
  public static JsonNode getRequiredArrayField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull() || !field.isArray()) {
      throw new IllegalArgumentException(
        String.format(INVALID_TYPE_MESSAGE, fieldName, "an array"));
    }
    return field;
  }

  /**
   * Validates that a string is not null or empty.
   *
   * @param value     The string to validate
   * @param fieldName The name of the field being validated
   * @throws IllegalArgumentException if string is null or empty
   */
  public static void validateNonEmptyString(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(
        String.format("Field '%s' cannot be null or empty", fieldName));
    }
  }

  /**
   * Validates that a number is within the specified range.
   *
   * @param value     The number to validate
   * @param min       The minimum allowed value (inclusive)
   * @param max       The maximum allowed value (inclusive)
   * @param fieldName The name of the field being validated
   * @throws IllegalArgumentException if number is outside the range
   */
  public static void validateRange(int value, int min, int max, String fieldName) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
        String.format("Field '%s' must be between %d and %d, got %d",
          fieldName, min, max, value));
    }
  }

}
