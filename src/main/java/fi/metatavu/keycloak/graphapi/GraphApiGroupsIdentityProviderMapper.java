package fi.metatavu.keycloak.graphapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import fi.metatavu.keycloak.graphapi.client.model.TransitiveMemberOfGroup;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessTokenResponse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphAPI groups identity provider mapper
 */
public class GraphApiGroupsIdentityProviderMapper extends AbstractGraphApiIdentityProviderMapper {

    private static final Logger logger = Logger.getLogger(GraphApiGroupsIdentityProviderMapper.class);
    private static final String PROVIDER_ID = "graph-api-groups-identity-provider-mapper";
    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.values()));
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

    public GraphApiGroupsIdentityProviderMapper() {
        super(PROVIDER_ID, "Graph API Groups", "Graph API Groups Identity Provider Mapper", configProperties);
    }

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateGroups(session, realm, user, mapperModel, context);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateGroups(session, realm, user, mapperModel, context);
    }

    /**
     * Updates user manager attributes
     *
     * @param user user model
     * @param context brokered identity context
     */
    private void updateGroups(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        AccessTokenResponse brokerToken = getBrokerToken(context);
        if (brokerToken == null) {
            logger.warn("Could not retrieve broker token from context, skipping group GraphAPI group mapping");
            return;
        }

        Map<String, List<String>> groupMappings = getGroupMappings(mapperModel);
        List<String> managedAzureGroupNames = groupMappings.keySet().stream().toList();
        List<String> managedKeycloakGroupNames = groupMappings.values().stream()
            .flatMap(List::stream)
            .toList();

        List<GroupModel> realmGroups = realm.getGroupsStream()
            .toList();

        Map<String, GroupModel> groupTree = realmGroups.stream()
            .collect(Collectors.toMap(GroupModel::getId, group -> group));

        Map<String, GroupModel> managedKeycloakGroups = realmGroups.stream()
            .map(group -> new AbstractMap.SimpleImmutableEntry<>(getGroupPath(groupTree, group.getId()), group))
            .filter(entry -> managedKeycloakGroupNames.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        ArrayList<GroupModel> leaveUserGroups = new ArrayList<>(user.getGroupsStream()
            .filter(group -> managedKeycloakGroupNames.contains(getGroupPath(groupTree, group.getId())))
            .toList());

        List<String> previousGroupNames = leaveUserGroups.stream()
            .map(group -> getGroupPath(groupTree, group.getId()))
            .toList();

        ArrayList<GroupModel> joinUserGroups = new ArrayList<>();

        List<TransitiveMemberOfGroup> azureGroups = getAzureGroups(brokerToken);
        if (azureGroups == null) {
            logger.warn("Could not retrieve user groups from GraphAPI, skipping group GraphAPI group mapping");
            return;
        }

        List<String> azureGroupNames = azureGroups.stream()
            .map(TransitiveMemberOfGroup::getDisplayName)
            .filter(Objects::nonNull)
            .map(GraphApiMapperUtils::encodeForStorage)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();

        logger.info("User's Azure groups: " + String.join(", ", azureGroupNames));

        for (String azureGroupName : azureGroupNames) {
            logger.info("Processing Azure group: " + azureGroupName);

            if (managedAzureGroupNames.contains(azureGroupName)) {
                List<String> keycloakGroups = groupMappings.get(azureGroupName);
                for (String keycloakGroup : keycloakGroups) {
                    if (previousGroupNames.contains(keycloakGroup)) {
                        logger.info("Not removing user from group " + keycloakGroup);
                        leaveUserGroups.removeIf(group -> getGroupPath(groupTree, group.getId()).equals(keycloakGroup));
                    } else {
                        if (managedKeycloakGroups.containsKey(keycloakGroup)) {
                            logger.info("Adding user to join group " + keycloakGroup);
                            joinUserGroups.add(managedKeycloakGroups.get(keycloakGroup));
                        } else {
                            logger.warn("Could not find managed Keycloak group " + keycloakGroup);
                        }
                    }
                }
            } else {
                logger.info("Skipping non-managed Azure group " + azureGroupName);
            }
        }

        for (GroupModel group : joinUserGroups) {
            logger.info("Joining user to group " + group.getName());

            String groupId = group.getId();
            String authNoteId = "USER_JOINING_GROUP_" + groupId;
            session.getContext().getAuthenticationSession().setAuthNote(authNoteId, user.getId());
            user.joinGroup(group);
        }

        for (GroupModel group : leaveUserGroups) {
            logger.info("Leaving user from group " + group.getName());

            String groupId = group.getId();
            String authNoteId = "USER_LEAVING_GROUP_" + groupId;
            session.getContext().getAuthenticationSession().setAuthNote(authNoteId, user.getId());
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
            return graphApiClient.getTransitiveMemberOfGroups(accessToken)
                .getValue()
                .stream()
                .filter(group -> group.getDisplayName() != null)
                .toList();
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
    private Map<String, List<String>> getGroupMappings(IdentityProviderMapperModel mapperModel) {
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mapperModel.getConfigMap(CONFIG_GRAPH_API_GROUP_MAPPING).entrySet()) {
            for (String value : entry.getValue()) {
                result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                result.get(entry.getKey()).add(value);
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
