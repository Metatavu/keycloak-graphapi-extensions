package fi.metatavu.keycloak.graphapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphProfilePositionDetail {

    private GraphProfileCompany company;

    public GraphProfileCompany getCompany() {
        return company;
    }

    public void setCompany(GraphProfileCompany company) {
        this.company = company;
    }
}
