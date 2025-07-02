import java.util.*

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val keycloakVersion: String by project
val testContainersVersion: String by project
val restAssuredVersion: String by project
val junitVersion: String by project
val seleniumRemoteDriverVersion: String by project
val seleniumVersion: String by project

dependencies {
    implementation(enforcedPlatform("org.keycloak.bom:keycloak-bom-parent:$keycloakVersion"))
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.seleniumhq.selenium:selenium-remote-driver:$seleniumRemoteDriverVersion")
    testImplementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("org.testcontainers:selenium:1.21.3")
}

tasks.named<Test>("test") {
    environment("BUILD_DIR", getLayout().buildDirectory.asFile.get().absolutePath)
    useJUnitPlatform()
}

group = "fi.metatavu.keycloak.graphapi"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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

tasks.withType<Test> {
    useJUnitPlatform()
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