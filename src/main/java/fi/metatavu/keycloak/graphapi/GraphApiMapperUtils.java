package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final String USER_GROUP_DISPLAY_NAMES_NOTE = "graph-api-user-group-display-names";
    private static final int MAX_LOGGED_RESPONSE_LENGTH = 20000;

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
     * Fetches display names of the logged user's groups from the context or from the Graph API.
     *
     * Names are returned as received from the Graph API. Use {@link #encodeGroupNames(List)} to convert them into
     * names suitable for storage.
     *
     * @return group display names or null if groups could not be retrieved
     */
    static List<String> fetchUserGroupDisplayNames(BrokeredIdentityContext context, GraphApiClient graphApiClient, Logger logger) {
        String[] displayNames = fetchCached(context, logger, USER_GROUP_DISPLAY_NAMES_NOTE, String[].class, accessToken -> {
            TransitiveMemberOfGroupsResponse response = graphApiClient.getTransitiveMemberOfGroups(accessToken);
            if (response == null || response.getValue() == null) {
                logger.warnf("Graph API user groups not found [%s]", describeLogin(context, accessToken));
                return null;
            }

            List<String> groupDisplayNames = toGroupDisplayNames(response.getValue());
            logUserGroupsResponse(context, accessToken, response, groupDisplayNames, logger);
            return groupDisplayNames.toArray(String[]::new);
        });

        return displayNames == null ? null : List.of(displayNames);
    }

    /**
     * Logs a summary of the user groups received from Graph API.
     *
     * Response body is logged as well when the groups may not be complete: no groups, groups without a display name
     * or more groups available than received. This allows finding out afterwards whether Graph API returned no groups
     * or groups with partial data.
     */
    static void logUserGroupsResponse(BrokeredIdentityContext context, AccessTokenResponse accessToken, TransitiveMemberOfGroupsResponse response, List<String> groupDisplayNames, Logger logger) {
        int groupCount = response.getValue().size();
        Integer receivedCount = response.getReceivedCount();
        Integer odataCount = response.getOdataCount();
        boolean hasNextPage = response.getOdataNextLink() != null;

        String summary = String.format(
            "%s, requestId=%s, received=%s, filteredLocally=%s, withoutDisplayName=%d, groups=%d, odataCount=%s, nextPage=%s",
            describeLogin(context, accessToken),
            response.getRequestId(),
            receivedCount,
            receivedCount == null ? null : receivedCount - groupCount,
            groupCount - groupDisplayNames.size(),
            groupDisplayNames.size(),
            odataCount,
            hasNextPage
        );

        logger.infof("Graph API user groups [%s]", summary);

        List<String> reasons = new ArrayList<>();
        if (groupDisplayNames.isEmpty()) {
            reasons.add("no-groups");
        }

        if (groupDisplayNames.size() < groupCount) {
            reasons.add("groups-without-display-name");
        }

        if (hasNextPage || (odataCount != null && receivedCount != null && odataCount > receivedCount)) {
            reasons.add("more-groups-available");
        }

        if (!reasons.isEmpty()) {
            logger.warnf(
                "Graph API user groups may be incomplete [reasons=%s, %s], response: %s",
                String.join(",", reasons),
                summary,
                truncate(toSingleLine(response.getRawBody()), MAX_LOGGED_RESPONSE_LENGTH)
            );
        }
    }

    /**
     * Describes the login for diagnostic logging: identity provider, brokered user and the Entra tenant, object id
     * and scopes of the access token. The token itself is never logged.
     */
    static String describeLogin(BrokeredIdentityContext context, AccessTokenResponse accessToken) {
        IdentityProviderModel identityProvider = context.getIdpConfig();
        Map<String, Object> claims = getAccessTokenClaims(accessToken);

        return String.format(
            "idp=%s, brokerUserId=%s, brokerUsername=%s, tenantId=%s, objectId=%s, scopes=%s",
            identityProvider == null ? null : identityProvider.getAlias(),
            context.getId(),
            context.getUsername(),
            claims.get("tid"),
            claims.get("oid"),
            claims.get("scp")
        );
    }

    /**
     * Returns claims of the access token.
     *
     * Claims are read only for diagnostic logging and the token signature is not verified.
     *
     * @return claims or empty map if token is missing or it could not be read
     */
    static Map<String, Object> getAccessTokenClaims(AccessTokenResponse accessToken) {
        if (accessToken == null || accessToken.getToken() == null) {
            return Map.of();
        }

        String[] parts = accessToken.getToken().split("\\.");
        if (parts.length < 2) {
            return Map.of();
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return new ObjectMapper().readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    /**
     * Removes line breaks and indentation from a JSON value, so that it is logged as a single line. JSON strings
     * cannot contain line breaks, so the value itself does not change.
     */
    private static String toSingleLine(String json) {
        if (json == null) {
            return null;
        }

        return json.replaceAll("\\s*\\R\\s*", "");
    }

    /**
     * Truncates a value to the maximum length.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * Returns display names of Graph API groups. Groups without a display name are skipped.
     */
    static List<String> toGroupDisplayNames(List<TransitiveMemberOfGroup> groups) {
        return groups.stream()
            .map(TransitiveMemberOfGroup::getDisplayName)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Converts Graph API groups into group names suitable for storage. Groups without a display name are skipped.
     */
    static List<String> toGroupNames(List<TransitiveMemberOfGroup> groups) {
        return encodeGroupNames(toGroupDisplayNames(groups));
    }

    /**
     * Converts group display names into group names suitable for storage. Names that are empty after encoding are skipped.
     */
    static List<String> encodeGroupNames(List<String> displayNames) {
        return displayNames.stream()
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
            logger.warnf("Broker token is null, cannot retrieve Graph API resource %s [%s]", resourceName, describeLogin(context, null));
            return null;
        }

        try {
            return fetcher.fetch(brokerToken);
        } catch (IOException e) {
            logger.errorf(e, "Failed to get Graph API resource %s [%s]", resourceName, describeLogin(context, brokerToken));
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
