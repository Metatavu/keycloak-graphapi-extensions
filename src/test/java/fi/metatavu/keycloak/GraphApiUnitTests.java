package fi.metatavu.keycloak.graphapi;

import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.GroupModel;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphApiGroupsIdentityProviderMapperTest {

    @Test
    void testGetAzureGroups() throws Exception {
        // Arrange
        GraphApiGroupsIdentityProviderMapper mapper = Mockito.spy(new GraphApiGroupsIdentityProviderMapper());
        AccessTokenResponse token = Mockito.mock(AccessTokenResponse.class);

        TransitiveMemberOfGroup group1 = new TransitiveMemberOfGroup();
        group1.setDisplayName("Group 1");
        TransitiveMemberOfGroup group2 = new TransitiveMemberOfGroup();
        group2.setDisplayName(null);
        TransitiveMemberOfGroupsResponse response = new TransitiveMemberOfGroupsResponse();
        response.setValue(Arrays.asList(group1, group2));

        GraphApiClient clientMock = Mockito.mock(GraphApiClient.class);
        Mockito.doReturn(clientMock).when(mapper).createGraphApiClient();
        Mockito.when(clientMock.getTransitiveMemberOfGroups(token)).thenReturn(response);

        // Act
        List<TransitiveMemberOfGroup> result = mapper.getAzureGroups(token);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Group 1", result.get(0).getDisplayName());
    }

    @Test
    void testGetGroupMappings() {
        // Arrange
        GraphApiGroupsIdentityProviderMapper mapper = new GraphApiGroupsIdentityProviderMapper();

        IdentityProviderMapperModel mapperModel = Mockito.mock(IdentityProviderMapperModel.class);
        Map<String, List<String>> configMap = new HashMap<>();
        configMap.put("AzureGroup1", Arrays.asList("KeycloakGroupA", "KeycloakGroupB"));
        configMap.put("AzureGroup2", Arrays.asList("KeycloakGroupC"));
        Mockito.when(mapperModel.getConfigMap("graph-api-group-mapping")).thenReturn(configMap);

        // Act
        Map<String, List<String>> result = mapper.getGroupMappings(mapperModel);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("AzureGroup1"));
        assertTrue(result.containsKey("AzureGroup2"));
        assertEquals(Arrays.asList("KeycloakGroupA", "KeycloakGroupB"), result.get("AzureGroup1"));
        assertEquals(Arrays.asList("KeycloakGroupC"), result.get("AzureGroup2"));
    }

    @Test
    void testGetGroupPath() {
        GraphApiGroupsIdentityProviderMapper mapper = new GraphApiGroupsIdentityProviderMapper();

        // Mock GroupModel
        GroupModel group = Mockito.mock(GroupModel.class);
        Mockito.when(group.getId()).thenReturn("group1");
        Mockito.when(group.getName()).thenReturn("TestGroup");
        Mockito.when(group.getParentId()).thenReturn(null);

        // Create groupTree map
        Map<String, GroupModel> groupTree = new HashMap<>();
        groupTree.put("group1", group);

        // Call method
        String result = mapper.getGroupPath(groupTree, "group1");

        // Assert
        assertEquals("TestGroup", result);
    }

    @Test
    void testGetBrokerToken_validToken() throws Exception {
        GraphApiGroupsIdentityProviderMapper mapper = new GraphApiGroupsIdentityProviderMapper();
        BrokeredIdentityContext context = Mockito.mock(BrokeredIdentityContext.class);

        AccessTokenResponse expected = new AccessTokenResponse();
        expected.setToken("access-token");
        String json = new ObjectMapper().writeValueAsString(expected);

        Mockito.when(context.getToken()).thenReturn(json);

        AccessTokenResponse result = mapper.getBrokerToken(context);

        assertNotNull(result);
        assertEquals("access-token", result.getToken());
    }

    @Test
    void testGetBrokerToken_nullToken() {
        GraphApiGroupsIdentityProviderMapper mapper = new GraphApiGroupsIdentityProviderMapper();
        BrokeredIdentityContext context = Mockito.mock(BrokeredIdentityContext.class);

        Mockito.when(context.getToken()).thenReturn(null);

        AccessTokenResponse result = mapper.getBrokerToken(context);

        assertNull(result);
    }

    @Test
    void testGetBrokerToken_invalidJson() {
        GraphApiGroupsIdentityProviderMapper mapper = new GraphApiGroupsIdentityProviderMapper();
        BrokeredIdentityContext context = Mockito.mock(BrokeredIdentityContext.class);

        Mockito.when(context.getToken()).thenReturn("not-a-json");

        AccessTokenResponse result = mapper.getBrokerToken(context);

        assertNull(result);
    }
}