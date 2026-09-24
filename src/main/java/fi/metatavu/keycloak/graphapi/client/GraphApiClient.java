package fi.metatavu.keycloak.graphapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import fi.metatavu.keycloak.graphapi.model.GraphProfilePosition;
import fi.metatavu.keycloak.graphapi.model.GraphProfilePositionsResponse;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.representations.AccessTokenResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Microsoft Graph API client
 */
public class GraphApiClient {
    private static final Logger logger = Logger.getLogger(GraphApiClient.class);
    private static final String CONSISTENCY_LEVEL_HEADER = "ConsistencyLevel";
    private static final String GROUPS_SELECT_FIELDS = "id,displayName,description,mail,groupTypes,resourceProvisioningOptions";
    private static final String FILTERED_GROUPS_QUERY_SUFFIX = String.format(
        "?$count=true&$select=%s&$filter=securityEnabled%%20eq%%20true%%20and%%20not(groupTypes/any(c:c%%20eq%%20'Unified'))",
        GROUPS_SELECT_FIELDS
    );
    private static final String USER_SELECT_FIELDS = String.join(",",
        "id",
        "businessPhones",
        "displayName",
        "companyName",
        "department",
        "costCenter",
        "givenName",
        "jobTitle",
        "mail",
        "mobilePhone",
        "officeLocation",
        "preferredLanguage",
        "surname",
        "userPrincipalName"
    );

    private static final Duration CONNECT_TIMEOUT = getTimeout("GRAPH_API_CONNECT_TIMEOUT_SECONDS", Duration.ofSeconds(5));
    private static final Duration REQUEST_TIMEOUT = getTimeout("GRAPH_API_REQUEST_TIMEOUT_SECONDS", Duration.ofSeconds(15));

    // HttpClient is thread-safe and shared to reuse connections between requests
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /**
     * Returns logged user's membership of groups
     *
     * @param accessToken access token
     * @return logged user's membership of groups
     * @throws IOException thrown when request fails
     */
    public TransitiveMemberOfGroupsResponse getTransitiveMemberOfGroups(AccessTokenResponse accessToken) throws IOException {
        TransitiveMemberOfGroupsResponse response = getGraphApiResource(
            accessToken,
            "me/transitiveMemberOf/microsoft.graph.group" + FILTERED_GROUPS_QUERY_SUFFIX,
            TransitiveMemberOfGroupsResponse.class,
            Map.of(CONSISTENCY_LEVEL_HEADER, "eventual")
        );

        return excludeUnifiedAndTeamsGroups(response, "me/transitiveMemberOf");
    }

    /**
     * Returns user's membership of groups by user id
     *
     * @param accessToken access token
     * @param userId user id
     * @return user's membership of groups
     * @throws IOException thrown when request fails
     */
    public TransitiveMemberOfGroupsResponse getTransitiveMemberOfGroupsForUser(AccessTokenResponse accessToken, String userId) throws IOException {
        TransitiveMemberOfGroupsResponse response = getGraphApiResource(
            accessToken,
            String.format("users/%s/transitiveMemberOf/microsoft.graph.group%s", userId, FILTERED_GROUPS_QUERY_SUFFIX),
            TransitiveMemberOfGroupsResponse.class,
            Map.of(CONSISTENCY_LEVEL_HEADER, "eventual")
        );

        return excludeUnifiedAndTeamsGroups(response, String.format("users/%s/transitiveMemberOf", userId));
    }

    /**
     * Applies local fallback filtering for Unified/Teams groups in case server-side filtering
     * behaves differently between Graph endpoints.
     */
    private TransitiveMemberOfGroupsResponse excludeUnifiedAndTeamsGroups(TransitiveMemberOfGroupsResponse response, String path) {
        if (response == null || response.getValue() == null) {
            return response;
        }

        List<TransitiveMemberOfGroup> filteredGroups = response.getValue()
            .stream()
            .filter(Objects::nonNull)
            .filter(this::isNotUnifiedOrTeamGroup)
            .toList();

        int excludedCount = response.getValue().size() - filteredGroups.size();
        if (excludedCount > 0) {
            logger.infof("Graph groups filtered locally [path=%s, excluded=%d]", path, excludedCount);
        }

        response.setValue(filteredGroups);
        return response;
    }

