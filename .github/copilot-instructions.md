# MCP Server Boilerplate - AI Coding Instructions

## Architecture Overview

This is a **Model Context Protocol (MCP) server** with an inheritance-based modular architecture built in Java with Spring Boot. The core design allows for easy extension through the `BaseModule` abstract class pattern.

### Key Components

- **`src/main/java/com/aversion/Application.java`**: Main Spring Boot application entry point
- **`src/main/java/com/aversion/modules/BaseModule.java`**: Abstract base class defining the module interface and common functionality
- **`src/main/java/com/aversion/modules/ModuleManager.java`**: Central registry for managing module lifecycle and initialization
- **Built-in modules**: `DatabaseModule`, `FileSystemModule`, `WebModule` (see `src/main/java/com/aversion/modules/`)
- **Sub-module structure**: Database module uses `src/main/java/com/aversion/modules/database/` for config and connection management

### Module System Pattern

All modules extend `BaseModule` and follow this structure:

```java
@Component
public class MyModule extends BaseModule {
    
    @Override
    protected ModuleConfig getConfig() {
        return ModuleConfig.builder()
            .name("my-module")
            .version("1.0.0")
            .description("Optional description")
            .build();
    }

    @Override
    protected void registerTools() {
        registerTool("tool_name", "description", schema, this::handleTool);
    }
    
    private Map<String, Object> handleTool(JsonNode args) {
        // Tool implementation
        return createTextResponse(result);
    }
}
```

**Critical**: Tools must be registered in `registerTools()` which is called during initialization. Use Jackson for JSON processing and validation. Use helper methods `createTextResponse()` / `createErrorResponse()` for consistent responses.

## Development Workflows

### Build & Run Commands
- `./gradlew build` - Build the application
- `./gradlew bootRun` - Run the Spring Boot application  
- `./gradlew test` - Run unit tests
- `./gradlew test jacocoTestReport` - Run tests with coverage
- `./gradlew check` - Run all checks including tests and quality gates

### Testing Strategy
- **Unit tests**: Individual module testing with Spring Boot test slices
- **Integration tests**: Full application testing with `@SpringBootTest`
- Test files follow the pattern: `src/test/java/**/*Test.java`
- Use JUnit 5 with the Spring Boot Test framework
- Mock pattern: `@MockBean` for Spring components, `@Mock` for regular classes

### Java Configuration
- **Java 17+** with Spring Boot 3.x
- **Maven/Gradle** for dependency management
- Component scanning for automatic module discovery
- Configuration properties via `application.yml`

## Project-Specific Conventions

### Module Registration Pattern
Modules are automatically discovered via Spring's component scanning:

```java
@SpringBootApplication
@ComponentScan(basePackages = "com.aversion.modules")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Database Module Design
The `DatabaseModule` supports multiple database types (H2, PostgreSQL, MySQL) through Spring Data JPA and HikariCP connection pooling. Connection management uses Spring's DataSource abstraction with connection IDs for multi-database support.

### Schema-First Development Pattern
All modules use **Jackson JsonSchema** for input validation:
```java
private JsonNode createSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    // Define properties...
    return schema;
}

registerTool("connect_database", "description", createSchema(), this::connectDatabase);
```

### Error Handling Convention
- All tool handlers wrapped with Spring's `@ControllerAdvice` for global exception handling
- Database operations include detailed error context using Spring's DataAccessException hierarchy
- Use `formatError(operation, error)` helper for consistent error messages
- Jackson validation errors are automatically formatted with field paths and messages

### Tool Registration Pattern
```java
// In registerTools() method of any module
registerTool(
    "tool_name",
    "Human-readable description", 
    createJsonSchema(), // JSON schema object
    args -> {
        // Validate args using Jackson
        // Tool logic here
        return createTextResponse(result);
    }
);
```

### File System Operations
The `FileSystemModule` uses Java NIO.2 (`java.nio.file.Path`) for modern file operations and provides secure path resolution with proper validation.

## Integration Points

### MCP Server Integration
- Custom Java MCP implementation with JSON-RPC protocol
- Server transport via standard input/output for command-line integration
- Graceful shutdown handling with Spring Boot's shutdown hooks

### External Dependencies
- **Database**: Spring Data JPA with HikariCP connection pooling
- **Web client**: Spring WebFlux WebClient for HTTP operations
- **Validation**: Jackson for JSON processing and validation
- **Testing**: JUnit 5 with the Spring Boot Test framework

### Cross-Module Communication
Modules are Spring components and can be autowired together. The `ModuleManager` provides module discovery via dependency injection for inter-module communication.

## Key Development Notes

- **Module initialization order**: Spring handles initialization order via `@DependsOn` if needed
- **Schema validation**: Use Jackson JsonNode for flexible schema validation
- **Response format is standardized**: Always return `Map<String, Object>` with `content` array
- **Path resolution**: Use `java.nio.file.Path` for all file system operations
- **Connection management**: Database connections are managed via Spring's DataSource abstraction

## Testing Patterns

Test modules using Spring Boot's testing framework:

```java
@SpringBootTest
class MyModuleTest {
    
    @Autowired
    private MyModule module;
    
    @Test
    void shouldRegisterTools() {
        assertThat(module.getTools()).hasSize(expectedToolCount);
    }
}
```

For database testing, use `@DataJpaTest` or `@TestPropertySource` with the H2 in-memory database and `@Transactional` for rollback.
