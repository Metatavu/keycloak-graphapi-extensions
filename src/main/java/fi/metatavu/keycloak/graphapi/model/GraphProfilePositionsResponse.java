package fi.metatavu.keycloak.graphapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphProfilePositionsResponse {

    private List<GraphProfilePosition> value;

    public List<GraphProfilePosition> getValue() {
        return value;
    }

    public void setValue(List<GraphProfilePosition> value) {
        this.value = value;
    }
}
