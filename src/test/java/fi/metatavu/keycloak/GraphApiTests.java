package fi.metatavu.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
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
import org.testcontainers.utility.DockerImageName;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class GraphApiTests extends AbstractSeleniumTest {

    private static final Network network = Network.newNetwork();

    private static DockerImageName getSeleniumImage() {
        String architecture = System.getProperty("os.arch", "").toLowerCase();
        if (architecture.contains("aarch64") || architecture.contains("arm64")) {
            return DockerImageName.parse("selenium/standalone-chromium:latest")
                .asCompatibleSubstituteFor("selenium/standalone-chrome");
        }

        return DockerImageName.parse("selenium/standalone-chrome:4.20.0");
    }

    @Container
    private static final KeycloakContainer keycloakContainer = KeycloakTestUtils.createKeycloakContainer(network);

    @Container
    @SuppressWarnings("unused")
    private static final WireMockContainer wiremockContainer = new WireMockContainer("wiremock/wiremock:2.35.0")
            .withNetwork(network)
            .withNetworkAliases("wiremock")
            .withFileSystemBind("./src/test/resources/mappings", "/home/wiremock/mappings", BindMode.READ_ONLY)
            .withLogConsumer(outputFrame -> System.out.printf("WIREMOCK: %s", outputFrame.getUtf8String()));

    private static ChromeOptions createChromeOptions() {
        return new ChromeOptions()
            .addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
            );
    }

    @Container
    @SuppressWarnings("resource")
    private static final BrowserWebDriverContainer<?> webDriverContainer = new BrowserWebDriverContainer<>(getSeleniumImage())
            .withNetwork(network)
            .withNetworkAliases("chrome")
            .withSharedMemorySize(2L * 1024 * 1024 * 1024)
            .withCapabilities(createChromeOptions())
            .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);

    private static final String TEST_REALM = "test";
    private static final String TEST_USERNAME = "test1";
    private static final String TRANSITIVE_MEMBER_OF_PATH = "/me/transitiveMemberOf/microsoft.graph.group";
    private static final Set<String> MANAGED_GROUP_PATHS = Set.of("/finance", "/sales", "/parent/child");

    private static Keycloak adminClient;

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
        if (adminClient != null) {
            adminClient.close();
        }

        KeycloakTestUtils.stopKeycloakContainer(keycloakContainer);
    }

    @Test
    void testGetManagerAttributes () {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
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
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/users/24fcbca3-c3e2-48bf-9ffc-c7f81b81483d/profile/positions")));
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-company-name"), "Contoso Ltd");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-department"), "Finance");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-cost-center"), "Information Management");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-job-title"), "CVP Finance");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-mail"), "diegos@m365x214355.onmicrosoft.com");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-mobile-phone"), "");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-office-location"), "14/1108");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-preferred-language"), "en-US");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-surname"), "Siciliani");
            waitAndAssertInputValue(driver, By.id("azure-ad-manager-user-principal-name"), "diegos@m365x214355.onmicrosoft.com");
            waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-manager-group-names0"), "Management+Group");
            waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-manager-group-names1"), "Leadership+Team");

            // Verify that the manager endpoint was called just once
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/manager")));
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/users/24fcbca3-c3e2-48bf-9ffc-c7f81b81483d/transitiveMemberOf/microsoft.graph.group"))
                .withQueryParam("$count", WireMock.equalTo("true"))
                .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail,groupTypes,resourceProvisioningOptions"))
                .withQueryParam("$filter", WireMock.equalTo("securityEnabled eq true and not(groupTypes/any(c:c eq 'Unified'))")));

            // Logout and login again
            logout(driver);
            driver.get(getAccountUrl());
            waitButtonAndClick(driver, By.id("social-oidc"));
            waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
            waitInputAndType(driver, By.id("username"), "test1");
            waitInputAndType(driver, By.id("password"), "test");
            waitButtonAndClick(driver, By.id("kc-login"));

            // Verify that the manager has been retrieved again
            WireMock.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/manager")));
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGetUserAttributes () {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
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
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/profile/positions")));
            waitAndAssertInputValue(driver, By.id("azure-ad-user-company-name"), "Contoso Ltd");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-department"), "Finance");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-cost-center"), "Information Management");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-job-title"), "Auditor");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-mail"), "meganb@m365x214355.onmicrosoft.com");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-mobile-phone"), "+1 425 555 0110");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-office-location"), "18/2111");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-preferred-language"), "en-US");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-surname"), "Bowen");
            waitAndAssertInputValue(driver, By.id("azure-ad-user-user-principal-name"), "meganb@m365x214355.onmicrosoft.com");
            waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-user-group-names0"), "Finance+Group");
            waitAndAssertInputValue(driver, byDataTestId("attributes.azure-ad-user-group-names1"), "Oulu+Team");

            // Verify that the user endpoint was called just once
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me")));
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
                .withQueryParam("$count", WireMock.equalTo("true"))
                .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail,groupTypes,resourceProvisioningOptions"))
                .withQueryParam("$filter", WireMock.equalTo("securityEnabled eq true and not(groupTypes/any(c:c eq 'Unified'))")));

            // Logout and login again
            logout(driver);
            driver.get(getAccountUrl());
            waitButtonAndClick(driver, By.id("social-oidc"));
            waitText(driver, By.id("kc-header-wrapper"), "REALM THAT SIMULATES AZURE AD");
            waitInputAndType(driver, By.id("username"), "test1");
            waitInputAndType(driver, By.id("password"), "test");
            waitButtonAndClick(driver, By.id("kc-login"));

            // Verify that the user has been retrieved again
            WireMock.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me")));
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGroups() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Default mock returns Finance Group, Oulu Team and non-managed All Staff group
            assertEquals(Set.of("/finance", "/parent/child"), getManagedGroupPaths());

            // Non-managed Keycloak groups must not be touched by the mapper
            UserResource user = getTestUserResource();
            user.joinGroup(getGroupByPath("/parent").getId());

            // User has left Oulu Team and joined Sales Group in Azure
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(TRANSITIVE_MEMBER_OF_PATH))
                .atPriority(1)
                .willReturn(WireMock.okJson(getTransitiveMemberOfJson("Finance Group", "Sales Group"))));

            logout(driver);
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            assertEquals(Set.of("/finance", "/sales"), getManagedGroupPaths());
            assertEquals(Set.of("/finance", "/sales", "/parent"), getUserGroupPaths());

            user.leaveGroup(getGroupByPath("/parent").getId());
        } finally {
            driver.quit();
        }
    }

    @Test
    void testUserGroupNames() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Group without display name is skipped and names are URL-encoded for storage
            assertEquals(Set.of("Finance+Group", "Oulu+Team", "All+Staff"), Set.copyOf(getTestUserAttribute("azure-ad-user-group-names")));

            // Groups mapper and group names mapper share the same request during the login
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(TRANSITIVE_MEMBER_OF_PATH)));

            // User has been removed from all groups in Azure
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(TRANSITIVE_MEMBER_OF_PATH))
                .atPriority(1)
                .willReturn(WireMock.okJson(getTransitiveMemberOfJson())));

            logout(driver);
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            assertEquals(List.of(), getTestUserAttribute("azure-ad-user-group-names"));
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGraphApiErrors() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            Set<String> groupsBefore = getUserGroupPaths();
            Map<String, List<String>> attributesBefore = getTestUser().getAttributes();

            for (String path : List.of("/me", "/me/manager", TRANSITIVE_MEMBER_OF_PATH)) {
                WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(path))
                    .atPriority(1)
                    .willReturn(WireMock.serverError()));
            }

            // Login must still succeed when Graph API fails
            logout(driver);
            WireMock.resetAllRequests();
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Failed requests must not be repeated by every mapper during the same login
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me")));
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/manager")));
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(TRANSITIVE_MEMBER_OF_PATH)));

            // Existing data must be preserved when Graph API fails
            assertEquals(groupsBefore, getUserGroupPaths());
            assertEquals(attributesBefore, getTestUser().getAttributes());
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGraphApiTimeout() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            Map<String, List<String>> attributesBefore = getTestUser().getAttributes();

            // Graph API responds slower than the request timeout (2 seconds in tests)
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me"))
                .atPriority(1)
                .willReturn(WireMock.okJson("{}").withFixedDelay(10000)));

            logout(driver);
            WireMock.resetAllRequests();
            long loginStarted = System.currentTimeMillis();
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Login must not wait for the slow response and timed out request must not be repeated by every mapper
            assertTrue(System.currentTimeMillis() - loginStarted < 10000, "Login should not wait for the slow Graph API response");
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me")));
            assertEquals(attributesBefore, getTestUser().getAttributes());
        } finally {
            driver.quit();
        }
    }

    @Test
    void testGraphApiStalledResponseBody() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            Map<String, List<String>> attributesBefore = getTestUser().getAttributes();

            // Graph API sends response headers immediately but the body slower than the request timeout (2 seconds in tests)
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me"))
                .atPriority(1)
                .willReturn(WireMock.okJson("{\"id\":\"c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110\",\"givenName\":\"Stalled\"}").withChunkedDribbleDelay(50, 10000)));

            logout(driver);
            WireMock.resetAllRequests();
            long loginStarted = System.currentTimeMillis();
            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Request timeout must also cover reading the response body
            assertTrue(System.currentTimeMillis() - loginStarted < 10000, "Login should not wait for the stalled Graph API response body");
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me")));
            assertEquals(attributesBefore, getTestUser().getAttributes());
        } finally {
            driver.quit();
        }
    }

    @Test
    void testUserWithoutManager() {
        RemoteWebDriver driver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), createChromeOptions());
        try {
            // Graph API responds with 404 when user does not have a manager
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/manager"))
                .atPriority(1)
                .willReturn(WireMock.notFound()));

            loginWithAzure(driver);
            waitAndAssertInputValue(driver, By.id("azure-ad-user-id"), "c13e5f62-fc61-4a9d-8a0c-5c9f87f0e110");

            // Missing manager must be requested only once per login regardless of the number of manager mappers
            WireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/me/manager")));
            WireMock.verify(0, WireMock.getRequestedFor(WireMock.urlPathMatching("/users/.*/profile/positions")));
        } finally {
            driver.quit();
        }
    }

    /**
     * Returns paths of groups managed by the groups mapper that the test user belongs to
     *
     * @return managed group paths
     */
    private Set<String> getManagedGroupPaths() {
        return getUserGroupPaths().stream()
            .filter(MANAGED_GROUP_PATHS::contains)
            .collect(Collectors.toSet());
    }

    /**
     * Returns paths of all groups the test user belongs to
     *
     * @return group paths
     */
    private Set<String> getUserGroupPaths() {
        return getTestUserResource().groups().stream()
            .map(GroupRepresentation::getPath)
            .collect(Collectors.toSet());
    }

    /**
     * Returns test realm group by path
     *
     * @param path group path
     * @return group
     */
    private GroupRepresentation getGroupByPath(String path) {
        return getAdminClient().realm(TEST_REALM).getGroupByPath(path);
    }

    /**
     * Returns attribute values of the test user
     *
     * @param name attribute name
     * @return attribute values
     */
    private List<String> getTestUserAttribute(String name) {
        return getTestUser().getAttributes().getOrDefault(name, List.of());
    }

    /**
     * Returns the brokered test user
     *
     * @return test user
     */
    private UserRepresentation getTestUser() {
        return getTestUserResource().toRepresentation();
    }

    /**
     * Returns resource for the brokered test user
     *
     * @return test user resource
     */
    private UserResource getTestUserResource() {
        List<UserRepresentation> users = getAdminClient().realm(TEST_REALM).users().searchByUsername(TEST_USERNAME, true);
        assertEquals(1, users.size());
        return getAdminClient().realm(TEST_REALM).users().get(users.getFirst().getId());
    }

    /**
     * Returns admin client for the Keycloak container
     *
     * @return admin client
     */
    private Keycloak getAdminClient() {
        if (adminClient == null) {
            adminClient = keycloakContainer.getKeycloakAdminClient();
        }

        return adminClient;
    }

    /**
     * Returns Graph API transitive member of response JSON with given group display names
     *
     * @param displayNames group display names
     * @return response JSON
     */
    private String getTransitiveMemberOfJson(String... displayNames) {
        String groups = Arrays.stream(displayNames)
            .map(displayName -> String.format("{\"id\":\"%s\",\"displayName\":\"%s\"}", UUID.randomUUID(), displayName))
            .collect(Collectors.joining(","));

        return String.format("{\"value\":[%s]}", groups);
    }

}
