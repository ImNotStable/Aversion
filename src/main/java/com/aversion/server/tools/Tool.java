package com.aversion.server.tools;

import com.aversion.server.modules.BaseModule;
import com.aversion.server.utils.InputSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

/**
 * Represents a tool that can be exposed by a module. Each tool has a name, description, an input schema,
 * and a handler function that executes the tool's logic.
 *
 * @param name        The unique name of the tool.
 * @param description A brief description of what the tool does.
 * @param inputSchema The schema defining the expected input arguments for the tool.
 * @param handler     The functional interface that contains the logic to execute the tool.
 */
public record Tool(String name, String description, InputSchema inputSchema, ToolHandler handler) {

  private static void verifyTool(Method method) {
    if (!method.isAnnotationPresent(ToolDefinition.class))
      throw new IllegalArgumentException("Field " + method.getName() + " is not a valid tool");

    if (!method.getReturnType().equals(Map.class))
      throw new IllegalArgumentException("Tool method " + method.getName() + " must return Map<String, Object>");

    Type genericReturnType = method.getGenericReturnType();
    if (!(genericReturnType instanceof ParameterizedType type))
      throw new IllegalArgumentException("Tool method " + method.getName() + " must return Map<String, Object>");

    Type[] typeArguments = type.getActualTypeArguments();
    if (typeArguments.length != 2 || !typeArguments[0].equals(String.class) || !typeArguments[1].equals(Object.class))
      throw new IllegalArgumentException("Tool method " + method.getName() + " must return Map<String, Object>");

    if (!Arrays.equals(method.getParameterTypes(), new Class<?>[]{JsonNode.class}))
      throw new IllegalArgumentException("Tool method " + method.getName() + " must accept a single JsonNode parameter");
  }

  public static Tool fromMethod(BaseModule module, @NotNull Method method) {
    verifyTool(method);

    ToolDefinition definition = method.getAnnotation(ToolDefinition.class);
    String name = definition.name();
    String description = definition.description();

    String schemaPath = "tools/%s/%s.json".formatted(module.getId(), name);
    InputStream inputStream = module.getClass().getResourceAsStream("/" + schemaPath);

    if (inputStream == null)
      throw new IllegalArgumentException("Input schema file not found for tool \"" + name + "\"");

    InputSchema inputSchema = InputSchema.fromStream(inputStream);

    ToolHandler handler = jsonNode -> {
      try {
        return (Map<String, Object>) method.invoke(module, jsonNode);
      } catch (Throwable e) {
        throw new RuntimeException("Failed to execute tool method", e);
      }
    };

    return new Tool(name, description, inputSchema, handler);
  }

  @FunctionalInterface
  public interface ToolHandler {
    Map<String, Object> handle(JsonNode arguments) throws Exception;
  }

}
