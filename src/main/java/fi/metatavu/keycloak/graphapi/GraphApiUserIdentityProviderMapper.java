package fi.metatavu.keycloak.graphapi;

import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroupsResponse;
import fi.metatavu.keycloak.graphapi.model.GraphUser;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * GraphAPI user identity provider mapper
 */
public class GraphApiUserIdentityProviderMapper extends AbstractGraphApiIdentityProviderMapper {
    private static final Logger logger = Logger.getLogger(GraphApiUserIdentityProviderMapper.class);

    private static final String PROVIDER_ID = "graph-api-user-identity-provider-mapper";
    private static final String CONFIG_GRAPH_API_USER_ATTRIBUTE = "graph-api-user-attribute-name";
    private static final String CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME = "graph-api-user-attribute-keycloak-name";

    private static final String USER_ID = "User ID";
    private static final String USER_GIVEN_NAME = "User Given Name";
    private static final String USER_BUSINESS_PHONES = "User Business Phones";
    private static final String USER_DISPLAY_NAME = "User Display Name";
    private static final String USER_JOB_TITLE = "User Job Title";
    private static final String USER_GROUP_NAMES = "User Group Names";
    private static final String USER_MAIL = "User Mail";
    private static final String USER_MOBILE_PHONE = "User Mobile Phone";
    private static final String USER_OFFICE_LOCATION = "User Office Location";
    private static final String USER_PREFERRED_LANGUAGE = "User Preferred Language";
    private static final String USER_SURNAME = "User Surname";
    private static final String USER_USER_PRINCIPAL_NAME = "User User Principal Name";

    private static final String USER_AUTH_NOTE = "graph-api-user";
    private static final List<String> ATTRIBUTE_OPTIONS = List.of(
        USER_ID,
        USER_GIVEN_NAME,
        USER_BUSINESS_PHONES,
        USER_DISPLAY_NAME,
        USER_JOB_TITLE,
        USER_GROUP_NAMES,
        USER_MAIL,
        USER_MOBILE_PHONE,
        USER_OFFICE_LOCATION,
        USER_PREFERRED_LANGUAGE,
        USER_SURNAME,
        USER_USER_PRINCIPAL_NAME
    );
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
    private static final List<ProviderConfigProperty> configProperties = GraphApiMapperUtils.buildConfigProperties(
        CONFIG_GRAPH_API_USER_ATTRIBUTE,
        "User attribute",
        "User attribute to map",
        ATTRIBUTE_OPTIONS,
        CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME,
        "Keycloak attribute name",
        "Keycloak attribute to map the user attribute to"
    );

    /**
     * Constructor for GraphApiUserIdentityProviderMapper.
     */
    public GraphApiUserIdentityProviderMapper() {
        super(PROVIDER_ID, "Graph API User Attributes", "Graph API User Identity Provider Mapper", configProperties);
    }

    /**
     * Imports a new user into Keycloak.
     */
    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateUserAttributes(context, mapperModel, user);
    }

    /**
     * Updates an existing user in Keycloak.
     */
    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateUserAttributes(context, mapperModel, user);
    }

    /**
     * Updates user attributes in Keycloak.
     */
    private void updateUserAttributes(BrokeredIdentityContext context, IdentityProviderMapperModel mapperModel, UserModel user) {
        String graphApiAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_ATTRIBUTE);
        String keycloakAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_ATTRIBUTE_KEYCLOAK_NAME);

        if (USER_GROUP_NAMES.equals(graphApiAttribute)) {
            List<String> groupIds = getUserGroupIds(context);
            logger.infof("Resolved user group ids: %s", groupIds);
            GraphApiMapperUtils.updateUserAttribute(user, keycloakAttribute, groupIds);
            return;
        }

        GraphUser graphUser = getUser(context);
        if (graphUser == null) {
            logger.warn("Could not retrieve user from Graph API, skipping user update");
            return;
        }

        GraphApiMapperUtils.applyAttributeMapping(graphUser, graphApiAttribute, keycloakAttribute, user, ATTRIBUTE_EXTRACTORS, logger);
    }

    /**
     * Returns user of the context
     *
     * @param context brokered identity context
     * @return user of the context
     */
    private GraphUser getUser(BrokeredIdentityContext context) {
        GraphApiClient graphApiClient = new GraphApiClient();
        return GraphApiMapperUtils.fetchGraphUser(context, logger, USER_AUTH_NOTE, graphApiClient::getUser);
    }

    private List<String> getUserGroupIds(BrokeredIdentityContext context) {
        AccessTokenResponse brokerToken = GraphApiMapperUtils.parseBrokerToken(context, logger);
        if (brokerToken == null) {
            logger.warn("Broker token is null, cannot retrieve user groups");
            return List.of();
        }

        GraphApiClient graphApiClient = new GraphApiClient();
        try {
            TransitiveMemberOfGroupsResponse response = graphApiClient.getTransitiveMemberOfGroups(brokerToken);
            if (response == null || response.getValue() == null) {
                logger.info("Graph API user groups response is empty");
                return List.of();
            }

            List<String> groupIds = response.getValue().stream()
                .map(TransitiveMemberOfGroup::getId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toList();
            logger.infof("Graph API returned %d user groups", groupIds.size());
            return groupIds;
        } catch (Exception e) {
            logger.error("Failed to get user groups", e);
            return List.of();
        }
    }
}
