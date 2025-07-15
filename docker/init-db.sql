-- Initialize MCP Server Database
-- This script runs when the PostgreSQL container starts for the first time

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create application user and database
CREATE USER mcp_user WITH PASSWORD 'changeme';
CREATE DATABASE mcp_server OWNER mcp_user;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE mcp_server TO mcp_user;
ALTER USER mcp_user CREATEDB;
