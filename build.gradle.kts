import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import javax.inject.Inject

plugins {
    application
    java
    id("java-library")
    id("com.gradleup.shadow") version "9.0.0-rc1"
    id("jacoco")
    id("org.sonarqube") version "6.2.0.5505"
    id("com.google.cloud.tools.jib") version "3.4.5"
    id("org.owasp.dependencycheck") version "12.1.3"
}

group = "com.aversion"
version = "1.0.0"

// Reusable custom task for executing command line instructions
abstract class ExecTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val command: ListProperty<String>

    @get:Input
    abstract val environmentVars: MapProperty<String, Any>

    @TaskAction
    fun runCommand() {
        execOperations.exec {
            commandLine(command.get())
            if (environmentVars.isPresent) {
                environment(environmentVars.get())
            }
        }
    }
}

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
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    // Database drivers
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.50.2.0")
    testImplementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.4.0")
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
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.jetbrains:annotations:26.0.2")

    // HTML parsing for web module
    implementation("org.jsoup:jsoup:1.21.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.3")
    testImplementation("org.junit.platform:junit-platform-launcher:1.13.3")
    testImplementation("org.junit.platform:junit-platform-engine:1.13.3")
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

    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
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

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-parameters", "-proc:none"))
    options.isIncremental = true
}

tasks.compileTestJava {
    options.compilerArgs.addAll(listOf("-parameters"))
    options.isIncremental = true
}

tasks.register("testWithCoverage") {
    group = "verification"
    description = "Run tests with coverage reporting"
    dependsOn(tasks.named("test"))
}

tasks.register<ExecTask>("buildAndRun") {
    group = "application"
    description = "Build the application and run it"
    dependsOn(tasks.named("shadowJar"))
    command.set(listOf("java", "-jar", "build/libs/mcp-server-${project.version}.jar"))
}

tasks.register<ExecTask>("dev") {
    group = "development"
    description = "Run the application in development mode"
    dependsOn(tasks.named("classes"))
    command.set(listOf("java", "-cp", sourceSets.main.get().runtimeClasspath.asPath, application.mainClass.get()))
    environmentVars.set(mapOf("ENV" to "development"))
}

tasks.register<ExecTask>("dockerBuild") {
    group = "docker"
    description = "Build Docker image"
    dependsOn(tasks.named("shadowJar"))
    command.set(listOf("docker", "build", "-t", "mcp-server:${project.version}", "."))
}

tasks.register<ExecTask>("dockerBuildProd") {
    group = "docker"
    description = "Build production Docker image"
    dependsOn(tasks.named("shadowJar"))
    command.set(listOf("docker", "build", "-t", "mcp-server:latest", "-t", "mcp-server:${project.version}", "."))
}

tasks.register<ExecTask>("dockerRun") {
    group = "docker"
    description = "Run Docker container locally"
    dependsOn(tasks.named("dockerBuild"))
    command.set(listOf("docker", "run", "--rm", "-p", "8080:8080", "mcp-server:${project.version}"))
}

tasks.register<ExecTask>("dockerComposeUp") {
    group = "docker"
    description = "Start services using Docker Compose"
    command.set(listOf("docker", "compose", "up", "-d"))
}

tasks.register<ExecTask>("dockerComposeDown") {
    group = "docker"
    description = "Stop services using Docker Compose"
    command.set(listOf("docker", "compose", "down"))
}

tasks.register<ExecTask>("dockerComposeProd") {
    group = "docker"
    description = "Deploy to production using Docker Compose"
    dependsOn(tasks.named("dockerBuildProd"))
    command.set(listOf("docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.prod.yml", "up", "-d"))
}

tasks.register("prodBuild") {
    group = "build"
    description = "Build for production deployment"
    dependsOn(tasks.named("shadowJar"), tasks.named("dockerBuildProd"))
}

tasks.register<ExecTask>("deploy") {
    group = "deployment"
    description = "Deploy to production"
    dependsOn(tasks.named("prodBuild"))
    command.set(listOf("docker", "compose", "-f", "docker-compose.prod.yml", "up", "-d", "--force-recreate"))
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("**/*.exec"))
    finalizedBy(tasks.named("jacocoTestCoverageVerification"))
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

dependencyCheck {
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: project.findProperty("NVD_API_KEY") as String?
        delay = 6000
    }
}

tasks.register("validateProduction") {
    group = "deployment"
    description = "Validate production readiness"
    dependsOn(tasks.named("test"), tasks.named("jacocoTestReport"))
    doLast {
        println("Production validation completed successfully")
    }
}

tasks.register("cleanBuildCache") {
    group = "build"
    description = "Clean Gradle build cache"
    doLast {
        delete(gradle.startParameter.gradleUserHomeDir.resolve("caches"))
        println("Build cache cleaned")
    }
}