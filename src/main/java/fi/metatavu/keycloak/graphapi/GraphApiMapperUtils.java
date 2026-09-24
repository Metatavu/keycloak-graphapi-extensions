package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared helpers for Graph API mappers to avoid duplicated logic.
 */
final class GraphApiMapperUtils {

    static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};

    private static final String UNAVAILABLE_USER_NOTE = "unavailable";

    private GraphApiMapperUtils() {
    }

    /**
     * Parses the broker token from the context.
     */
    static AccessTokenResponse parseBrokerToken(BrokeredIdentityContext context, Logger logger) {
        String token = context.getToken();
        if (token == null) {
            return null;
        }

        try {
            return new ObjectMapper().readValue(token, AccessTokenResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse token", e);
            return null;
        }
    }

    /**
     * Updates a user attribute in Keycloak.
     */
    static void updateUserAttribute(UserModel user, String attributeName, Object value) {
        if (value == null) {
            user.removeAttribute(attributeName);
        } else if (value instanceof List) {
            user.setAttribute(attributeName, (List<String>) value);
        } else {
            user.setSingleAttribute(attributeName, value.toString());
        }
    }

    /**
     * URL-encodes a value for storage to avoid utf8mb4 characters.
     */
    static String encodeForStorage(String value) {
        if (value == null) {
            return null;
        }

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Fetches a GraphUser from the context or by calling the fetcher.
     *
     * Result is cached in the authentication session, so that the Graph API is called only once per login
     * regardless of how many mappers use the same user. Unavailable user (request failure or not found) is
     * cached as well to prevent every mapper from repeating a request that already failed during the same login.
     */
    static GraphUser fetchGraphUser(BrokeredIdentityContext context, Logger logger, String cacheKey, GraphUserFetcher fetcher) {
        AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();
        String cachedUser = authenticationSession.getAuthNote(cacheKey);
        if (UNAVAILABLE_USER_NOTE.equals(cachedUser)) {
            return null;
        }

        if (cachedUser != null) {
            try {
                return new ObjectMapper().readValue(cachedUser, GraphUser.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse cached user", e);
            }
        }

        GraphUser graphUser = requestGraphUser(context, logger, fetcher);
        if (graphUser == null) {
            authenticationSession.setAuthNote(cacheKey, UNAVAILABLE_USER_NOTE);
            return null;
        }

        try {
            authenticationSession.setAuthNote(cacheKey, new ObjectMapper().writeValueAsString(graphUser));
        } catch (JsonProcessingException e) {
            logger.error("Failed to cache user", e);
        }

        return graphUser;
    }

    /**
     * Requests a GraphUser from the Graph API using the fetcher.
     *
     * @return GraphUser or null if request failed or user was not found
     */
    private static GraphUser requestGraphUser(BrokeredIdentityContext context, Logger logger, GraphUserFetcher fetcher) {
        AccessTokenResponse brokerToken = parseBrokerToken(context, logger);
        if (brokerToken == null) {
            logger.warn("Broker token is null, cannot retrieve user");
            return null;
        }

        try {
            return fetcher.fetch(brokerToken);
        } catch (IOException e) {
            logger.error("Failed to get user", e);
            return null;
        }
    }

    /**
     * Applies attribute mapping from a GraphUser to a Keycloak UserModel.
     */
    static void applyAttributeMapping(GraphUser graphUser, String sourceAttribute, String keycloakAttribute, UserModel userModel, Map<String, Function<GraphUser, Object>> mapping, Logger logger) {
        Function<GraphUser, Object> extractor = mapping.get(sourceAttribute);
        if (extractor == null) {
            logger.warnf("Unsupported Graph API user attribute: %s", sourceAttribute);
            return;
        }

        updateUserAttribute(userModel, keycloakAttribute, extractor.apply(graphUser));
    }

    /**
     * Builds configuration properties for the Graph API and Keycloak mapping.
     */
    static List<ProviderConfigProperty> buildConfigProperties(String graphApiConfigName, String graphApiLabel, String graphApiHelp, List<String> options, String keycloakConfigName, String keycloakLabel, String keycloakHelp) {
        ProviderConfigProperty graphApiProperty = new ProviderConfigProperty();
        graphApiProperty.setName(graphApiConfigName);
        graphApiProperty.setLabel(graphApiLabel);
        graphApiProperty.setHelpText(graphApiHelp);
        graphApiProperty.setType(ProviderConfigProperty.LIST_TYPE);
        graphApiProperty.setOptions(options);

        ProviderConfigProperty keycloakProperty = new ProviderConfigProperty();
        keycloakProperty.setName(keycloakConfigName);
        keycloakProperty.setLabel(keycloakLabel);
        keycloakProperty.setHelpText(keycloakHelp);
        keycloakProperty.setType(ProviderConfigProperty.USER_PROFILE_ATTRIBUTE_LIST_TYPE);

        List<ProviderConfigProperty> config = new ArrayList<>();
        config.add(graphApiProperty);
        config.add(keycloakProperty);
        return Collections.unmodifiableList(config);
    }

    @FunctionalInterface
    interface GraphUserFetcher {
        GraphUser fetch(AccessTokenResponse accessToken) throws IOException;
    }
}
