# Aversion MCP Server - AI Coding Instructions

## Architecture Overview

This is a **Model Context Protocol (MCP) server** with an annotation-based modular architecture built in **plain Java** (no Spring Boot). The core design allows for easy extension through the `BaseModule` abstract class pattern with reflection-based tool discovery.

### Key Components

- **`src/main/java/com/aversion/server/AversionServerApplication.java`**: Main application entry point using custom MCP server
- **`src/main/java/com/aversion/server/modules/BaseModule.java`**: Abstract base class defining the module interface and common functionality
- **`src/main/java/com/aversion/server/modules/ModuleManager.java`**: Central registry for managing module lifecycle and initialization
- **Built-in modules**: `DatabaseModule`, `FileSystemModule`, `WebModule` (see `src/main/java/com/aversion/server/modules/`)
- **Sub-module structure**: Database module uses `src/main/java/com/aversion/server/modules/database/` for config and connection management

### Module System Pattern

All modules extend `BaseModule` and tools are auto-discovered via `@ToolDefinition` annotations:

```java
public class MyModule extends BaseModule {
    
    @Override
    public ModuleConfig getConfig() {
        return new ModuleConfig("my-module", "1.0.0", "Optional description");
    }

    @ToolDefinition(name = "tool_name", description = "Tool description")
    private Map<String, Object> handleTool(JsonNode args) {
        // Tool implementation
        return createTextResponse(result);
    }
}
```

**Critical**: Tools are automatically registered via reflection scanning of `@ToolDefinition` annotations. Use Jackson for JSON processing and helper methods `createTextResponse()` / `createErrorResponse()` for consistent responses.

## Development Workflows

### Build & Run Commands
- `./gradlew build` - Build the application (creates shadow JAR)
- `./gradlew dev` - Run in development mode with ENV=development
- `./gradlew test` - Run unit tests (requires -PrunOnCI property)
- `./gradlew testWithCoverage` - Run tests with coverage
- `./gradlew buildAndRun` - Build and run the application
- `./gradlew dockerBuild` - Build Docker image

### Testing Strategy
- **Unit tests**: Individual module testing with JUnit 5 and Mockito
- **Integration tests**: Full application testing with temporary databases
- Test files follow the pattern: `src/test/java/**/*Test.java`
- Use JUnit 5 with `@TempDir` for filesystem testing
- Mock pattern: `@Mock` for regular classes, manual mocking for complex scenarios

### Java Configuration
- **Java 21** with modern Java features
- **Gradle** for dependency management
- Reflection-based tool discovery via `ReflectionUtil.getTools()`
- Configuration properties via `application.yml` and environment variables

## Project-Specific Conventions

### Module Registration Pattern
Modules are automatically discovered via reflection scanning:

```java
public class ModuleManager {
    public void registerKnownModules() {
        Set<? extends BaseModule> knownModules = ReflectionUtil.getModules();
        registerModules(knownModules.toArray(BaseModule[]::new));
    }
}
```

### Database Module Design
The `DatabaseModule` supports multiple database types (SQLite, MySQL, PostgreSQL) through direct JDBC and HikariCP connection pooling. Connection management uses custom `DatabaseConnectionManager` with connection IDs for multi-database support.

### Schema-First Development Pattern
Tool schemas are defined in separate JSON files under `src/main/resources/tools/`:
```
src/main/resources/tools/
├── database/
│   ├── connect_database.json
│   ├── execute_query.json
│   └── ...
├── filesystem/
│   ├── read_file.json
│   ├── write_file.json
│   └── ...
```

### Error Handling Convention
- All tool handlers wrapped with automatic validation and error handling in `BaseModule`
- Database operations include detailed error context using SQLException hierarchy
- Use `createErrorResponse(error.getMessage())` for consistent error messages
- JSON Schema validation errors are automatically formatted with field paths and messages

### Tool Registration Pattern
```java
// Tools are auto-discovered via annotation scanning
@ToolDefinition(name = "tool_name", description = "Human-readable description")
private Map<String, Object> handleTool(JsonNode args) {
    // JSON schema loaded from resources/tools/{moduleId}/tool_name.json
    // Input validation handled automatically
    return createTextResponse(result);
}
```

### File System Operations
The `FileSystemModule` uses Java NIO.2 (`java.nio.file.Path`) for modern file operations and provides secure path resolution with proper validation. Includes batch operations for bulk file manipulation.

## Integration Points

### MCP Server Integration
- Custom Java MCP implementation with JSON-RPC protocol
- Server transport via `StdioServerTransport` for command-line integration
- Graceful shutdown handling with CompletableFuture-based async startup

### External Dependencies
- **Database**: Direct JDBC with HikariCP connection pooling
- **Web client**: OkHttp for HTTP operations
- **Validation**: NetworkNT JSON Schema Validator for input validation
- **Testing**: JUnit 5 with AssertJ and Mockito

### Cross-Module Communication
Modules are plain Java objects managed by `ModuleManager`. No dependency injection - modules communicate via direct method calls on the server instance.

## Key Development Notes

- **Module initialization order**: Handled manually in `ModuleManager.registerKnownModules()`
- **Schema validation**: Uses NetworkNT JSON Schema Validator with schemas loaded from resources
- **Response format is standardized**: Always return `Map<String, Object>` with `content` array containing `type: "text"` and `text: "content"`
- **Path resolution**: Use `java.nio.file.Path` for all file system operations
- **Connection management**: Database connections are managed via custom `DatabaseConnectionManager`

## Testing Patterns

Test modules using plain JUnit 5 with manual setup:

```java
class MyModuleTest {
    private MyModule module;
    private AversionServer testServer;
    
    @BeforeEach
    void setUp() {
        testServer = new AversionServer("test-server", "1.0.0");
        module = new MyModule();
        module.initialize(testServer);
    }
    
    @Test
    void shouldRegisterTools() {
        assertThat(module.getTools()).hasSize(expectedToolCount);
    }
}
```

For database testing, use `@TempDir` with SQLite in-memory databases and direct JDBC operations.
