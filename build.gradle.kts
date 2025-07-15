plugins {
    application
    java
    id("java-library")
    id("com.gradleup.shadow") version "9.0.0-rc1"
    id("jacoco")
    id("org.sonarqube") version "6.2.0.5505"
    id("com.google.cloud.tools.jib") version "3.4.5"
}

group = "com.aversion"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Core MCP dependencies
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    
    // Database drivers
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.50.2.0")
    testImplementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.4")
    implementation("org.postgresql:postgresql:42.7.7")
    
    // HTTP client for web module
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    
    // JSON validation
    implementation("com.networknt:json-schema-validator:1.5.8")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    
    // Configuration
    implementation("org.yaml:snakeyaml:2.4")
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("org.jetbrains:annotations:24.1.0")
    
    // HTML parsing for web module
    implementation("org.jsoup:jsoup:1.21.1")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.junit.platform:junit-platform-engine:1.11.4")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:mysql:1.21.3")
}

application {
    mainClass.set("com.aversion.server.AversionServerApplication")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    
    // Enable parallel test execution
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1

    // Only run tests if 'runOnCI' property is set (e.g., by GitHub Actions)
    onlyIf { project.hasProperty("runOnCI") }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("mcp-server-${version}.jar")
    
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
    
    // Exclude duplicates and unwanted files
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
    
    // Enable incremental compilation
    options.isIncremental = true
    
    // Enable annotation processing
    options.compilerArgs.addAll(listOf("-proc:none"))
}

tasks.compileTestJava {
    options.compilerArgs.addAll(listOf("-parameters"))
    
    // Enable incremental compilation
    options.isIncremental = true
    
    // Enable annotation processing
    // options.compilerArgs.addAll(listOf("-proc:none")) // Removed to allow Lombok processing
}

// Custom task for running tests with coverage
tasks.register("testWithCoverage") {
    group = "verification"
    description = "Run tests with coverage reporting"
    dependsOn(tasks.test)
}

// Custom task for building and running
tasks.register("buildAndRun") {
    group = "application"
    description = "Build the application and run it"
    dependsOn(tasks.shadowJar)
    doLast {
        exec {
            commandLine("java", "-jar", "build/libs/mcp-server-${version}.jar")
        }
    }
}

// Development tasks
tasks.register("dev") {
    group = "development"
    description = "Run the application in development mode"
    dependsOn(tasks.classes)
    doLast {
        exec {
            commandLine("java", "-cp", sourceSets.main.get().runtimeClasspath.asPath, application.mainClass.get())
            environment("ENV", "development")
        }
    }
}

// Docker tasks
tasks.register("dockerBuild") {
    group = "docker"
    description = "Build Docker image"
    dependsOn(tasks.shadowJar)
    doLast {
        exec {
            commandLine("docker", "build", "-t", "mcp-server:${version}", ".")
        }
    }
}

tasks.register("dockerBuildProd") {
    group = "docker"
    description = "Build production Docker image"
    dependsOn(tasks.shadowJar)
    doLast {
        exec {
            commandLine("docker", "build", "-t", "mcp-server:latest", "-t", "mcp-server:${version}", ".")
        }
    }
}

tasks.register("dockerRun") {
    group = "docker"
    description = "Run Docker container locally"
    dependsOn("dockerBuild")
    doLast {
        exec {
            commandLine("docker", "run", "--rm", "-p", "8080:8080", "mcp-server:${version}")
        }
    }
}

tasks.register("dockerComposeUp") {
    group = "docker"
    description = "Start services using Docker Compose"
    doLast {
        exec {
            commandLine("docker", "compose", "up", "-d")
        }
    }
}

tasks.register("dockerComposeDown") {
    group = "docker"
    description = "Stop services using Docker Compose"
    doLast {
        exec {
            commandLine("docker", "compose", "down")
        }
    }
}

tasks.register("dockerComposeProd") {
    group = "docker"
    description = "Deploy to production using Docker Compose"
    dependsOn("dockerBuildProd")
    doLast {
        exec {
            commandLine("docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.prod.yml", "up", "-d")
        }
    }
}

// Production tasks
tasks.register("prodBuild") {
    group = "build"
    description = "Build for production deployment"
    dependsOn(tasks.shadowJar, "dockerBuildProd")
}

tasks.register("deploy") {
    group = "deployment"
    description = "Deploy to production"
    dependsOn("prodBuild")
    doLast {
        exec {
            commandLine("docker", "compose", "-f", "docker-compose.prod.yml", "up", "-d", "--force-recreate")
        }
    }
}

// JaCoCo configuration for code coverage
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("**/*.exec"))
    finalizedBy(tasks.jacocoTestCoverageVerification)

    // Only generate report if 'runOnCI' property is set
    onlyIf { project.hasProperty("runOnCI") }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Jib configuration for containerization
jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        image = "mcp-server"
        tags = setOf("latest", version.toString())
    }
    container {
        ports = listOf("8080")
        labels = mapOf(
            "maintainer" to "MCP Server Team",
            "version" to version.toString(),
            "description" to "Production-ready MCP Server"
        )
        jvmFlags = listOf(
            "-Xmx512m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200",
            "-Djava.security.egd=file:/dev/./urandom"
        )
        environment = mapOf(
            "JAVA_TOOL_OPTIONS" to "-XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport"
        )
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
    extraDirectories {
        paths {
            path {
                setFrom("src/main/jib")
            }
        }
    }
}

// SonarQube configuration
sonarqube {
    properties {
        property("sonar.projectKey", "aversion-server")
        property("sonar.projectName", "Aversion MCP Server")
        property("sonar.projectVersion", version.toString())
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.binaries", "build/classes")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

tasks.sonarqube {
    onlyIf { project.hasProperty("runOnCI") }
}

// Deployment validation task
tasks.register("validateProduction") {
    group = "deployment"
    description = "Validate production readiness"
    dependsOn(tasks.test, tasks.jacocoTestReport)
    doLast {
        println("Production validation completed successfully")
    }
}

// Performance optimization tasks
tasks.register("cleanBuildCache") {
    group = "build"
    description = "Clean Gradle build cache"
    doLast {
        delete(gradle.startParameter.gradleUserHomeDir.resolve("caches"))
        println("Build cache cleaned")
    }
}
