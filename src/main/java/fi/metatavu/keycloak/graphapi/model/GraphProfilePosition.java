package fi.metatavu.keycloak.graphapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphProfilePosition {

    private GraphProfilePositionDetail detail;

    public GraphProfilePositionDetail getDetail() {
        return detail;
    }

    public void setDetail(GraphProfilePositionDetail detail) {
        this.detail = detail;
    }
}
