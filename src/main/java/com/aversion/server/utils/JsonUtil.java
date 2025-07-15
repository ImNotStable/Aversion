package com.aversion.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Utility class for JSON operations, providing methods for object mapping, JSON formatting, and extracting various field types from JsonNode objects.
 */
public class JsonUtil {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public static Object convertJsonValue(JsonNode node) {
    if (node.isNull()) return null;
    if (node.isBoolean()) return node.asBoolean();
    if (node.isInt()) return node.asInt();
    if (node.isLong()) return node.asLong();
    if (node.isDouble()) return node.asDouble();
    return node.asText();
  }

  public static String formatJson(Object obj) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (Exception e) {
      return obj.toString();
    }
  }

  public static JsonNode getObjectField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull() || !field.isObject()) {
      throw new IllegalArgumentException("Required object field '" + fieldName + "' is missing or not an object");
    }
    return field;
  }

  public static boolean getBooleanField(JsonNode node, String fieldName, boolean defaultValue) {
    JsonNode field = node.get(fieldName);
    return (field == null || field.isNull()) ? defaultValue : field.asBoolean();
  }

  public static int getIntField(JsonNode node, String fieldName, int defaultValue) {
    return ValidationUtils.getOptionalIntField(node, fieldName, defaultValue);
  }

  public static String getStringField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull()) {
      throw new IllegalArgumentException("Required field '" + fieldName + "' is missing");
    }
    return field.asText();
  }

  public static String getStringField(JsonNode node, String fieldName, String defaultValue) {
    return ValidationUtils.getOptionalStringField(node, fieldName, defaultValue);
  }

  public static JsonNode getArrayField(JsonNode node, String fieldName) {
    JsonNode field = node.get(fieldName);
    if (field == null || field.isNull() || !field.isArray()) {
      throw new IllegalArgumentException("Required array field '" + fieldName + "' is missing or not an array");
    }
    return field;
  }

}
