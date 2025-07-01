package fi.metatavu.keycloak;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class GraphApiTests {

    static GenericContainer keycloakContainer;

    @BeforeAll
    public static void setup() {
        keycloakContainer = new GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.1.2"));
        keycloakContainer.start();
    }

    @Test
    public void testImportUser () {
        assert (keycloakContainer.isRunning());
        System.out.println(keycloakContainer.getHost());// This test will be implemented later
    }

    @AfterAll
    public static void tearDown() {
        if (keycloakContainer != null) {
            keycloakContainer.stop();
        }
    }
}
