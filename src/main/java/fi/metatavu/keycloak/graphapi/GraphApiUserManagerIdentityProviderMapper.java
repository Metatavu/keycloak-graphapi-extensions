package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MapEntry;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphApiUserManagerIdentityProviderMapper extends AbstractIdentityProviderMapper {
    private static final Logger logger = Logger.getLogger(GraphApiUserManagerIdentityProviderMapper.class);

    private static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String PROVIDER_ID = "graph-api-user-manager-identity-provider-mapper";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE = "graph-api-user-manager-attribute-name";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME = "graph-api-user-manager-attribute-keycloak-name";

    private static final String MANAGER_ID = "Manager ID";
    private static final String MANAGER_GIVEN_NAME = "Manager Given Name";
    private static final String MANAGER_BUSINESS_PHONES = "Manager Business Phones";
    private static final String MANAGER_DISPLAY_NAME = "Manager Display Name";
    private static final String MANAGER_JOB_TITLE = "Manager Job Title";
    private static final String MANAGER_MAIL = "Manager Mail";
    private static final String MANAGER_MOBILE_PHONE = "Manager Mobile Phone";
    private static final String MANAGER_OFFICE_LOCATION = "Manager Office Location";
    private static final String MANAGER_PREFERRED_LANGUAGE = "Manager Preferred Language";
    private static final String MANAGER_SURNAME = "Manager Surname";
    private static final String MANAGER_USER_PRINCIPAL_NAME = "Manager User Principal Name";

    private static final String MANAGER_AUTH_NOTE = "graph-api-user-manager";

    static {
        ProviderConfigProperty graphApiProperty = new ProviderConfigProperty();
        graphApiProperty.setName(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE);
        graphApiProperty.setLabel("Manager attribute");
        graphApiProperty.setHelpText("Manager attribute to map");
        graphApiProperty.setType(ProviderConfigProperty.LIST_TYPE);

        List<String> options = new ArrayList<>();
        options.add(MANAGER_ID);
        options.add(MANAGER_GIVEN_NAME);
        options.add(MANAGER_BUSINESS_PHONES);
        options.add(MANAGER_DISPLAY_NAME);
        options.add(MANAGER_JOB_TITLE);
        options.add(MANAGER_MAIL);
        options.add(MANAGER_MOBILE_PHONE);
        options.add(MANAGER_OFFICE_LOCATION);
        options.add(MANAGER_PREFERRED_LANGUAGE);
        options.add(MANAGER_SURNAME);
        options.add(MANAGER_USER_PRINCIPAL_NAME);

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
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateManagerAttributes(context, mapperModel, user);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateManagerAttributes(context, mapperModel, user);
    }

    /**
     * Returns parsed broker token from context
     *
     * @param context brokered identity context
     * @return parsed broker token or null if token is not present
     */
    private AccessTokenResponse getBrokerToken(BrokeredIdentityContext context) {
        String token = context.getToken();
        if (token == null) {
            return null;
        }

        try {
            return new ObjectMapper().readValue(token, AccessTokenResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse token", e);
            return null;
        }
    }

    private void updateUserAttribute(UserModel user, String attributeName, Object value) {
        if (value == null) {
            user.removeAttribute(attributeName);
        } else {
            if (value instanceof List) {
                user.setAttribute(attributeName, (List<String>) value);
            } else {
                user.setSingleAttribute(attributeName, value.toString());
            }
        }
    }

    private void updateManagerAttributes(BrokeredIdentityContext context, IdentityProviderMapperModel mapperModel, UserModel user) {
        String graphApiAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE);
        String keycloakAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME);

        GraphUser manager = getManager(context);
        if (manager == null) {
            logger.warn("Could not retrieve manager from Graph API, skipping manager update");
            return;
        }

        for (Map.Entry<String, List<String>> attribute : user.getAttributes().entrySet()) {
            logger.info(attribute.getKey() + " 1: " + attribute.getValue());
        }

        switch (graphApiAttribute) {
            case MANAGER_ID:
                updateUserAttribute(user, keycloakAttribute, manager.getId());
                break;
            case MANAGER_GIVEN_NAME:
                updateUserAttribute(user, keycloakAttribute, manager.getGivenName());
                break;
            case MANAGER_BUSINESS_PHONES:
                updateUserAttribute(user, keycloakAttribute, manager.getBusinessPhones());
                break;
            case MANAGER_DISPLAY_NAME:
                updateUserAttribute(user, keycloakAttribute, manager.getDisplayName());
                break;
            case MANAGER_JOB_TITLE:
                updateUserAttribute(user, keycloakAttribute, manager.getJobTitle());
                break;
            case MANAGER_MAIL:
                updateUserAttribute(user, keycloakAttribute, manager.getMail());
                break;
            case MANAGER_MOBILE_PHONE:
                updateUserAttribute(user, keycloakAttribute, manager.getMobilePhone());
                break;
            case MANAGER_OFFICE_LOCATION:
                updateUserAttribute(user, keycloakAttribute, manager.getOfficeLocation());
                break;
            case MANAGER_PREFERRED_LANGUAGE:
                updateUserAttribute(user, keycloakAttribute, manager.getPreferredLanguage());
                break;
            case MANAGER_SURNAME:
                updateUserAttribute(user, keycloakAttribute, manager.getSurname());
                break;
            case MANAGER_USER_PRINCIPAL_NAME:
                updateUserAttribute(user, keycloakAttribute, manager.getUserPrincipalName());
                break;
        }

        for (Map.Entry<String, List<String>> attribute : user.getAttributes().entrySet()) {
            logger.info(attribute.getKey() + " 2: " + attribute.getValue());
        }
    }

    /**
     * Returns manager of the user
     *
     * @param context brokered identity context
     * @return manager of the user
     */
    private GraphUser getManager(BrokeredIdentityContext context) {
        String cachedManager = context.getAuthenticationSession().getAuthNote(MANAGER_AUTH_NOTE);
        if (cachedManager != null) {
            try {
                return new ObjectMapper().readValue(cachedManager, GraphUser.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse cached manager", e);
            }
        }

        AccessTokenResponse brokerToken = getBrokerToken(context);
        if (brokerToken == null) {
            logger.warn("Broker token is null, cannot retrieve manager");
            return null;
        }

        GraphApiClient graphApiClient = new GraphApiClient();
        try {
            GraphUser manager = graphApiClient.getManager(brokerToken);
            if (manager != null) {
                context.getAuthenticationSession().setAuthNote(MANAGER_AUTH_NOTE, new ObjectMapper().writeValueAsString(manager));
            }

            return manager;
        } catch (Exception e) {
            logger.error("Failed to get manager", e);
            return null;
        }
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
