package fi.metatavu.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class GraphApiTests extends AbstractSeleniumTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createKeycloakContainer(network);

    @Container
    @SuppressWarnings({"resource", "unused"})
    private static final GenericContainer<?> wiremockContainer = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.3.1"))
            .withNetworkAliases("wiremock")
            .withNetwork(network)
            .withFileSystemBind("./src/test/resources/mappings", "/home/wiremock/mappings", BindMode.READ_ONLY);

    @Container
    @SuppressWarnings("resource")
    private static final BrowserWebDriverContainer<?> webDriverContainer = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withNetworkAliases("chrome")
            .withCapabilities(new ChromeOptions())
            .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);

    @AfterAll
    static void tearDown() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
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
}
