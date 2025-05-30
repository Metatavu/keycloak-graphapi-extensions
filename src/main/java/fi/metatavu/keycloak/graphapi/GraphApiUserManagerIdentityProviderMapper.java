package fi.metatavu.keycloak.graphapi;

import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class GraphApiUserManagerIdentityProviderMapper extends AbstractIdentityProviderMapper {
    private static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String PROVIDER_ID = "graph-api-user-manager-identity-provider-mapper";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE = "graph-api-user-manager-attribute-name";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME = "graph-api-user-manager-attribute-keycloak-name";

    static {
        ProviderConfigProperty graphApiProperty = new ProviderConfigProperty();
        graphApiProperty.setName(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE);
        graphApiProperty.setLabel("Manager attribute");
        graphApiProperty.setHelpText("Manager attribute to map");
        graphApiProperty.setType(ProviderConfigProperty.LIST_TYPE);

        List<String> options = new ArrayList<>();
        options.add("Manager ID");
        options.add("Manager Given Name");
        options.add("Manager Business Phones");
        options.add("Manager Display Name");
        options.add("Manager Job Title");
        options.add("Manager Mail");
        options.add("Manager Mobile Phone");
        options.add("Manager Office Location");
        options.add("Manager Preferred Language");
        options.add("Manager Surname");
        options.add("Manager User Principal Name");
        graphApiProperty.setOptions(options);
        configProperties.add(graphApiProperty);

        ProviderConfigProperty keycloakProperty = new ProviderConfigProperty();
        keycloakProperty.setName(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME);
        keycloakProperty.setLabel("Keycloak attribute name");
        keycloakProperty.setHelpText("Keycloak attribute to map the manager attribute to");
        keycloakProperty.setType(ProviderConfigProperty.USER_PROFILE_ATTRIBUTE_LIST_TYPE);
        configProperties.add(keycloakProperty);
    }

    @Override
    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS;
    }

    @Override
    public String getDisplayCategory() {
        return "Graph API";
    }

    @Override
    public String getDisplayType() {
        return "Graph API User Manager Attributes";
    }

    @Override
    public String getHelpText() {
        return "Graph API User Manager Identity Provider Mapper";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

}