    private boolean isNotUnifiedOrTeamGroup(TransitiveMemberOfGroup group) {
        return !containsIgnoreCase(group.getGroupTypes(), "Unified")
            && !containsIgnoreCase(group.getResourceProvisioningOptions(), "Team");
    }

    private boolean containsIgnoreCase(List<String> values, String expectedValue) {
        return values != null && values.stream().anyMatch(value -> value != null && expectedValue.equalsIgnoreCase(value));
    }

    /**
     * Returns logged user's manager
     *
     * @param accessToken access token
     * @return logged user's manager
     * @throws IOException thrown when request fails
     */
    public GraphUser getManager(AccessTokenResponse accessToken) throws IOException {
        GraphUser manager = getGraphApiResource(accessToken, String.format("me/manager?$select=%s", USER_SELECT_FIELDS), GraphUser.class);
        if (manager == null || manager.getId() == null) {
            return manager;
        }

        return enrichWithProfileCompany(accessToken, manager, String.format("users/%s/profile/positions?$top=1", manager.getId()));
    }

    /**
     * Returns logged user
     *
     * @param accessToken access token
     * @return logged user
     * @throws IOException thrown when request fails
     */
    public GraphUser getUser(AccessTokenResponse accessToken) throws IOException {
        GraphUser user = getGraphApiResource(accessToken, String.format("me?$select=%s", USER_SELECT_FIELDS), GraphUser.class);
        if (user == null) {
            return null;
        }

        return enrichWithProfileCompany(accessToken, user, "me/profile/positions?$top=1");
    }

    /**
     * Populates companyName, department, and costCenter from profile positions when top-level fields are missing.
     *
     * @param accessToken access token
     * @param user user to enrich
     * @param profilePath profile positions endpoint path
     * @return enriched user
     */
    private GraphUser enrichWithProfileCompany(AccessTokenResponse accessToken, GraphUser user, String profilePath) {
        logger.infof(
            "Graph profile enrichment start [path=%s, userId=%s, companyName='%s', department='%s', costCenter='%s']",
            profilePath,
            user.getId(),
            user.getCompanyName(),
            user.getDepartment(),
            user.getCostCenter()
        );

        if (hasValue(user.getCompanyName()) && hasValue(user.getDepartment()) && hasValue(user.getCostCenter())) {
            logger.infof(
                "Graph profile enrichment skipped [path=%s, userId=%s, reason=top-level-fields-present]",
                profilePath,
                user.getId()
            );
            return user;
        }

        GraphProfilePosition profilePosition = getLatestProfilePosition(accessToken, profilePath);
        if (profilePosition == null || profilePosition.getDetail() == null || profilePosition.getDetail().getCompany() == null) {
            logger.infof(
                "Graph profile enrichment no company data [path=%s, userId=%s]",
                profilePath,
                user.getId()
            );
            return user;
        }

        String profileCompanyName = profilePosition.getDetail().getCompany().getDisplayName();
        String profileDepartment = profilePosition.getDetail().getCompany().getDepartment();
        String profileCostCenter = profilePosition.getDetail().getCompany().getCostCenter();
        logger.infof(
            "Graph profile company data [path=%s, userId=%s, profileCompanyName='%s', profileDepartment='%s', profileCostCenter='%s']",
            profilePath,
            user.getId(),
            profileCompanyName,
            profileDepartment,
            profileCostCenter
        );

        if (!hasValue(user.getCompanyName()) && hasValue(profileCompanyName)) {
            user.setCompanyName(profileCompanyName);
        }

        if (!hasValue(user.getDepartment()) && hasValue(profileDepartment)) {
            user.setDepartment(profileDepartment);
        }

        if (!hasValue(user.getCostCenter()) && hasValue(profileCostCenter)) {
            user.setCostCenter(profileCostCenter);
        }

        logger.infof(
            "Graph profile enrichment result [path=%s, userId=%s, companyName='%s', department='%s', costCenter='%s']",
            profilePath,
            user.getId(),
            user.getCompanyName(),
            user.getDepartment(),
            user.getCostCenter()
        );

        return user;
    }

