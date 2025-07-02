package fi.metatavu.keycloak;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

public class GraphApiTests extends AbstractSeleniumTest {

    private static final Network network = Network.newNetwork();

    static GenericContainer keycloakContainer;

    static GenericContainer wiremockContainer;

    static BrowserWebDriverContainer webDriverContainer;

    @BeforeAll
    public static void setup() throws IOException, InterruptedException {
        keycloakContainer = new GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.1.2"));
        keycloakContainer.withNetwork(network);
        keycloakContainer.addFileSystemBind("./build/libs/", "/opt/keycloak/providers", BindMode.READ_ONLY);
        keycloakContainer.addFileSystemBind("./src/test/resources/kc.json", "/opt/keycloak/data/import/kc.json", BindMode.READ_ONLY);
        keycloakContainer.withNetworkAliases("keycloak");

        keycloakContainer.withExposedPorts(8080);
        keycloakContainer.withEnv("GRAPH_API_URL", "http://wiremock:8080");
        keycloakContainer.withCommand("start-dev", "--import-realm");
        keycloakContainer.start();

        wiremockContainer = new GenericContainer(DockerImageName.parse("wiremock/wiremock:3.3.1"));
        wiremockContainer.withNetworkAliases("wiremock");
        wiremockContainer.withNetwork(network);
        wiremockContainer.withFileSystemBind("./src/test/resources/mappings", "/home/wiremock/mappings", BindMode.READ_ONLY);
        wiremockContainer.start();

        webDriverContainer = new BrowserWebDriverContainer();
        webDriverContainer.withNetwork(network);
        webDriverContainer.withNetworkAliases("chrome");
        webDriverContainer.withCapabilities(new ChromeOptions());
        webDriverContainer.withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);
        webDriverContainer.withExposedPorts(4444);
        webDriverContainer.start();
    }

    @Test
    public void testGetManagerAttributes () {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), new ChromeOptions());
        driver.get("http://keycloak:8080/realms/test/account");

        waitButtonAndClick(driver, By.id("social-oidc"));
        waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
        waitInputAndType(driver, By.id("username"), "test1");
        waitInputAndType(driver, By.id("password"), "test");
        waitButtonAndClick(driver, By.id("kc-login"));

        waitAndAssertInputValue(driver, By.id("azure-ad-manager-id"), "24fcbca3-c3e2-48bf-9ffc-c7f81b81483d");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-given-name"), "Diego");
        waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-manager-business-phones0"), "+1 205 555 0108");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-display-name"), "Diego Siciliani");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-job-title"), "CVP Finance");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-mail"), "DiegoS@M365x214355.onmicrosoft.com");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-mobile-phone"), "");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-office-location"), "14/1108");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-preferred-language"), "en-US");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-surname"), "Siciliani");
        waitAndAssertInputValue(driver, By.id("azure-ad-manager-user-principal-name"), "DiegoS@M365x214355.onmicrosoft.com");
    }

    @AfterAll
    public static void tearDown() {
        if (keycloakContainer != null) {
            keycloakContainer.stop();
        }

        if (wiremockContainer != null) {
            wiremockContainer.stop();
        }

        if (webDriverContainer != null) {
            webDriverContainer.stop();
        }
    }
}
