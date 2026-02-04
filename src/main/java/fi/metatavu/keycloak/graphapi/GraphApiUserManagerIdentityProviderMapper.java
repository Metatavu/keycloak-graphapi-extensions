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

public class GraphApiUserManagerIdentityProviderMapper extends AbstractGraphApiIdentityProviderMapper {
    private static final Logger logger = Logger.getLogger(GraphApiUserManagerIdentityProviderMapper.class);

    private static final String PROVIDER_ID = "graph-api-user-manager-identity-provider-mapper";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE = "graph-api-user-manager-attribute-name";
    private static final String CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME = "graph-api-user-manager-attribute-keycloak-name";

    private static final String MANAGER_ID = "Manager ID";
    private static final String MANAGER_GIVEN_NAME = "Manager Given Name";
    private static final String MANAGER_BUSINESS_PHONES = "Manager Business Phones";
    private static final String MANAGER_DISPLAY_NAME = "Manager Display Name";
    private static final String MANAGER_JOB_TITLE = "Manager Job Title";
    private static final String MANAGER_GROUP_NAMES = "Manager Group Names";
    private static final String MANAGER_MAIL = "Manager Mail";
    private static final String MANAGER_MOBILE_PHONE = "Manager Mobile Phone";
    private static final String MANAGER_OFFICE_LOCATION = "Manager Office Location";
    private static final String MANAGER_PREFERRED_LANGUAGE = "Manager Preferred Language";
    private static final String MANAGER_SURNAME = "Manager Surname";
    private static final String MANAGER_USER_PRINCIPAL_NAME = "Manager User Principal Name";

    private static final String MANAGER_AUTH_NOTE = "graph-api-user-manager";
    private static final List<String> ATTRIBUTE_OPTIONS = List.of(
        MANAGER_ID,
        MANAGER_GIVEN_NAME,
        MANAGER_BUSINESS_PHONES,
        MANAGER_DISPLAY_NAME,
        MANAGER_JOB_TITLE,
        MANAGER_GROUP_NAMES,
        MANAGER_MAIL,
        MANAGER_MOBILE_PHONE,
        MANAGER_OFFICE_LOCATION,
        MANAGER_PREFERRED_LANGUAGE,
        MANAGER_SURNAME,
        MANAGER_USER_PRINCIPAL_NAME
    );
    private static final Map<String, Function<GraphUser, Object>> ATTRIBUTE_EXTRACTORS = Map.ofEntries(
        Map.entry(MANAGER_ID, GraphUser::getId),
        Map.entry(MANAGER_GIVEN_NAME, GraphUser::getGivenName),
        Map.entry(MANAGER_BUSINESS_PHONES, GraphUser::getBusinessPhones),
        Map.entry(MANAGER_DISPLAY_NAME, GraphUser::getDisplayName),
        Map.entry(MANAGER_JOB_TITLE, GraphUser::getJobTitle),
        Map.entry(MANAGER_MAIL, GraphUser::getMail),
        Map.entry(MANAGER_MOBILE_PHONE, GraphUser::getMobilePhone),
        Map.entry(MANAGER_OFFICE_LOCATION, GraphUser::getOfficeLocation),
        Map.entry(MANAGER_PREFERRED_LANGUAGE, GraphUser::getPreferredLanguage),
        Map.entry(MANAGER_SURNAME, GraphUser::getSurname),
        Map.entry(MANAGER_USER_PRINCIPAL_NAME, GraphUser::getUserPrincipalName)
    );
    private static final List<ProviderConfigProperty> configProperties = GraphApiMapperUtils.buildConfigProperties(
        CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE,
        "Manager attribute",
        "Manager attribute to map",
        ATTRIBUTE_OPTIONS,
        CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME,
        "Keycloak attribute name",
        "Keycloak attribute to map the manager attribute to"
    );

    public GraphApiUserManagerIdentityProviderMapper() {
        super(PROVIDER_ID, "Graph API User Manager Attributes", "Graph API User Manager Identity Provider Mapper", configProperties);
    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateManagerAttributes(context, mapperModel, user);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateManagerAttributes(context, mapperModel, user);
    }

    private void updateManagerAttributes(BrokeredIdentityContext context, IdentityProviderMapperModel mapperModel, UserModel user) {
        String graphApiAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE);
        String keycloakAttribute = mapperModel.getConfig().get(CONFIG_GRAPH_API_USER_MANAGER_ATTRIBUTE_KEYCLOAK_NAME);

        GraphUser manager = getManager(context);
        if (manager == null) {
            logger.warn("Could not retrieve manager from Graph API, skipping manager update");
            return;
        }

        if (MANAGER_GROUP_NAMES.equals(graphApiAttribute)) {
            List<String> groupNames = getManagerGroupNames(context, manager);
            logger.infof("Resolved manager group names: %s", groupNames);
            GraphApiMapperUtils.updateUserAttribute(user, keycloakAttribute, groupNames);
            return;
        }

        GraphApiMapperUtils.applyAttributeMapping(manager, graphApiAttribute, keycloakAttribute, user, ATTRIBUTE_EXTRACTORS, logger);
    }

    /**
     * Returns manager of the user
     *
     * @param context brokered identity context
     * @return manager of the user
     */
    private GraphUser getManager(BrokeredIdentityContext context) {
        GraphApiClient graphApiClient = new GraphApiClient();
        return GraphApiMapperUtils.fetchGraphUser(context, logger, MANAGER_AUTH_NOTE, graphApiClient::getManager);
    }

    private List<String> getManagerGroupNames(BrokeredIdentityContext context, GraphUser manager) {
        AccessTokenResponse brokerToken = GraphApiMapperUtils.parseBrokerToken(context, logger);
        if (brokerToken == null) {
            logger.warn("Broker token is null, cannot retrieve manager groups");
            return List.of();
        }

        if (manager.getId() == null) {
            logger.warn("Manager id is null, cannot retrieve manager groups");
            return List.of();
        }

        GraphApiClient graphApiClient = new GraphApiClient();
        try {
            TransitiveMemberOfGroupsResponse response = graphApiClient.getTransitiveMemberOfGroupsForUser(brokerToken, manager.getId());
            if (response == null || response.getValue() == null) {
                logger.info("Graph API manager groups response is empty");
                return List.of();
            }

            List<String> groupNames = response.getValue().stream()
                .map(TransitiveMemberOfGroup::getDisplayName)
                .filter(Objects::nonNull)
                .map(GraphApiMapperUtils::encodeForStorage)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
            logger.infof("Graph API returned %d manager groups", groupNames.size());
            return groupNames;
        } catch (Exception e) {
            logger.error("Failed to get manager groups", e);
            return List.of();
        }
    }
}
