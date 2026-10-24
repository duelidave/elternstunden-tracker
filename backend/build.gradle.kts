import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
    alias(libs.plugins.cyclonedx)
}

group = "de.elternstunden"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        // OPEN-08: Java and Gradle are the interim choice (Kotlin or Maven still open)
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// SEC-14: security fixes ahead of the next Spring Boot patch release (found by the Trivy SBOM scan).
// Remove each override once the Spring Boot BOM ships the same or a newer version.
extra["tomcat.version"] = "11.0.25"
extra["jackson-bom.version"] = "3.1.7"
extra["jackson-2-bom.version"] = "2.21.7"

// ---------------------------------------------------------------------------
// API-first (D-25): interfaces and DTOs are generated from the shared contract
// ---------------------------------------------------------------------------
val contract = rootProject.layout.projectDirectory.file("api/openapi.yaml")
val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName = "spring"
    inputSpec = contract
    outputDir = generatedApiDir
    apiPackage = "de.elternstunden.api"
    modelPackage = "de.elternstunden.api.model"
    modelNameSuffix = "Dto"
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot4" to "true",
        "useJackson3" to "true",
        "useBeanValidation" to "true",
        "performBeanValidation" to "false",
        "skipDefaultInterface" to "true",
        "useResponseEntity" to "true",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "useTags" to "true",
        "useJspecify" to "true",
        "requestMappingMode" to "api_interface",
        "generateJsonIncludeAnnotations" to "false",
        "hideGenerationTimestamp" to "true",
        "sourceFolder" to "src/main/java",
    )
}

openApiValidate {
    inputSpec = contract
}

sourceSets {
    main {
        java.srcDir(generatedApiDir.map { it.dir("src/main/java") })
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// Fixed name for the executable jar (Dockerfile); the plain jar stays for the integration test suite
tasks.bootJar {
    archiveFileName = "app.jar"
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.github.ben-manes.caffeine:caffeine")
    // ALTCHA captcha (D-07), needs org.json at runtime
    implementation(libs.altcha)
    runtimeOnly(libs.org.json)
    // Argon2id for the master password (D-21)
    implementation(libs.bouncycastle)
    // Billing PDF (FS-PDF): HTML template with auto-escaping, rendered without any external resource
    implementation("org.thymeleaf:thymeleaf")
    implementation(libs.openhtmltopdf)
    // Passkeys (WebAuthn): relying party operations and JDBC storage from Spring Security, webauthn4j inside
    implementation("org.springframework.security:spring-security-webauthn")
    runtimeOnly("org.postgresql:postgresql")

    errorprone(libs.errorprone.core)
}

// ---------------------------------------------------------------------------
// Tests: unit/slice tests in `test`, Testcontainers tests in `integrationTest`
// ---------------------------------------------------------------------------
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
                implementation("org.springframework.boot:spring-boot-starter-security-test")
            }
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.testcontainers:testcontainers-junit-jupiter")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation(libs.swagger.parser)
                // Authenticator emulator for the passkey tests (same version as in spring-security-webauthn)
                implementation("com.webauthn4j:webauthn4j-test:0.31.9.RELEASE")
                runtimeOnly("org.postgresql:postgresql")
            }
            targets.all {
                testTask.configure { shouldRunAfter(tasks.test) }
            }
        }
    }
}

// Integration tests share the test resources (application-test.yaml)
sourceSets.named("integrationTest") {
    resources.srcDir("src/test/resources")
}

// Local run without Docker: ./gradlew :backend:bootRun reads ../.env and starts with the dev profile
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                environment(key.trim(), value.trim().removeSurrounding("'").removeSurrounding("\""))
            }
    }
    environment("SPRING_PROFILES_ACTIVE", System.getenv("SPRING_PROFILES_ACTIVE") ?: "dev")
}

// Integration tests see the same libraries as the application code
configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get())
}

// The integration tests use the same DB init script as docker compose (OPS-14)
tasks.named<Test>("integrationTest") {
    systemProperty("elternstunden.dbInitScript", rootProject.file("ops/db/init/01-create-app-role.sh").absolutePath)
    // AC-AUTH-17 sends a forged Host header
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "host")
}

tasks.withType<Test>().configureEach {
    // The contract is read by the contract coverage test
    systemProperty("elternstunden.contract", contract.asFile.absolutePath)
}

// ---------------------------------------------------------------------------
// Static analysis (NFR-04): warnings are errors, Error Prone as linter, Spotless for formatting.
// Generated code is excluded from both.
// ---------------------------------------------------------------------------
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror", "-parameters"))
    options.errorprone {
        excludedPaths = ".*/build/generated/.*"
        disableWarningsInGeneratedCode = true
    }
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------------
// Coverage (NFR-04): service layer at least 80 % lines. Packages appear from M1 on.
// ---------------------------------------------------------------------------
val integrationTest = tasks.named<Test>("integrationTest")
val coverageData = fileTree(layout.buildDirectory.dir("jacoco")) { include("test.exec", "integrationTest.exec") }

tasks.jacocoTestReport {
    executionData.setFrom(coverageData)
    dependsOn(tasks.test, integrationTest)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude("de/elternstunden/api/**") })
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    executionData.setFrom(coverageData)
    dependsOn(tasks.test, integrationTest)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude("de/elternstunden/api/**") })
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("de.elternstunden.*.service*")
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.openApiValidate, tasks.jacocoTestCoverageVerification)
    finalizedBy(tasks.jacocoTestReport)
}
