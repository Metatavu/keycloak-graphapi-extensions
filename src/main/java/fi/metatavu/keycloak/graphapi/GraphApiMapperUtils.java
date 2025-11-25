package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessTokenResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared helpers for Graph API mappers to avoid duplicated logic.
 */
final class GraphApiMapperUtils {

    private GraphApiMapperUtils() {
    }

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

    static void updateUserAttribute(UserModel user, String attributeName, Object value) {
        if (value == null) {
            user.removeAttribute(attributeName);
        } else if (value instanceof List) {
            user.setAttribute(attributeName, (List<String>) value);
        } else {
            user.setSingleAttribute(attributeName, value.toString());
        }
    }

    static GraphUser fetchGraphUser(BrokeredIdentityContext context, Logger logger, String cacheKey, GraphUserFetcher fetcher) {
        String cachedUser = context.getAuthenticationSession().getAuthNote(cacheKey);
        if (cachedUser != null) {
            try {
                return new ObjectMapper().readValue(cachedUser, GraphUser.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse cached user", e);
            }
        }

        AccessTokenResponse brokerToken = parseBrokerToken(context, logger);
        if (brokerToken == null) {
            logger.warn("Broker token is null, cannot retrieve user");
            return null;
        }

        GraphUser graphUser;
        try {
            graphUser = fetcher.fetch(brokerToken);
        } catch (Exception e) {
            logger.error("Failed to get user", e);
            return null;
        }

        if (graphUser != null) {
            try {
                context.getAuthenticationSession().setAuthNote(cacheKey, new ObjectMapper().writeValueAsString(graphUser));
            } catch (JsonProcessingException e) {
                logger.error("Failed to cache user", e);
            }
        }

        return graphUser;
    }

    static void applyAttributeMapping(GraphUser graphUser, String sourceAttribute, String keycloakAttribute, UserModel userModel, Map<String, Function<GraphUser, Object>> mapping, Logger logger) {
        Function<GraphUser, Object> extractor = mapping.get(sourceAttribute);
        if (extractor == null) {
            logger.warnf("Unsupported Graph API user attribute: %s", sourceAttribute);
            return;
        }

        updateUserAttribute(userModel, keycloakAttribute, extractor.apply(graphUser));
    }

    @FunctionalInterface
    interface GraphUserFetcher {
        GraphUser fetch(AccessTokenResponse accessToken) throws Exception;
    }
}
