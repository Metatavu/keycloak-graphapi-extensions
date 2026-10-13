package fi.metatavu.keycloak.graphapi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * User groups response model
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransitiveMemberOfGroupsResponse {

    private List<TransitiveMemberOfGroup> value;

    @JsonProperty("@odata.count")
    private Integer odataCount;

    @JsonProperty("@odata.nextLink")
    private String odataNextLink;

    @JsonIgnore
    private Integer receivedCount;

    @JsonIgnore
    private String requestId;

    @JsonIgnore
    private String rawBody;

    public List<TransitiveMemberOfGroup> getValue() {
        return value;
    }

    @SuppressWarnings("unused")
    public void setValue(List<TransitiveMemberOfGroup> value) {
        this.value = value;
    }

    /**
     * Returns total number of groups matching the query as reported by Graph API
     *
     * @return total number of groups or null if Graph API did not report it
     */
    public Integer getOdataCount() {
        return odataCount;
    }

    public void setOdataCount(Integer odataCount) {
        this.odataCount = odataCount;
    }

    /**
     * Returns link to the next page of results
     *
     * @return link to the next page or null if the response contains all results
     */
    public String getOdataNextLink() {
        return odataNextLink;
    }

    public void setOdataNextLink(String odataNextLink) {
        this.odataNextLink = odataNextLink;
    }

    /**
     * Returns number of groups received from Graph API before local filtering
     *
     * @return number of groups received from Graph API or null if not known
     */
    @JsonIgnore
    public Integer getReceivedCount() {
        return receivedCount;
    }

    @JsonIgnore
    public void setReceivedCount(Integer receivedCount) {
        this.receivedCount = receivedCount;
    }

    /**
     * Returns Graph API request id of the response. Microsoft support can trace requests with it
     *
     * @return Graph API request id or null if not known
     */
    @JsonIgnore
    public String getRequestId() {
        return requestId;
    }

    @JsonIgnore
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Returns response body as received from Graph API
     *
     * @return response body or null if not known
     */
    @JsonIgnore
    public String getRawBody() {
        return rawBody;
    }

    @JsonIgnore
    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

}
