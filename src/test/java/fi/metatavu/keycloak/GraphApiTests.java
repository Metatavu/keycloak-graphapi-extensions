package fi.metatavu.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@Testcontainers
public class GraphApiTests extends AbstractSeleniumTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createKeycloakContainer(network);

    @Container
    @SuppressWarnings("unused")
    private static final WireMockContainer wiremockContainer = new WireMockContainer("wiremock/wiremock:2.35.0")
            .withNetwork(network)
            .withNetworkAliases("wiremock")
            .withFileSystemBind("./src/test/resources/mappings", "/home/wiremock/mappings", BindMode.READ_ONLY)
            .withLogConsumer(outputFrame -> System.out.printf("WIREMOCK: %s", outputFrame.getUtf8String()));

    @Container
    @SuppressWarnings("resource")
    private static final BrowserWebDriverContainer<?> webDriverContainer = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withNetworkAliases("chrome")
            .withCapabilities(new ChromeOptions())
            .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);

    @BeforeAll
    static void setUp() {
        WireMock.configureFor(wiremockContainer.getMappedPort(8080));
    }

    @AfterEach
    void afterEach() {
        WireMock.reset();
    }

    @AfterAll
    static void afterAll() {
        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Test
    void testGetManagerAttributes () {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), new ChromeOptions());
        try {
            driver.get(getAccountUrl());

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

            // Verify that the manager endpoint was called just once
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/me/manager")));

            // Logout and login again
            logout(driver);
            driver.get(getAccountUrl());
            waitButtonAndClick(driver, By.id("social-oidc"));
            waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
            waitInputAndType(driver, By.id("username"), "test1");
            waitInputAndType(driver, By.id("password"), "test");
            waitButtonAndClick(driver, By.id("kc-login"));

            // Verify that the manager has been retrieved again
            WireMock.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/me/manager")));
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGetUserAttributes () {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), new ChromeOptions());
        try {
            driver.get(getAccountUrl());

            waitButtonAndClick(driver, By.id("social-oidc"));
            waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
            waitInputAndType(driver, By.id("username"), "test1");
            waitInputAndType(driver, By.id("password"), "test");
            waitButtonAndClick(driver, By.id("kc-login"));

            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-given-name"), "Megan");
            waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-user-business-phones0"), "+1 425 555 0102");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-display-name"), "Megan Bowen");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-job-title"), "Auditor");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-mail"), "MeganB@M365x214355.onmicrosoft.com");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-mobile-phone"), "+1 425 555 0110");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-office-location"), "18/2111");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-preferred-language"), "en-US");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-surname"), "Bowen");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-user-principal-name"), "MeganB@M365x214355.onmicrosoft.com");

            // Verify that the user endpoint was called just once
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/me")));

            // Logout and login again
            logout(driver);
            driver.get(getAccountUrl());
            waitButtonAndClick(driver, By.id("social-oidc"));
            waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
            waitInputAndType(driver, By.id("username"), "test1");
            waitInputAndType(driver, By.id("password"), "test");
            waitButtonAndClick(driver, By.id("kc-login"));

            // Verify that the user has been retrieved again
            WireMock.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/me")));
        } finally {
            driver.quit();
        }
    }

}
