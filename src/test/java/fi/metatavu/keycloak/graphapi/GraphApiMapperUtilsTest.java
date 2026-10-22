package fi.metatavu.keycloak.graphapi;

import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.representations.AccessTokenResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphApiMapperUtilsTest {

    @Test
    void getAccessTokenClaimsReadsTokenPayload() {
        AccessTokenResponse accessToken = accessToken("{\"tid\":\"tenant-1\",\"oid\":\"object-1\",\"scp\":\"GroupMember.Read.All User.Read\"}");

        Map<String, Object> claims = GraphApiMapperUtils.getAccessTokenClaims(accessToken);

        assertEquals("tenant-1", claims.get("tid"));
        assertEquals("object-1", claims.get("oid"));
        assertEquals("GroupMember.Read.All User.Read", claims.get("scp"));
    }

    @Test
    void getAccessTokenClaimsReturnsEmptyClaimsForUnreadableToken() {
        AccessTokenResponse opaqueToken = new AccessTokenResponse();
        opaqueToken.setToken("opaque-token");
        AccessTokenResponse invalidPayloadToken = new AccessTokenResponse();
        invalidPayloadToken.setToken("header.not-base64-json!.signature");

        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(null));
        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(new AccessTokenResponse()));
        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(opaqueToken));
        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(invalidPayloadToken));
        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(accessToken("null")));
        assertEquals(Map.of(), GraphApiMapperUtils.getAccessTokenClaims(accessToken("[\"tid\"]")));
    }

    @Test
    void describeLoginDescribesIdentityProviderUserAndTokenWithoutToken() {
        IdentityProviderModel identityProvider = new IdentityProviderModel();
        identityProvider.setAlias("entra");
        BrokeredIdentityContext context = mock(BrokeredIdentityContext.class);
        when(context.getIdpConfig()).thenReturn(identityProvider);
        when(context.getId()).thenReturn("broker-user-1");
        when(context.getUsername()).thenReturn("user@example.com");
        AccessTokenResponse accessToken = accessToken("{\"tid\":\"tenant-1\",\"oid\":\"object-1\",\"scp\":\"User.Read\"}");

        String description = GraphApiMapperUtils.describeLogin(context, accessToken);

        assertEquals("idp=entra, brokerUserId=broker-user-1, brokerUsername=user@example.com, tenantId=tenant-1, objectId=object-1, scopes=User.Read", description);
    }

    @Test
    void isMissingGroupReadPermissionChecksTokenScopes() {
        assertTrue(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"scp\":\"email profile User.Read openid\"}")));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"scp\":\"email Group.Read.All profile User.Read openid\"}")));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"scp\":\"GroupMember.Read.All\"}")));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"scp\":\"openid directory.read.all\"}")));
    }

    @Test
    void isMissingGroupReadPermissionIgnoresTokensWithoutScopes() {
        AccessTokenResponse opaqueToken = new AccessTokenResponse();
        opaqueToken.setToken("opaque-token");

        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(null));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(opaqueToken));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"tid\":\"tenant-1\"}")));
        assertFalse(GraphApiMapperUtils.isMissingGroupReadPermission(accessToken("{\"scp\":[\"User.Read\"]}")));
    }

    @Test
    void hasOnlyGroupsWithoutDisplayNameDetectsGroupsWithIdsOnly() {
        assertTrue(GraphApiMapperUtils.hasOnlyGroupsWithoutDisplayName(List.of(group(null), group(null))));
        assertFalse(GraphApiMapperUtils.hasOnlyGroupsWithoutDisplayName(List.of(group(null), group("Finance Group"))));
        assertFalse(GraphApiMapperUtils.hasOnlyGroupsWithoutDisplayName(List.of()));
    }

    private static TransitiveMemberOfGroup group(String displayName) {
        TransitiveMemberOfGroup group = new TransitiveMemberOfGroup();
        group.setDisplayName(displayName);
        return group;
    }

    private static AccessTokenResponse accessToken(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        AccessTokenResponse accessToken = new AccessTokenResponse();
        accessToken.setToken(String.format(
            "%s.%s.signature",
            encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)),
            encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        ));
        return accessToken;
    }
}
