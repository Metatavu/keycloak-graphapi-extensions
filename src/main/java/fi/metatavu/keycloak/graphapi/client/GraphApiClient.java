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

    private final String graphApiUrl;

    /**
     * Default constructor uses environment variable or default URL
     */
    public GraphApiClient() {
        this.graphApiUrl = getDefaultGraphApiUrl();
    }

    /**
     * Constructor for injecting custom Graph API URL (for testing)
     * @param graphApiUrl base URL for Graph API
     */
    public GraphApiClient(String graphApiUrl) {
        this.graphApiUrl = graphApiUrl;
    }

    /**
     * Returns logged user's membership of groups
     *
     * @param accessToken access token
     * @return logged user's membership of groups
     * @throws IOException thrown when request fails
     */
    public TransitiveMemberOfGroupsResponse getTransitiveMemberOfGroups(AccessTokenResponse accessToken) throws IOException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/me/transitiveMemberOf/microsoft.graph.group?$select=id,displayName,description,mail", this.graphApiUrl)))
                    .header("Authorization", "Bearer " + accessToken.getToken())
                    .build();

            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return deserialize(response.body(), TransitiveMemberOfGroupsResponse.class);
                } else if (statusCode == 404) {
                    return null;
                } else {
                    throw new IOException(String.format("Failed to execute: %s", statusCode));
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * Returns logged user's manager
     *
     * @param accessToken access token
     * @return logged user's manager
     * @throws IOException thrown when request fails
     */
    public GraphUser getManager(AccessTokenResponse accessToken) throws IOException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/me/manager", this.graphApiUrl)))
                .header("Authorization", "Bearer " + accessToken.getToken())
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                return deserialize(response.body(), GraphUser.class);
            } else if (statusCode == 404) {
                return null;
            } else {
                throw new IOException(String.format("Failed to execute: %s", statusCode));
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
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
    private String getDefaultGraphApiUrl() {
        String envUrl = System.getenv("GRAPH_API_URL");
        if (envUrl != null) {
            return envUrl;
        }
        return "https://graph.microsoft.com/v1.0";
    }

}