    /**
     * Returns whether a text value is non-null and contains non-whitespace characters.
     *
     * @param value text value
     * @return true when value has non-whitespace content
     */
    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Returns first profile position from profile positions endpoint.
     *
     * @param accessToken access token
     * @param profilePath profile positions endpoint path
     * @return first profile position or null
     */
    private GraphProfilePosition getLatestProfilePosition(AccessTokenResponse accessToken, String profilePath) {
        try {
            GraphProfilePositionsResponse response = getGraphApiResource(accessToken, profilePath, GraphProfilePositionsResponse.class);
            if (response == null || response.getValue() == null || response.getValue().isEmpty()) {
                logger.infof("Graph profile positions empty [path=%s]", profilePath);
                return null;
            }

            logger.infof(
                "Graph profile positions fetched [path=%s, count=%d]",
                profilePath,
                response.getValue().size()
            );
            return response.getValue().getFirst();
        } catch (IOException e) {
            // Profile permissions vary per tenant, so fallback must be best-effort.
            logger.warnf(
                "Graph profile positions fetch failed [path=%s, error=%s]",
                profilePath,
                e.getMessage()
            );
            return null;
        }
    }

    /**
     * Fetches a resource from the Microsoft Graph API.
     *
     * @param accessToken access token
     * @param path API path
     * @param clazz target class
     * @return resource
     * @throws IOException thrown when request fails or times out
     */
    private <T> T getGraphApiResource(AccessTokenResponse accessToken, String path, Class<T> clazz) throws IOException {
        return getGraphApiResource(accessToken, path, clazz, Map.of());
    }

    /**
     * Fetches a resource from the Microsoft Graph API with optional request headers.
     *
     * Request timeout covers the whole exchange including reading the response body, because
     * HttpRequest timeout alone covers only receiving the response headers.
     *
     * @param accessToken access token
     * @param path API path
     * @param clazz target class
     * @param headers extra HTTP headers
     * @return resource
     * @throws IOException thrown when request fails or times out
     */
    private <T> T getGraphApiResource(AccessTokenResponse accessToken, String path, Class<T> clazz, Map<String, String> headers) throws IOException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/%s", getGraphApiUrl(), path)))
                .header("Authorization", "Bearer " + accessToken.getToken())
                .timeout(REQUEST_TIMEOUT);

        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.build();

        CompletableFuture<HttpResponse<byte[]>> responseFuture = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = responseFuture.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return handleResponse(response, clazz);
        } catch (TimeoutException e) {
            responseFuture.cancel(true);
            throw new HttpTimeoutException(String.format("Request to %s timed out", path));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }

            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /**
     * Handles the HTTP response from the Microsoft Graph API.
     *
     * @param response HTTP response
     * @param clazz target class
     * @return resource
     * @throws IOException thrown when response handling fails
     */
    private <T> T handleResponse(HttpResponse<byte[]> response, Class<T> clazz) throws IOException {
        int statusCode = response.statusCode();

        if (statusCode == 200) {
            return deserialize(response.body(), clazz);
        } else if (statusCode == 404) {
            return null;
        } else {
            throw new IOException(String.format("Failed to execute: %s", statusCode));
        }
    }

    /**
     * Deserializes JSON to object
     *
     * @param json JSON bytes
     * @param clazz target class
     * @return deserialized object
     * @param <T> target class type
     * @throws IOException thrown when deserialization fails
     */
    @SuppressWarnings("SameParameterValue")
    private <T> T deserialize(byte[] json, Class<T> clazz) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, clazz);
    }

    /**
     * Returns timeout from environment variable or default timeout if variable is not set or is invalid
     *
     * @param variableName environment variable name containing timeout in seconds
     * @param defaultTimeout default timeout
     * @return timeout
     */
    private static Duration getTimeout(String variableName, Duration defaultTimeout) {
        String value = System.getenv(variableName);
        if (value == null || value.isBlank()) {
            return defaultTimeout;
        }

        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds > 0) {
                return Duration.ofSeconds(seconds);
            }
        } catch (NumberFormatException e) {
            // Invalid value is reported below
        }

        logger.warnf("Invalid value '%s' in %s, using default %d seconds", value, variableName, defaultTimeout.toSeconds());
        return defaultTimeout;
    }

    /**
     * Returns base URL for Microsoft Graph API
     *
     * @return base URL for Microsoft Graph API
     */
    private String getGraphApiUrl() {
        if (System.getenv("GRAPH_API_URL") != null) {
            return System.getenv("GRAPH_API_URL");
        }

        return "https://graph.microsoft.com/v1.0";
    }

}
