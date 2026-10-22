package fi.metatavu.keycloak.graphapi;

import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphApiGroupsIdentityProviderMapperTest {

    @Test
    void importNewUserJoinsMappedGroupsAndLeavesObsoleteManagedGroups() throws IOException {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);

        GroupModel financeGroup = group("g-finance", "Finance Members");
        GroupModel obsoleteGroup = group("g-obsolete", "Legacy Members");
        RealmModel realm = mock(RealmModel.class);
        when(realm.getGroupsStream()).thenReturn(Stream.of(financeGroup, obsoleteGroup));

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-1");
        when(user.getGroupsStream()).thenReturn(Stream.of(obsoleteGroup));

        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        when(mapperModel.getConfigMap("graph-api-group-mapping")).thenReturn(Map.of(
            "Finance+Group", List.of("Finance Members"),
            "Legacy+Group", List.of("Legacy Members")
        ));

        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn("{\"token\":\"broker-token\"}");
        when(brokeredIdentityContext.getAuthenticationSession()).thenReturn(authSession);

        TransitiveMemberOfGroupsResponse groupsResponse = new TransitiveMemberOfGroupsResponse();
        groupsResponse.setValue(List.of(groupResponse("Finance Group")));
        when(graphApiClient.getTransitiveMemberOfGroups(any())).thenReturn(groupsResponse);

        mapper.importNewUser(session, realm, user, mapperModel, brokeredIdentityContext);

        verify(user, times(1)).joinGroup(financeGroup);
        verify(user, times(1)).leaveGroup(obsoleteGroup);
        verify(authSession, times(1)).setAuthNote("USER_JOINING_GROUP_g-finance", "user-1");
        verify(authSession, times(1)).setAuthNote("USER_LEAVING_GROUP_g-obsolete", "user-1");
    }

    @Test
    void updateBrokeredUserMatchesPlainAndEncodedMappingKeys() throws IOException {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);

        GroupModel parentGroup = group("g-parent", "parent", null);
        GroupModel adminsGroup = group("g-admins", "Pääkäyttäjät", "g-parent");
        GroupModel supportGroup = group("g-support", "Support", "g-parent");
        GroupModel financeGroup = group("g-finance", "Finance Members", null);
        GroupModel salesGroup = group("g-sales", "Sales Members", null);
        GroupModel legacyGroup = group("g-legacy", "Legacy Members", null);
        RealmModel realm = mock(RealmModel.class);
        when(realm.getGroupsStream()).thenReturn(Stream.of(parentGroup, adminsGroup, supportGroup, financeGroup, salesGroup, legacyGroup));

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-1");
        when(user.getGroupsStream()).thenReturn(Stream.of(supportGroup, salesGroup, legacyGroup));

        // Plain keys are used by mappings configured for version 1.0.7 and URL-encoded keys by later versions
        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        when(mapperModel.getConfigMap("graph-api-group-mapping")).thenReturn(Map.of(
            "Ylläpitäjät Öljytiimi", List.of("parent/Pääkäyttäjät"),
            "support_team", List.of("parent/Support"),
            "Finance Group", List.of("Finance Members"),
            "Sales+Group", List.of("Sales Members"),
            "Legacy Group", List.of("Legacy Members")
        ));

        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn("{\"token\":\"broker-token\"}");
        when(brokeredIdentityContext.getAuthenticationSession()).thenReturn(authSession);

        TransitiveMemberOfGroupsResponse groupsResponse = new TransitiveMemberOfGroupsResponse();
        groupsResponse.setValue(List.of(
            groupResponse("Ylläpitäjät Öljytiimi"),
            groupResponse("support_team"),
            groupResponse(" Finance Group "),
            groupResponse("Sales Group")
        ));
        when(graphApiClient.getTransitiveMemberOfGroups(any())).thenReturn(groupsResponse);

        mapper.updateBrokeredUser(session, realm, user, mapperModel, brokeredIdentityContext);

        verify(user, times(1)).joinGroup(adminsGroup);
        verify(user, times(1)).joinGroup(financeGroup);
        verify(user, never()).joinGroup(supportGroup);
        verify(user, never()).joinGroup(salesGroup);
        verify(user, never()).leaveGroup(supportGroup);
        verify(user, never()).leaveGroup(salesGroup);
        verify(user, times(1)).leaveGroup(legacyGroup);
    }

    @Test
    void encodeGroupNamesEncodesNamesForStorage() {
        assertEquals(
            List.of("Finance+Group", "Yll%C3%A4pit%C3%A4j%C3%A4t+%C3%96ljytiimi", "support_team"),
            GraphApiMapperUtils.encodeGroupNames(List.of("Finance Group", "Ylläpitäjät Öljytiimi", "support_team", ""))
        );
    }

    @Test
    void updateBrokeredUserKeepsGroupsWhenAllWouldBeRemoved() throws IOException {
        GroupModel financeGroup = group("g-finance", "Finance Members");
        GroupModel salesGroup = group("g-sales", "Sales Members");
        UserModel user = updateBrokeredUserWithoutMappedAzureGroups(null, financeGroup, salesGroup);

        verify(user, never()).leaveGroup(any());
        verify(user, never()).joinGroup(any());
    }

    @Test
    void updateBrokeredUserRemovesAllGroupsWhenKeepingIsDisabled() throws IOException {
        GroupModel financeGroup = group("g-finance", "Finance Members");
        GroupModel salesGroup = group("g-sales", "Sales Members");
        UserModel user = updateBrokeredUserWithoutMappedAzureGroups(Map.of("keep-groups-when-all-would-be-removed", "false"), financeGroup, salesGroup);

        verify(user, times(1)).leaveGroup(financeGroup);
        verify(user, times(1)).leaveGroup(salesGroup);
    }

    @Test
    void updateBrokeredUserSkipsGroupSyncWhenTokenCannotReadGroups() throws IOException {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        GroupModel financeGroup = group("g-finance", "Finance Members");
        RealmModel realm = mock(RealmModel.class);
        when(realm.getGroupsStream()).thenReturn(Stream.of(financeGroup));

        UserModel user = mock(UserModel.class);
        when(user.getGroupsStream()).thenReturn(Stream.of(financeGroup));

        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        when(mapperModel.getConfigMap("graph-api-group-mapping")).thenReturn(Map.of("Finance Group", List.of("Finance Members")));

        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn(String.format("{\"access_token\":\"%s\"}", jwt("{\"scp\":\"email profile User.Read openid\"}")));
        when(brokeredIdentityContext.getAuthenticationSession()).thenReturn(mock(AuthenticationSessionModel.class));

        mapper.updateBrokeredUser(mock(KeycloakSession.class), realm, user, mapperModel, brokeredIdentityContext);

        verify(graphApiClient, never()).getTransitiveMemberOfGroups(any());
        verify(user, never()).joinGroup(any());
        verify(user, never()).leaveGroup(any());
    }

    @Test
    void importNewUserSkipsWhenBrokerTokenIsMissing() {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        KeycloakSession session = mock(KeycloakSession.class);
        RealmModel realm = mock(RealmModel.class);
        UserModel user = mock(UserModel.class);
        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn(null);
        when(brokeredIdentityContext.getAuthenticationSession()).thenReturn(mock(AuthenticationSessionModel.class));

        mapper.importNewUser(session, realm, user, mapperModel, brokeredIdentityContext);

        verify(user, never()).joinGroup(any());
        verify(user, never()).leaveGroup(any());
    }

    /**
     * Runs the mapper for a user who belongs to the given managed groups, when Graph API returns only an unmapped group
     *
     * @param config mapper configuration or null if not configured
     * @param userGroups managed groups the user belongs to
     * @return user
     */
    private static UserModel updateBrokeredUserWithoutMappedAzureGroups(Map<String, String> config, GroupModel... userGroups) throws IOException {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getAuthenticationSession()).thenReturn(authSession);

        RealmModel realm = mock(RealmModel.class);
        when(realm.getGroupsStream()).thenReturn(Stream.of(userGroups));

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-1");
        when(user.getGroupsStream()).thenReturn(Stream.of(userGroups));

        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        when(mapperModel.getConfig()).thenReturn(config);
        when(mapperModel.getConfigMap("graph-api-group-mapping")).thenReturn(Map.of(
            "Finance Group", List.of("Finance Members"),
            "Sales Group", List.of("Sales Members")
        ));

        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn("{\"token\":\"broker-token\"}");
        when(brokeredIdentityContext.getAuthenticationSession()).thenReturn(authSession);

        TransitiveMemberOfGroupsResponse groupsResponse = new TransitiveMemberOfGroupsResponse();
        groupsResponse.setValue(List.of(groupResponse("All Staff")));
        when(graphApiClient.getTransitiveMemberOfGroups(any())).thenReturn(groupsResponse);

        mapper.updateBrokeredUser(session, realm, user, mapperModel, brokeredIdentityContext);

        return user;
    }

    private static String jwt(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static GroupModel group(String id, String name) {
        return group(id, name, null);
    }

    private static GroupModel group(String id, String name, String parentId) {
        GroupModel group = mock(GroupModel.class);
        when(group.getId()).thenReturn(id);
        when(group.getName()).thenReturn(name);
        when(group.getParentId()).thenReturn(parentId);
        return group;
    }

    private static TransitiveMemberOfGroup groupResponse(String name) {
        TransitiveMemberOfGroup group = new TransitiveMemberOfGroup();
        group.setId(UUID.randomUUID());
        group.setDisplayName(name);
        return group;
    }

    private static class TestableGraphApiGroupsIdentityProviderMapper extends GraphApiGroupsIdentityProviderMapper {
        private final GraphApiClient graphApiClient;

        private TestableGraphApiGroupsIdentityProviderMapper(GraphApiClient graphApiClient) {
            this.graphApiClient = graphApiClient;
        }

        @Override
        protected GraphApiClient createGraphApiClient() {
            return graphApiClient;
        }
    }
}
