package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphAPI groups identity provider mapper
 */
public class GraphApiGroupsIdentityProviderMapper extends AbstractIdentityProviderMapper {

    private static final Logger logger = Logger.getLogger(GraphApiGroupsIdentityProviderMapper.class);
    private static final String PROVIDER_ID = "graph-api-groups-identity-provider-mapper";
    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.values()));
    private static final String[] COMPATIBLE_PROVIDERS = new String[] {"oidc"};
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String CONFIG_GRAPH_API_GROUP_MAPPING = "graph-api-group-mapping";

    static {
        ProviderConfigProperty claimsProperty = new ProviderConfigProperty();
        claimsProperty.setName(CONFIG_GRAPH_API_GROUP_MAPPING);
        claimsProperty.setLabel("Groups");
        claimsProperty.setHelpText("Map Azure groups to Keycloak groups");
        claimsProperty.setType(ProviderConfigProperty.MAP_TYPE);
        configProperties.add(claimsProperty);
    }

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
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
        return "Graph API Groups";
    }

    @Override
    public String getHelpText() {
        return "Graph API Groups Identity Provider Mapper";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateGroups(realm, user, mapperModel, context);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateGroups(realm, user, mapperModel, context);
    }

    /**
     * Updates user manager attributes
     *
     * @param user user model
     * @param context brokered identity context
     */
    private void updateGroups(RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        AccessTokenResponse brokerToken = getBrokerToken(context);
        if (brokerToken == null) {
            logger.warn("Could not retrieve broker token from context, skipping group GraphAPI group mapping");
            return;
        }

        Map<String, String> groupMappings = getGroupMappings(mapperModel);
        List<String> managedAzureGroupNames = groupMappings.keySet().stream().toList();
        List<String> managedKeycloakGroupNames = groupMappings.values().stream().toList();

        List<GroupModel> realmGroups = realm.getGroupsStream()
            .toList();

        Map<String, GroupModel> groupTree = realmGroups.stream()
            .collect(Collectors.toMap(GroupModel::getId, group -> group));

        Map<String, GroupModel> managedKeycloakGroups = realmGroups.stream()
                .map(group -> new AbstractMap.SimpleImmutableEntry<>(getGroupPath(groupTree, group.getId()), group))
                .filter(entry -> managedKeycloakGroupNames.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        ArrayList<GroupModel> leaveUserGroups = new ArrayList<>(user.getGroupsStream()
            .toList());

        List<String> previousGroupNames = leaveUserGroups.stream()
            .map(GroupModel::getName)
            .filter(managedKeycloakGroupNames::contains)
            .toList();

        ArrayList<GroupModel> joinUserGroups = new ArrayList<>();

        List<TransitiveMemberOfGroup> azureGroups = getAzureGroups(brokerToken);
        if (azureGroups == null) {
            logger.warn("Could not retrieve user groups from GraphAPI, skipping group GraphAPI group mapping");
            return;
        }

        for (TransitiveMemberOfGroup azureGroup : azureGroups) {
            String azureGroupName = azureGroup.getDisplayName().trim();

            if (managedAzureGroupNames.contains(azureGroupName)) {
                String keycloakGroup = groupMappings.get(azureGroupName);

                if (previousGroupNames.contains(keycloakGroup)) {
                    logger.debug("Removing user from leave group " + keycloakGroup);
                    leaveUserGroups.removeIf(group -> group.getName().equals(keycloakGroup));
                } else {
                    if (managedKeycloakGroups.containsKey(keycloakGroup)) {
                        logger.debug("Adding user to join group " + keycloakGroup);
                        joinUserGroups.add(managedKeycloakGroups.get(keycloakGroup));
                    } else {
                        logger.warn("Could not find managed Keycloak group " + keycloakGroup);
                    }
                }
            } else {
                logger.debug("Skipping non-managed Azure group " + azureGroupName);
            }
        }

        for (GroupModel group : joinUserGroups) {
            logger.debug("Joining user to group " + group.getName());
            user.joinGroup(group);
        }

        for (GroupModel group : leaveUserGroups) {
            logger.debug("Leaving user from group " + group.getName());
            user.leaveGroup(group);
        }
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

    /**
     * Returns user groups from GraphAPI
     *
     * @param accessToken access token
     * @return user groups
     */
    private List<TransitiveMemberOfGroup> getAzureGroups(AccessTokenResponse accessToken) {
        GraphApiClient graphApiClient = new GraphApiClient();
        try {
            return graphApiClient.getTransitiveMemberOfGroups(accessToken).getValue();
        } catch (Exception e) {
            logger.error("Failed to get manager", e);
            return null;
        }
    }

    /**
     * Returns group mappings from mapper configuration
     *
     * @param mapperModel mapper model configuration
     * @return group mappings
     */
    private Map<String, String> getGroupMappings(IdentityProviderMapperModel mapperModel) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mapperModel.getConfigMap(CONFIG_GRAPH_API_GROUP_MAPPING).entrySet()) {
            for (String value : entry.getValue()) {
                result.put(entry.getKey(), value);
            }
        }

        return result;
    }

    /**
     * Returns group path
     *
     * @param groupTree group tree
     * @param id group id
     * @return group path
     */
    private String getGroupPath(Map<String, GroupModel> groupTree, String id) {
        GroupModel group = groupTree.get(id);
        if (group.getParentId() != null) {
            return getGroupPath(groupTree, group.getParentId()) + "/" + group.getName();
        }

        return group.getName();
    }

}
