package fi.metatavu.keycloak.graphapi;

import org.junit.jupiter.api.Test;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.representations.AccessTokenResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
