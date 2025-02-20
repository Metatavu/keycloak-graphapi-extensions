package fi.metatavu.keycloak.graphapi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * User groups response model
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransitiveMemberOfGroupsResponse {

    private List<TransitiveMemberOfGroup> value;

    public List<TransitiveMemberOfGroup> getValue() {
        return value;
    }

    @SuppressWarnings("unused")
    public void setValue(List<TransitiveMemberOfGroup> value) {
        this.value = value;
    }

}
