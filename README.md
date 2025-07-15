# Aversion MCP Server

## Overview

Aversion MCP Server is a lightweight, extensible server designed to provide a robust set of tools for AI agents. It acts as a bridge, allowing AI models to interact with various functionalities (like file system operations, web browsing, and database management) through a well-defined API. This project is ideal for developers looking to integrate custom tools into their AI workflows or experiment with AI agent capabilities.

## Features

- **Modular Architecture:** Easily extend functionality by adding new modules.
- **Tool Definition:** Define custom tools with JSON schemas for clear AI interaction.
- **Database Integration:** Tools for connecting to, querying, and managing SQL databases (SQLite, MySQL, PostgreSQL).
- **Web Interaction:** Tools for fetching web page content and performing basic web searches.
- **File System Access:** Securely interact with the local file system.
- **Containerized Deployment:** Ready for deployment with Docker and Docker Compose.
- **Environment Variable Configuration:** Easily manage settings via `.env` files.

## Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 21 or later:** [Download from Oracle](https://www.oracle.com/java/technologies/downloads/) or [Adoptium](https://adoptium.net/)
- **Gradle:** (Optional, `gradlew` wrapper is included)
- **Docker & Docker Compose:** [Install Docker Desktop](https://www.docker.com/products/docker-desktop/)

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/Aversion.git # Replace with your repository URL
cd Aversion
```

### 2. Configure Environment Variables

Copy the example environment file and customize it with your settings. This file will be used by Docker Compose.

```bash
cp .env.example .env
```

Open the newly created `.env` file in your text editor and adjust the values as needed. Pay special attention to database credentials and any API keys you might want to add for future integrations.

### 3. Run with Docker Compose (Recommended for full environment)

This will build the Docker image for the MCP server and start all dependent services (PostgreSQL, Redis).

```bash
docker compose up --build -d
```

To stop the services:

```bash
docker compose down
```

### 4. Run the Java Application Directly (for development)

If you prefer to run the Java application without Docker Compose (e.g., for faster development cycles), you can build and run it directly.

```bash
./gradlew build
java -jar build/libs/mcp-server-1.0.0.jar # Adjust version if different
```

**Note:** When running directly, you'll need to ensure any external dependencies (like databases or Redis) are running and accessible, and their connection details are configured in `src/main/resources/application.yml` or via system environment variables.

## Project Structure

- `src/main/java/com/aversion/server/`: Core server logic.
- `src/main/java/com/aversion/server/modules/`: Contains modular functionalities (e.g., `database`, `web`, `filesystem`).
- `src/main/resources/tools/`: JSON schema definitions for AI tools.
- `docker/`: Docker-related files (initialization scripts).
- `build.gradle.kts`: Gradle build configuration.
- `docker-compose.yml`: Defines the multi-container Docker environment.
- `.env.example`: Example environment variables.

## Extending the Project

### Adding a New Module

1.  Create a new package under `src/main/java/com/aversion/server/modules/` (e.g., `com.aversion.server.modules.yourmodule`).
2.  Create a new class that extends `BaseModule`.
3.  Implement the `getConfig()` method to define your module's name and version.
4.  Add methods annotated with `@com.aversion.server.tools.ToolDefinition` to define new tools. Each tool method should accept a `JsonNode` for arguments and return a `Map<String, Object>`.
5.  Create corresponding JSON schema files for your new tools in `src/main/resources/tools/yourmodule/`.

### Defining New Tools

Tools are defined by:

1.  A method in a `BaseModule` subclass annotated with `@ToolDefinition`.
2.  A JSON schema file in `src/main/resources/tools/` that describes the tool's input and output.

## Contributing

Feel free to fork this repository, open issues, and submit pull requests. Any contributions are welcome!
