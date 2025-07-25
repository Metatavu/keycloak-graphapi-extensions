package fi.metatavu.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import java.util.UUID;

/**
 * Utility class for Keycloak tests
 */
public class KeycloakTestUtils {

    /**
     * Creates a Keycloak container with the specified network
     *
     * @param network the network to use for the Keycloak container
     * @return a configured KeycloakContainer instance
     */
    @SuppressWarnings("resource")
    public static KeycloakContainer createKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
            .withNetwork(network)
            .withFileSystemBind("./build/libs/", "/opt/keycloak/providers", BindMode.READ_ONLY)
            .withRealmImportFiles("kc-azure.json", "kc-test.json")
            .withEnv("GRAPH_API_URL", "http://wiremock:8080")
            .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(KeycloakTestUtils.getJacocoAgentPath()),
                    "/jacoco-agent/org.jacoco.agent-runtime.jar"
            )
            .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()))
            .withNetworkAliases("keycloak");
    }

    /**
     * Stops the Keycloak container and copies the JaCoCo exec file to the build directory
     *
     * @param keycloakContainer the Keycloak container to stop
     */
    @SuppressWarnings("resource")
    public static void stopKeycloakContainer(KeycloakContainer keycloakContainer) {
        if (keycloakContainer != null) {
            String execFile = UUID.randomUUID().toString();
            keycloakContainer.getDockerClient().stopContainerCmd(keycloakContainer.getContainerId()).exec();
            keycloakContainer.copyFileFromContainer("/tmp/jacoco.exec", "./build/jacoco/" + execFile + ".exec");

            keycloakContainer.stop();
        }
    }

    /**
     * Returns Keycloak image
     *
     * @return Keycloak image
     */
    private static String getKeycloakImage() {
        String keycloakVersion = System.getenv("KEYCLOAK_VERSION");
        if (keycloakVersion == null || keycloakVersion.isEmpty()) {
            throw new IllegalStateException("Environment variable 'KEYCLOAK_VERSION' is not set or is empty.");
        }
        return "quay.io/keycloak/keycloak:" + keycloakVersion;
    }

    /**
     * Returns Jacoco agent path
     *
     * @return Jacoco agent path
     */
    private static String getJacocoAgentPath() {
        return System.getenv("JACOCO_AGENT");
    }



}
