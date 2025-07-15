package com.aversion.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.aversion.server.tools.Tool;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MCP Server core functionality.
 */
class AversionServerTest {
    
    private AversionServer server;
    
    @BeforeEach
    void setUp() {
        server = new AversionServer("test-server", "1.0.0");
    }
    
    @Test
    @DisplayName("Server should have correct name and version")
    void testServerInfo() {
        assertEquals("test-server", server.getName());
        assertEquals("1.0.0", server.getVersion());
    }
    
    @Test
    @DisplayName("Server should start with no tools registered")
    void testInitialState() {
        assertTrue(server.getTools().isEmpty());
    }
    
    @Test
    @DisplayName("Server should allow tool registration")
    void testToolRegistration() {
        Tool tool = new Tool(
            "test_tool",
            "Test tool",
            new com.aversion.server.utils.InputSchema(java.util.Map.of("type", "object")),
            (args) -> java.util.Map.of("result", "success")
        );
        
        server.registerTool(tool);
        
        assertEquals(1, server.getTools().size());
        assertTrue(server.getTools().containsKey("test_tool"));
    }
    
    @Test
    @DisplayName("Server should not allow duplicate tool registration")
    void testDuplicateToolRegistration() {
        Tool tool = new Tool(
            "test_tool",
            "Test tool",
            new com.aversion.server.utils.InputSchema(java.util.Map.of("type", "object")),
            (args) -> java.util.Map.of("result", "success")
        );
        
        server.registerTool(tool);
        
        assertThrows(IllegalArgumentException.class, () -> server.registerTool(tool));
    }
}
