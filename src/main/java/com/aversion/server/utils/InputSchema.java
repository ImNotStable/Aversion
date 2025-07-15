package com.aversion.server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Represents an input schema for a tool, typically loaded from a JSON file.
 * This record provides a convenient way to access and manage the schema as a map.
 */
public record InputSchema(Map<String, Object> schema) implements Map<String, Object> {

  public static InputSchema fromStream(@NotNull InputStream inputStream) {
    try {
      JsonNode node = JsonUtil.getObjectMapper().readTree(inputStream);
      Map<String, Object> schemaMap = JsonUtil.getObjectMapper().convertValue(node, Map.class);
      return new InputSchema(schemaMap);
    } catch (IOException e) {
      com.aversion.server.utils.Logger.getInstance().error("Failed to read input schema from stream", e);
      return null;
    }
  }

  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(schema);
  }

  @Override
  public int size() {
    return schema.size();
  }

  @Override
  public boolean isEmpty() {
    return schema.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return schema.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return schema.containsValue(value);
  }

  @Override
  public Object get(Object key) {
    return schema.get(key);
  }

  @Nullable
  @Override
  public Object put(String key, Object value) {
    return null;
  }

  @Override
  public Object remove(Object key) {
    return null;
  }

  @Override
  public void putAll(@NotNull Map<? extends String, ?> m) {

  }

  @Override
  public void clear() {

  }

  @NotNull
  @Override
  public Set<String> keySet() {
    return Collections.unmodifiableSet(schema.keySet());
  }

  @NotNull
  @Override
  public Collection<Object> values() {
    return Collections.unmodifiableCollection(schema.values());
  }

  @NotNull
  @Override
  public Set<Entry<String, Object>> entrySet() {
    return Collections.unmodifiableSet(schema.entrySet());
  }

}
