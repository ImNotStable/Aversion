package com.aversion.server.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark methods as tools that can be exposed by modules.
 * Methods annotated with {@code ToolDefinition} will be automatically discovered
 * and registered with the Aversion server.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolDefinition {

  /**
   * The unique name of the tool.
   *
   * @return The tool's name.
   */
  String name();

  /**
   * A brief description of what the tool does.
   *
   * @return The tool's description.
   */
  String description() default "";

}
