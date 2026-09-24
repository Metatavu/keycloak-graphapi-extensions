package fi.metatavu.keycloak.graphapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Employee organization data of a Microsoft Graph API user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphEmployeeOrgData {

    private String costCenter;

    private String division;

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }
}
