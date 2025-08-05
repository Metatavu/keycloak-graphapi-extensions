import java.util.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
}

repositories {
    mavenLocal()
    mavenCentral()
}

val keycloakVersion: String by project
val testContainersKeycloakVersion: String by project
val testContainersVersion: String by project
val restAssuredVersion: String by project
val junitVersion: String by project
val seleniumRemoteDriverVersion: String by project
val seleniumVersion: String by project
val jacocoVersion: String by project
val wiremockTestContainerVersion: String by project
val wiremockVersion: String by project

val jacocoRuntime: Configuration by configurations.creating

dependencies {
    implementation(enforcedPlatform("org.keycloak.bom:keycloak-bom-parent:$keycloakVersion"))
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")

    testImplementation("org.seleniumhq.selenium:selenium-remote-driver:$seleniumRemoteDriverVersion")
    testImplementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("org.testcontainers:selenium:$testContainersVersion")
    testImplementation("com.github.dasniko:testcontainers-keycloak:$testContainersKeycloakVersion")
    testImplementation("org.wiremock.integrations.testcontainers:wiremock-testcontainers-module:$wiremockTestContainerVersion")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")

    jacocoRuntime("org.jacoco:org.jacoco.agent:$jacocoVersion:runtime")
}

jacoco {
    toolVersion = jacocoVersion
}

tasks.named<Test>("test") {
    val jacocoAgent = configurations["jacocoRuntime"].singleFile

    environment("BUILD_DIR", getLayout().buildDirectory.asFile.get().absolutePath)
    environment("KEYCLOAK_VERSION", keycloakVersion)
    environment("JACOCO_AGENT", jacocoAgent)

    useJUnitPlatform()
}

group = "fi.metatavu.keycloak.graphapi"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.register<JacocoReport>("jacocoIntegrationReport") {
    dependsOn("test")

    val execFiles = fileTree("build/jacoco") {
        include("**/*.exec")
    }

    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(execFiles)

    classDirectories.setFrom(fileTree("build/classes/java/main") {
        include("fi/metatavu/keycloak/graphapi/**")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register("nextReleaseVersion") {
    doLast {
        val propsFile = project.rootDir.resolve("gradle.properties")
        val props = Properties().apply {
            load(propsFile.reader())
        }

        val currentVersion = props.getProperty("version")
        println("currentVersion: $currentVersion")

        if (!currentVersion.endsWith("-SNAPSHOT")) {
            println("Current version is not a snapshot version.")
        } else {
            val newVersion = currentVersion.substring(0, currentVersion.length - "-SNAPSHOT".length)
            props.setProperty("version", newVersion)
            props.store(propsFile.writer(), null)
            println("Version updated to: $newVersion")
        }
    }
}

tasks.register("nextSnapshotVersion") {
    doLast {
        val propsFile = project.rootDir.resolve("gradle.properties")
        val props = Properties().apply {
            load(propsFile.reader())
        }

        val currentVersion = props.getProperty("version")
        val baseVersion = if (!currentVersion.endsWith("-SNAPSHOT")) {
            currentVersion
        } else {
            currentVersion.substring(0, currentVersion.length - "-SNAPSHOT".length)
        }

        println("currentVersion: $currentVersion")

        val versionComponents = baseVersion.split('.').map { it.toInt() }.toMutableList()
        if (versionComponents.size >= 3) {
            versionComponents[2] = versionComponents[2] + 1
            val newVersion = versionComponents.joinToString(".") + "-SNAPSHOT"
            props.setProperty("version", newVersion)
            props.store(propsFile.writer(), null)
            println("Version updated to: $newVersion")
        } else {
            println("Invalid version format")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Metatavu/keycloak-graphapi-extensions")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            artifact(tasks["jar"])
        }
    }
}