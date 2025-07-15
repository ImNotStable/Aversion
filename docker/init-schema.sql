-- Connect to the MCP server database
\c mcp_server;

-- Create application schemas
CREATE SCHEMA IF NOT EXISTS mcp_server;
CREATE SCHEMA IF NOT EXISTS audit;

-- Set default search path
ALTER DATABASE mcp_server SET search_path TO mcp_server, public;

-- Create basic tables for MCP server operations
CREATE TABLE IF NOT EXISTS mcp_server.connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    connection_id VARCHAR(255) UNIQUE NOT NULL,
    database_type VARCHAR(50) NOT NULL,
    configuration JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS mcp_server.query_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    connection_id VARCHAR(255) REFERENCES mcp_server.connections(connection_id),
    query_text TEXT NOT NULL,
    parameters JSONB,
    execution_time_ms INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('success', 'error')),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_connections_connection_id ON mcp_server.connections(connection_id);
CREATE INDEX IF NOT EXISTS idx_connections_database_type ON mcp_server.connections(database_type);
CREATE INDEX IF NOT EXISTS idx_query_log_connection_id ON mcp_server.query_log(connection_id);
CREATE INDEX IF NOT EXISTS idx_query_log_created_at ON mcp_server.query_log(created_at);
CREATE INDEX IF NOT EXISTS idx_query_log_status ON mcp_server.query_log(status);

-- Create audit table for tracking changes
CREATE TABLE IF NOT EXISTS audit.activity_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_name VARCHAR(255) NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    row_id UUID,
    old_values JSONB,
    new_values JSONB,
    changed_by VARCHAR(255),
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create audit trigger function
CREATE OR REPLACE FUNCTION audit.log_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO audit.activity_log (table_name, operation, row_id, old_values)
        VALUES (TG_TABLE_NAME, TG_OP, OLD.id, row_to_json(OLD));
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit.activity_log (table_name, operation, row_id, old_values, new_values)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, row_to_json(OLD), row_to_json(NEW));
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO audit.activity_log (table_name, operation, row_id, new_values)
        VALUES (TG_TABLE_NAME, TG_OP, NEW.id, row_to_json(NEW));
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Apply audit triggers to main tables
CREATE TRIGGER connections_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON mcp_server.connections
    FOR EACH ROW EXECUTE FUNCTION audit.log_changes();

-- Grant permissions to application user
GRANT USAGE ON SCHEMA mcp_server TO mcp_user;
GRANT USAGE ON SCHEMA audit TO mcp_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA mcp_server TO mcp_user;
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO mcp_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA mcp_server TO mcp_user;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA mcp_server GRANT ALL ON TABLES TO mcp_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA mcp_server GRANT ALL ON SEQUENCES TO mcp_user;
