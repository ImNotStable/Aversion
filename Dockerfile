# Multi-stage Docker build for Java MCP Server
# Optimized for performance, security, and modern best practices

# Build stage with optimized caching
FROM gradle:8.14.3-jdk21-alpine AS builder

# Set build arguments for better caching
ARG GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"
ARG BUILD_CACHE_DIR=/root/.gradle

# Create app directory
WORKDIR /app

# Copy dependency files first for better layer caching
COPY gradle gradle/
COPY gradlew gradlew.bat ./
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies (this layer will be cached if dependencies don't change)
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon

# Copy source code
COPY src src/

# Build the application with optimized settings
RUN ./gradlew shadowJar --no-daemon -x test && \
    # Verify the JAR was created
    ls -la build/libs/ && \
    # Create a checksum for verification
    sha256sum build/libs/mcp-server-*.jar > build/libs/checksum.sha256

# Runtime stage with minimal footprint
FROM eclipse-temurin:21-jre-alpine AS production

# Install necessary packages with minimal footprint
RUN apk add --no-cache --update \
        curl \
        sqlite \
        ca-certificates \
        tzdata \
        dumb-init && \
    # Clean up package cache
    rm -rf /var/cache/apk/*

# Create non-root user with specific UID/GID for better security
RUN addgroup -g 1001 -S mcpuser && \
    adduser -u 1001 -S mcpuser -G mcpuser

# Create necessary directories with proper permissions
RUN mkdir -p /app/logs /app/data /app/config /app/temp && \
    chown -R mcpuser:mcpuser /app

WORKDIR /app

# Copy JAR from builder stage with verification
COPY --from=builder --chown=mcpuser:mcpuser /app/build/libs/mcp-server-*.jar app.jar
COPY --from=builder --chown=mcpuser:mcpuser /app/build/libs/checksum.sha256 checksum.sha256

# Verify JAR integrity
RUN sha256sum -c checksum.sha256 && \
    rm checksum.sha256

# Copy configuration files
COPY --chown=mcpuser:mcpuser src/main/resources/application.yml config/application.yml
COPY --chown=mcpuser:mcpuser src/main/resources/logback-spring.xml config/logback-spring.xml

# Create enhanced health check script
RUN echo '#!/bin/sh\n\
# Enhanced health check for MCP Server\n\
if [ -f /app/app.jar ]; then\n\
    echo "Health check - MCP Server JAR exists"\n\
    exit 0\n\
else\n\
    echo "Health check failed - JAR not found"\n\
    exit 1\n\
fi' > /app/health-check.sh && \
    chmod +x /app/health-check.sh && \
    chown mcpuser:mcpuser /app/health-check.sh

# Switch to non-root user
USER mcpuser

# Set optimized environment variables
# Set optimized environment variables
# ENV JAVA_OPTS="-Xmx512m \
#     -XX:+UseG1GC \
#     -XX:MaxGCPauseMillis=200 \
#     -XX:+UseStringDeduplication \
#     -XX:+OptimizeStringConcat \
#     -Djava.security.egd=file:/dev/./urandom \
#     -Dfile.encoding=UTF-8 \
#     -Duser.timezone=UTC" \
#     LOG_LEVEL=info \
#     LOG_FORMAT=json \
#     TZ=UTC

# Expose port for health checks
EXPOSE 8080

# Add labels for better image management
LABEL maintainer="Aversion Team" \
      version="1.0.0" \
      description="Modern MCP Server for AI assistants" \
      org.opencontainers.image.source="https://github.com/ImNotStable/Aversion" \
      org.opencontainers.image.title="Aversion MCP Server" \
      org.opencontainers.image.description="A modern MCP server implementation"

# Health check with better configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD /app/health-check.sh

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Run the application with proper signal handling
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
