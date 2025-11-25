package fi.metatavu.keycloak.graphapi;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GraphApiUserIdentityProviderMapper extends AbstractIdentityProviderMapper {
    private static final Logger logger = Logger.getLogger(GraphApiUserIdentityProviderMapper.class);

    private static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String PROVIDER_ID = "graph-api-user-identity-provider-mapper";
    private static final String CONFIG_GRAPH_API_USER_ATTRIBUTE = "graph-api-user-attribute-name";
    private static final String CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME = "graph-api-user-attribute-keycloak-name";

    private static final String USER_ID = "User ID";
    private static final String USER_GIVEN_NAME = "User Given Name";
    private static final String USER_BUSINESS_PHONES = "User Business Phones";
    private static final String USER_DISPLAY_NAME = "User Display Name";
    private static final String USER_JOB_TITLE = "User Job Title";
    private static final String USER_MAIL = "User Mail";
    private static final String USER_MOBILE_PHONE = "User Mobile Phone";
    private static final String USER_OFFICE_LOCATION = "User Office Location";
    private static final String USER_PREFERRED_LANGUAGE = "User Preferred Language";
    private static final String USER_SURNAME = "User Surname";
    private static final String USER_USER_PRINCIPAL_NAME = "User User Principal Name";

    private static final String USER_AUTH_NOTE = "graph-api-user";
    private static final Map<String, Function<GraphUser, Object>> ATTRIBUTE_EXTRACTORS = Map.ofEntries(
            Map.entry(USER_ID, GraphUser::getId),
            Map.entry(USER_GIVEN_NAME, GraphUser::getGivenName),
            Map.entry(USER_BUSINESS_PHONES, GraphUser::getBusinessPhones),
            Map.entry(USER_DISPLAY_NAME, GraphUser::getDisplayName),
            Map.entry(USER_JOB_TITLE, GraphUser::getJobTitle),
            Map.entry(USER_MAIL, GraphUser::getMail),
            Map.entry(USER_MOBILE_PHONE, GraphUser::getMobilePhone),
            Map.entry(USER_OFFICE_LOCATION, GraphUser::getOfficeLocation),
            Map.entry(USER_PREFERRED_LANGUAGE, GraphUser::getPreferredLanguage),
            Map.entry(USER_SURNAME, GraphUser::getSurname),
            Map.entry(USER_USER_PRINCIPAL_NAME, GraphUser::getUserPrincipalName)
    );

    static {
        ProviderConfigProperty graphApiProperty = new ProviderConfigProperty();
        graphApiProperty.setName(CONFIG_GRAPH_API_USER_ATTRIBUTE);
        graphApiProperty.setLabel("User attribute");
        graphApiProperty.setHelpText("User attribute to map");
        graphApiProperty.setType(ProviderConfigProperty.LIST_TYPE);

        List<String> options = new ArrayList<>();
        options.add(USER_ID);
        options.add(USER_GIVEN_NAME);
        options.add(USER_BUSINESS_PHONES);
        options.add(USER_DISPLAY_NAME);
        options.add(USER_JOB_TITLE);
        options.add(USER_MAIL);
        options.add(USER_MOBILE_PHONE);
        options.add(USER_OFFICE_LOCATION);
        options.add(USER_PREFERRED_LANGUAGE);
        options.add(USER_SURNAME);
        options.add(USER_USER_PRINCIPAL_NAME);

        graphApiProperty.setOptions(options);
        configProperties.add(graphApiProperty);

        ProviderConfigProperty keycloakProperty = new ProviderConfigProperty();
        keycloakProperty.setName(CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME);
        keycloakProperty.setLabel("Keycloak attribute name");
        keycloakProperty.setHelpText("Keycloak attribute to map the user attribute to");
        keycloakProperty.setType(ProviderConfigProperty.USER_PROFILE_ATTRIBUTE_LIST_TYPE);
        configProperties.add(keycloakProperty);
    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateUserAttributes(context, mapperModel, user);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateUserAttributes(context, mapperModel, user);
    }

    private void updateUserAttributes(BrokeredIdentityContext context, IdentityProviderMapperModel mapperModel, UserModel user) {
        String graphApiAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_ATTRIBUTE);
        String keycloakAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME);

        GraphUser graphUser = getUser(context);
        if (graphUser == null) {
            logger.warn("Could not retrieve user from Graph API, skipping user update");
            return;
        }

        GraphApiMapperUtils.applyAttributeMapping(graphUser, graphApiAttribute, keycloakAttribute, user, ATTRIBUTE_EXTRACTORS, logger);
    }

    private GraphUser getUser(BrokeredIdentityContext context) {
        GraphApiClient graphApiClient = new GraphApiClient();
        return GraphApiMapperUtils.fetchGraphUser(context, logger, USER_AUTH_NOTE, graphApiClient::getUser);
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
        return "Graph API User Attributes";
    }

    @Override
    public String getHelpText() {
        return "Graph API User Identity Provider Mapper";
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
