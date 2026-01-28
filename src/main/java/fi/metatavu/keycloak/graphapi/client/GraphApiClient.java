package fi.metatavu.keycloak.graphapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.keycloak.representations.AccessTokenResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Microsoft Graph API client
 */
public class GraphApiClient {

    /**
     * Returns logged user's membership of groups
     *
     * @param accessToken access token
     * @return logged user's membership of groups
     * @throws IOException thrown when request fails
     */
    public TransitiveMemberOfGroupsResponse getTransitiveMemberOfGroups(AccessTokenResponse accessToken) throws IOException {
        return getGraphApiResource(accessToken, "me/transitiveMemberOf/microsoft.graph.group?$select=id,displayName,description,mail", TransitiveMemberOfGroupsResponse.class);
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
        return getGraphApiResource(accessToken, String.format("users/%s/transitiveMemberOf/microsoft.graph.group?$select=id,displayName,description,mail", userId), TransitiveMemberOfGroupsResponse.class);
    }

    /**
     * Returns logged user's manager
     *
     * @param accessToken access token
     * @return logged user's manager
     * @throws IOException thrown when request fails
     */
    public GraphUser getManager(AccessTokenResponse accessToken) throws IOException {
        return getGraphApiResource(accessToken, "me/manager", GraphUser.class);
    }

    /**
     * Returns logged user
     *
     * @param accessToken access token
     * @return logged user
     * @throws IOException thrown when request fails
     */
    public GraphUser getUser(AccessTokenResponse accessToken) throws IOException {
        return getGraphApiResource(accessToken, "me", GraphUser.class);
    }

    /**
     * Fetches a resource from the Microsoft Graph API.
     *
     * @param accessToken access token
     * @param path API path
     * @param clazz target class
     * @return resource
     * @throws IOException thrown when request fails
     */
    private <T> T getGraphApiResource(AccessTokenResponse accessToken, String path, Class<T> clazz) throws IOException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/%s", getGraphApiUrl(), path)))
                .header("Authorization", "Bearer " + accessToken.getToken())
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return handleResponse(response, clazz);
        } catch (InterruptedException e) {
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
    private <T> T handleResponse(HttpResponse<InputStream> response, Class<T> clazz) throws IOException {
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
     * @param json JSON input stream
     * @param clazz target class
     * @return deserialized object
     * @param <T> target class type
     * @throws IOException thrown when deserialization fails
     */
    @SuppressWarnings("SameParameterValue")
    private <T> T deserialize(InputStream json, Class<T> clazz) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, clazz);
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
