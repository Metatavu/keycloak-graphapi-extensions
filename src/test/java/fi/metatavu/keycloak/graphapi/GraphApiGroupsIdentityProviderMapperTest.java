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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(mapperModel.getConfigMap(eq("graph-api-group-mapping"))).thenReturn(Map.of(
            "Finance+Group", List.of("Finance Members"),
            "Legacy+Group", List.of("Legacy Members")
        ));

        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn("{\"token\":\"broker-token\"}");

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
    void importNewUserSkipsWhenBrokerTokenIsMissing() {
        GraphApiClient graphApiClient = mock(GraphApiClient.class);
        GraphApiGroupsIdentityProviderMapper mapper = new TestableGraphApiGroupsIdentityProviderMapper(graphApiClient);

        KeycloakSession session = mock(KeycloakSession.class);
        RealmModel realm = mock(RealmModel.class);
        UserModel user = mock(UserModel.class);
        IdentityProviderMapperModel mapperModel = mock(IdentityProviderMapperModel.class);
        BrokeredIdentityContext brokeredIdentityContext = mock(BrokeredIdentityContext.class);
        when(brokeredIdentityContext.getToken()).thenReturn(null);

        mapper.importNewUser(session, realm, user, mapperModel, brokeredIdentityContext);

        verify(user, never()).joinGroup(any());
        verify(user, never()).leaveGroup(any());
    }

    private static GroupModel group(String id, String name) {
        GroupModel group = mock(GroupModel.class);
        when(group.getId()).thenReturn(id);
        when(group.getName()).thenReturn(name);
        when(group.getParentId()).thenReturn(null);
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
