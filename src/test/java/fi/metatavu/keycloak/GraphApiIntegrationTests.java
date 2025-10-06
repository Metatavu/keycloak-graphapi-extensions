package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import fi.metatavu.keycloak.KeycloakTestUtils;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import org.junit.jupiter.api.*;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.representations.AccessTokenResponse;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Simple stub for IdentityProviderMapperModel for testing purposes
 */
class StubIdentityProviderMapperModel extends IdentityProviderMapperModel {
    private final java.util.Map<String, String> config = new java.util.HashMap<>();

    @Override
    public java.util.Map<String, String> getConfig() {
        return config;
    }
}

/**
 * Test subclass to inject custom GraphApiClient
 */
class TestGraphApiGroupsIdentityProviderMapper extends GraphApiGroupsIdentityProviderMapper {
    private final GraphApiClient client;

    public TestGraphApiGroupsIdentityProviderMapper(GraphApiClient client) {
        this.client = client;
    }

    @Override
    protected GraphApiClient createGraphApiClient() {
        return client;
    }
}

@Testcontainers
public class GraphApiIntegrationTests {

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

    @BeforeEach
    void beforeEach() {
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
    void testGetAzureGroups() throws Exception {
        String expectedJson = """
            {
                "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#groups(id,displayName,description,mail)",
                "value": [
                    {"id": "123e4567-e89b-12d3-a456-426614174000","displayName": "Test Group 1","description": "Description 1","mail": "group1@example.com"},
                    {"id": "223e4567-e89b-12d3-a456-426614174001","displayName": "Test Group 2","description": "Description 2","mail": "group2@example.com"},
                    {"id": "323e4567-e89b-12d3-a456-426614174002","displayName": null,"description": "Description 3","mail": "group3@example.com"}
                ]
            }
        """;

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(expectedJson)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");

        GraphApiClient client = new GraphApiClient(wiremockUrl);
        GraphApiGroupsIdentityProviderMapper mapper = new TestGraphApiGroupsIdentityProviderMapper(client);

        List<TransitiveMemberOfGroup> groups = mapper.getAzureGroups(accessToken);

        assertNotNull(groups);
        assertEquals(2, groups.size());
        assertEquals("Test Group 1", groups.get(0).getDisplayName());
        assertEquals("Test Group 2", groups.get(1).getDisplayName());
    }

    @Test
    void testGetTransitiveMemberOfGroups() throws Exception {
        String expectedJson = """
            {
                "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#groups(id,displayName,description,mail)",
                "value": [
                    {"id": "123e4567-e89b-12d3-a456-426614174000","displayName": "Test Group 1","description": "Description 1","mail": "group1@example.com"},
                    {"id": "223e4567-e89b-12d3-a456-426614174001","displayName": "Test Group 2","description": "Description 2","mail": "group2@example.com"}
                ]
            }
        """;

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(expectedJson)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");

        GraphApiClient client = new GraphApiClient(wiremockUrl);
        var result = client.getTransitiveMemberOfGroups(accessToken);

        assertNotNull(result);
        assertNotNull(result.getValue());
        assertEquals(2, result.getValue().size());
    }

    @Test
    void testGetManager() throws Exception {
        String expectedJson = """
            {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "displayName": "Manager User",
                "mail": "manager@example.com",
                "userPrincipalName": "manager@example.com"
            }
        """;

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/manager"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(expectedJson)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");

        GraphApiClient client = new GraphApiClient(wiremockUrl);
        var result = client.getManager(accessToken);

        assertNotNull(result);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", result.getId().toString());
    }

