package fi.metatavu.keycloak;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

public class GraphApiTests {

    static GenericContainer keycloakContainer;

    static GenericContainer wiremockContainer;

    static GenericContainer seleniumContainer;

    @BeforeAll
    public static void setup() throws IOException, InterruptedException {
        keycloakContainer = new GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.1.2"));
        keycloakContainer.addFileSystemBind("./build/libs/", "/opt/keycloak/providers", BindMode.READ_ONLY);
        keycloakContainer.addFileSystemBind("./src/test/resources/kc.json", "/opt/keycloak/data/import/kc.json", BindMode.READ_ONLY);
        keycloakContainer.withEnv("GRAPH_API_URL", "http://wiremock:8080");
        keycloakContainer.withCommand("start-dev", "--import-realm");
        keycloakContainer.start();

        wiremockContainer = new GenericContainer(DockerImageName.parse("wiremock/wiremock:3.3.1"));
        wiremockContainer.withFileSystemBind("./src/test/resources/mappings", "/home/wiremock/mappings", BindMode.READ_ONLY);
        wiremockContainer.start();

        seleniumContainer = new GenericContainer(DockerImageName.parse("selenium/standalone-chrome:124.0-20250606"));
        seleniumContainer.start();
    }

    @Test
    public void testImportUser () {
    }

    @AfterAll
    public static void tearDown() {
        if (keycloakContainer != null) {
            keycloakContainer.stop();
        }

        if (wiremockContainer != null) {
            wiremockContainer.stop();
        }

        if (seleniumContainer != null) {
            seleniumContainer.stop();
        }
    }
}
