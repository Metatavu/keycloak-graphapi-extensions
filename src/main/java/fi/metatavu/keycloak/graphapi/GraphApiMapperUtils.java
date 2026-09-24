package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
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
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared helpers for Graph API mappers to avoid duplicated logic.
 */
final class GraphApiMapperUtils {

    static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};

    private static final String UNAVAILABLE_NOTE = "unavailable";
    private static final String USER_GROUP_NAMES_NOTE = "graph-api-user-group-names";

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
     */
    static GraphUser fetchGraphUser(BrokeredIdentityContext context, Logger logger, String cacheKey, GraphResourceFetcher<GraphUser> fetcher) {
        return fetchCached(context, logger, cacheKey, GraphUser.class, fetcher);
    }

    /**
     * Fetches names of the logged user's groups from the context or from the Graph API.
     *
     * @return group names or null if groups could not be retrieved
     */
    static List<String> fetchUserGroupNames(BrokeredIdentityContext context, GraphApiClient graphApiClient, Logger logger) {
        String[] groupNames = fetchCached(context, logger, USER_GROUP_NAMES_NOTE, String[].class, accessToken -> {
            TransitiveMemberOfGroupsResponse response = graphApiClient.getTransitiveMemberOfGroups(accessToken);
            if (response == null || response.getValue() == null) {
                return null;
            }

            return toGroupNames(response.getValue()).toArray(String[]::new);
        });

        return groupNames == null ? null : List.of(groupNames);
    }

    /**
     * Converts Graph API groups into group names suitable for storage. Groups without a display name are skipped.
     */
    static List<String> toGroupNames(List<TransitiveMemberOfGroup> groups) {
        return groups.stream()
            .map(TransitiveMemberOfGroup::getDisplayName)
            .filter(Objects::nonNull)
            .map(GraphApiMapperUtils::encodeForStorage)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();
    }

    /**
     * Fetches a Graph API resource from the context or by calling the fetcher.
     *
     * Result is cached in the authentication session, so that the Graph API is called only once per login
     * regardless of how many mappers use the same resource. Unavailable resource (request failure or not found) is
     * cached as well to prevent every mapper from repeating a request that already failed during the same login.
     */
    private static <T> T fetchCached(BrokeredIdentityContext context, Logger logger, String cacheKey, Class<T> type, GraphResourceFetcher<T> fetcher) {
        AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();
        String cachedValue = authenticationSession.getAuthNote(cacheKey);
        if (UNAVAILABLE_NOTE.equals(cachedValue)) {
            return null;
        }

        if (cachedValue != null) {
            try {
                return new ObjectMapper().readValue(cachedValue, type);
            } catch (JsonProcessingException e) {
                logger.errorf(e, "Failed to parse cached Graph API resource %s", cacheKey);
            }
        }

        T value = requestGraphResource(context, logger, cacheKey, fetcher);
        if (value == null) {
            authenticationSession.setAuthNote(cacheKey, UNAVAILABLE_NOTE);
            return null;
        }

        try {
            authenticationSession.setAuthNote(cacheKey, new ObjectMapper().writeValueAsString(value));
        } catch (JsonProcessingException e) {
            logger.errorf(e, "Failed to cache Graph API resource %s", cacheKey);
        }

        return value;
    }

    /**
     * Requests a Graph API resource using the fetcher.
     *
     * @return resource or null if request failed or resource was not found
     */
    private static <T> T requestGraphResource(BrokeredIdentityContext context, Logger logger, String resourceName, GraphResourceFetcher<T> fetcher) {
        AccessTokenResponse brokerToken = parseBrokerToken(context, logger);
        if (brokerToken == null) {
            logger.warnf("Broker token is null, cannot retrieve Graph API resource %s", resourceName);
            return null;
        }

        try {
            return fetcher.fetch(brokerToken);
        } catch (IOException e) {
            logger.errorf(e, "Failed to get Graph API resource %s", resourceName);
            return null;
        }
    }

    /**
     * Applies attribute mapping from a GraphUser to a Keycloak UserModel.
     */
    static void applyAttributeMapping(GraphUser graphUser, String sourceAttribute, String keycloakAttribute, UserModel userModel, Map<String, Function<GraphUser, Object>> mapping, Logger logger) {
        Function<GraphUser, Object> extractor = mapping.get(sourceAttribute);
        if (extractor == null) {
            extractor = resolveExtractorByAlias(sourceAttribute, mapping, logger);
        }

        if (extractor == null) {
            logger.warnf("Unsupported Graph API user attribute: %s", sourceAttribute);
            return;
        }

        updateUserAttribute(userModel, keycloakAttribute, extractor.apply(graphUser));
    }

    /**
     * Resolves extractor by normalized alias when mapper configuration uses legacy labels
     * or raw Graph field names (for example "costCenter" instead of "User Cost Center").
     */
    private static Function<GraphUser, Object> resolveExtractorByAlias(String sourceAttribute, Map<String, Function<GraphUser, Object>> mapping, Logger logger) {
        if (sourceAttribute == null || sourceAttribute.isBlank()) {
            return null;
        }

        String normalizedSource = normalizeAttributeName(sourceAttribute);
        List<Map.Entry<String, Function<GraphUser, Object>>> matches = mapping.entrySet()
            .stream()
            .filter(entry -> {
                String normalizedKey = normalizeAttributeName(entry.getKey());
                return normalizedKey.equals(normalizedSource) || normalizedKey.endsWith(normalizedSource);
            })
            .toList();

        if (matches.size() == 1) {
            Map.Entry<String, Function<GraphUser, Object>> match = matches.getFirst();
            logger.infof("Resolved Graph API user attribute alias '%s' -> '%s'", sourceAttribute, match.getKey());
            return match.getValue();
        }

        return null;
    }

    /**
     * Normalizes a mapping key for case/spacing/punctuation-insensitive comparisons.
     */
    private static String normalizeAttributeName(String attributeName) {
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < attributeName.length(); i++) {
            char character = attributeName.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }

        return normalized.toString();
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
    interface GraphResourceFetcher<T> {
        T fetch(AccessTokenResponse accessToken) throws IOException;
    }
}