    @Test
    void testUserAddedToKeycloakGroupFromAzureGroup() throws Exception {
        String azureGroupName = "Azure Engineering";
        String keycloakGroupPath = "engineering";

        String azureResponse = """
            {
                "value": [
                    {"id": "111e4567-e89b-12d3-a456-426614174000","displayName": "%s"}
                ]
            }
        """.formatted(azureGroupName);

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(azureResponse)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        GraphApiClient client = new GraphApiClient(wiremockUrl);
        GraphApiGroupsIdentityProviderMapper mapper = new TestGraphApiGroupsIdentityProviderMapper(client);

        var accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");

        var groups = mapper.getAzureGroups(accessToken);
        assertNotNull(groups);
        assertEquals(1, groups.size());
        assertEquals(azureGroupName, groups.get(0).getDisplayName());

        WireMock.verify(1, WireMock.getRequestedFor(
            WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
        );
    }

    @Test
    void testUpdateGroupsJoinsUserToMappedGroup() throws Exception {
        String azureGroupName = "Azure Engineering";
        String keycloakGroupPath = "engineering";

        String azureResponse = """
            {
                "value": [
                    {"id": "111e4567-e89b-12d3-a456-426614174000","displayName": "%s"}
                ]
            }
        """.formatted(azureGroupName);

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(azureResponse)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        GraphApiClient client = new GraphApiClient(wiremockUrl);
        GraphApiGroupsIdentityProviderMapper mapper = new TestGraphApiGroupsIdentityProviderMapper(client);

        ObjectMapper objectMapper = new ObjectMapper();
        String mappingJson = """
            [{"key":"%s","value":"%s"}]
        """.formatted(azureGroupName, keycloakGroupPath);
        IdentityProviderMapperModel mapperModel = new StubIdentityProviderMapperModel();
        mapperModel.setConfig(Map.of("graph-api-group-mapping", mappingJson));

        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);

        RealmModel realm = mock(RealmModel.class);
        GroupModel groupModel = mock(GroupModel.class);
        when(groupModel.getName()).thenReturn(keycloakGroupPath);
        when(realm.getGroupsStream()).thenReturn(Stream.of(groupModel));

        UserModel user = mock(UserModel.class);
        when(user.getGroupsStream()).thenReturn(Stream.empty());

        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");
        BrokeredIdentityContext context = mock(BrokeredIdentityContext.class);
        when(context.getToken()).thenReturn(objectMapper.writeValueAsString(accessToken));

        assertDoesNotThrow(() -> mapper.updateGroups(session, realm, user, mapperModel, context));

        verify(user, times(1)).joinGroup(groupModel);
        verify(user, never()).leaveGroup(any(GroupModel.class));

        WireMock.verify(1, WireMock.getRequestedFor(
            WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
        );
    }

    @Test
    void testUpdateGroupsLeavesUserFromManagedGroupWhenNotInAzure() throws Exception {
        String azureGroupName = "Azure Engineering";
        String keycloakGroupPath = "engineering";

        String azureResponse = """
            {
                "value": [ ]
            }
        """;

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(azureResponse)));

        String wiremockUrl = String.format("http://localhost:%d", wiremockContainer.getMappedPort(8080));
        GraphApiClient client = new GraphApiClient(wiremockUrl);
        GraphApiGroupsIdentityProviderMapper mapper = new TestGraphApiGroupsIdentityProviderMapper(client);

        ObjectMapper objectMapper = new ObjectMapper();
        String mappingJson = """
            [{"key":"%s","value":"%s"}]
        """.formatted(azureGroupName, keycloakGroupPath);
        IdentityProviderMapperModel mapperModel = new StubIdentityProviderMapperModel();
        mapperModel.setConfig(Map.of("graph-api-group-mapping", mappingJson));

        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);

        RealmModel realm = mock(RealmModel.class);
        GroupModel groupModel = mock(GroupModel.class);
        when(groupModel.getName()).thenReturn(keycloakGroupPath);
        when(realm.getGroupsStream()).thenReturn(Stream.of(groupModel));

        UserModel user = mock(UserModel.class);
        when(user.getGroupsStream()).thenReturn(Stream.of(groupModel));

        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken("dummy-token");
        BrokeredIdentityContext context = mock(BrokeredIdentityContext.class);
        when(context.getToken()).thenReturn(objectMapper.writeValueAsString(accessToken));

        assertDoesNotThrow(() -> mapper.updateGroups(session, realm, user, mapperModel, context));

        verify(user, never()).joinGroup(any(GroupModel.class));
        verify(user, times(1)).leaveGroup(groupModel);

        WireMock.verify(1, WireMock.getRequestedFor(
            WireMock.urlPathEqualTo("/me/transitiveMemberOf/microsoft.graph.group"))
            .withQueryParam("$select", WireMock.equalTo("id,displayName,description,mail"))
        );
    }
}
