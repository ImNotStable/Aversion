package com.aversion.server.utils;

import com.aversion.server.modules.BaseModule;
import com.aversion.server.tools.Tool;
import com.aversion.server.tools.ToolDefinition;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Utility class for reflection operations, primarily used for discovering and managing modules and tools.
 */
public class ReflectionUtil {

  private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

  /**
   * Discovers and instantiates all modules extending {@link BaseModule} within the
   * `com.aversion.server.modules` package.
   *
   * @return A set of instantiated {@link BaseModule} objects.
   * @throws RuntimeException if any module fails to instantiate.
   */
  public static @NotNull Set<? extends BaseModule> getModules() {
    Set<Class<?>> classes = getClasses("com.aversion.server.modules");

    return classes.stream()
      .filter(BaseModule.class::isAssignableFrom)
      .map(clazz -> {
        try {
          return (BaseModule) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
          throw new RuntimeException(String.format("Failed to instantiate module \"%s\"", clazz.getSimpleName()), e);
        }
      })
      .collect(Collectors.toSet());
  }

  /**
   * Scans a given package for all `.class` files and returns them as a set of {@link Class} objects.
   *
   * @param targetPackage The package to scan (e.g., "com.aversion.server.modules").
   * @return A set of {@link Class} objects found in the package.
   */
  private static @NotNull Set<Class<?>> getClasses(String targetPackage) {
    InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(targetPackage.replaceAll("\\.", "/"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    return reader.lines()
      .filter(line -> line.endsWith(".class"))
      .map(clazz -> getClass(targetPackage, clazz))
      .collect(Collectors.toSet());
  }

  /**
   * Loads a {@link Class} object by its package and class name. Caches the loaded classes for performance.
   *
   * @param pkg The package name.
   * @param clazz The class file name (e.g., "MyClass.class").
   * @return The loaded {@link Class} object.
   * @throws RuntimeException if the class cannot be found or loaded.
   */
  private static Class<?> getClass(String pkg, String clazz) {
    String classPath = String.format("%s.%s", pkg, clazz.substring(0, clazz.lastIndexOf(".")));
    return classCache.computeIfAbsent(classPath, key -> {
      try {
        return Class.forName(key);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(String.format("Failed to load class \"%s\"", key), e);
      } catch (Exception e) {
        throw new RuntimeException(String.format("Unexpected error while loading class \"%s\"", key), e);
      }
    });
  }

  /**
   * Retrieves a list of {@link Tool} objects from a given {@link BaseModule} by scanning for methods
   * annotated with {@link ToolDefinition}.
   *
   * @param module The {@link BaseModule} instance to scan.
   * @return A list of {@link Tool} objects.
   */
  public static @NotNull List<Tool> getTools(@NotNull BaseModule module) {
    return getAnnotatedMethods(ToolDefinition.class, module.getClass())
      .map(method -> Tool.fromMethod(module, method))
      .toList();
  }

  /**
   * Retrieves a stream of methods annotated with a specific annotation from a target class.
   *
   * @param annotationClass The annotation class to search for (e.g., {@link ToolDefinition}).
   * @param targetClass The class to scan for annotated methods.
   * @return A stream of annotated {@link Method} objects.
   */
  private static Stream<Method> getAnnotatedMethods(Class<? extends Annotation> annotationClass, Class<?> targetClass) {
    return getMethods(targetClass)
      .filter(method -> method.isAnnotationPresent(annotationClass))
      .peek(method -> method.setAccessible(true));
  }

  /**
   * Retrieves a list of tool IDs (names) from a given {@link BaseModule} by scanning for methods
   * annotated with {@link ToolDefinition}.
   *
   * @param module The {@link BaseModule} instance to scan.
   * @return A list of tool names (IDs).
   */
  public static @NotNull List<String> getToolIds(@NotNull BaseModule module) {
    return getAnnotationsOfMethods(ToolDefinition.class, module.getClass())
      .map(ToolDefinition::name)
      .toList();
  }

  /**
   * Retrieves a stream of annotations of a specific type from methods within a target class.
   *
   * @param annotationClass The annotation class to retrieve.
   * @param targetClass The class to scan for annotated methods.
   * @param <T> The type of the annotation.
   * @return A stream of annotations.
   */
  private static <T extends Annotation> Stream<T> getAnnotationsOfMethods(Class<T> annotationClass, Class<?> targetClass) {
    return getMethods(targetClass)
      .filter(method -> method.isAnnotationPresent(annotationClass))
      .map(method -> method.getAnnotation(annotationClass));
  }

  /**
   * Retrieves a stream of all declared methods in a given class, making them accessible.
   *
   * @param targetClass The class to retrieve methods from.
   * @return A stream of {@link Method} objects.
   */
  private static Stream<Method> getMethods(Class<?> targetClass) {
    return Arrays.stream(targetClass.getDeclaredMethods())
      .peek(method -> method.setAccessible(true));
  }

}
