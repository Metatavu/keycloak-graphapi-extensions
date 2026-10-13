package fi.metatavu.keycloak.graphapi;

import fi.metatavu.keycloak.graphapi.client.GraphApiClient;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GraphAPI groups identity provider mapper
 */
public class GraphApiGroupsIdentityProviderMapper extends AbstractGraphApiIdentityProviderMapper {

    private static final Logger logger = Logger.getLogger(GraphApiGroupsIdentityProviderMapper.class);
    private static final String PROVIDER_ID = "graph-api-groups-identity-provider-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String CONFIG_GRAPH_API_GROUP_MAPPING = "graph-api-group-mapping";
    private static final String CONFIG_KEEP_GROUPS_WHEN_ALL_WOULD_BE_REMOVED = "keep-groups-when-all-would-be-removed";

    static {
        ProviderConfigProperty claimsProperty = new ProviderConfigProperty();
        claimsProperty.setName(CONFIG_GRAPH_API_GROUP_MAPPING);
        claimsProperty.setLabel("Groups");
        claimsProperty.setHelpText("Map Azure groups to Keycloak groups");
        claimsProperty.setType(ProviderConfigProperty.MAP_TYPE);
        configProperties.add(claimsProperty);

        ProviderConfigProperty keepGroupsProperty = new ProviderConfigProperty();
        keepGroupsProperty.setName(CONFIG_KEEP_GROUPS_WHEN_ALL_WOULD_BE_REMOVED);
        keepGroupsProperty.setLabel("Keep groups when all would be removed");
        keepGroupsProperty.setHelpText("When a login would remove the user from all managed groups without joining any, keep the user in the groups. Graph API may return an incomplete group list, so this prevents losing all permissions because of it. Removals from some of the groups are always applied. Enabled when not set.");
        keepGroupsProperty.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        keepGroupsProperty.setDefaultValue(Boolean.TRUE.toString());
        configProperties.add(keepGroupsProperty);
    }

    public GraphApiGroupsIdentityProviderMapper() {
        super(PROVIDER_ID, "Graph API Groups", "Graph API Groups Identity Provider Mapper", configProperties);
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
        List<String> azureGroupNames = GraphApiMapperUtils.fetchUserGroupDisplayNames(context, createGraphApiClient(), logger);
        if (azureGroupNames == null) {
            logger.debug("Could not retrieve user groups from GraphAPI, skipping group GraphAPI group mapping");
            return;
        }

        Map<String, List<String>> groupMappings = getGroupMappings(mapperModel);
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
        List<String> matchedAzureGroupNames = new ArrayList<>();

        if (logger.isDebugEnabled()) {
            logger.debugf("User's Azure groups: %s", String.join(", ", azureGroupNames));
        }

        for (String azureGroupName : azureGroupNames) {
            logger.debugf("Processing Azure group: %s", azureGroupName);

            List<String> keycloakGroups = getMappedKeycloakGroups(groupMappings, azureGroupName);
            if (!keycloakGroups.isEmpty()) {
                matchedAzureGroupNames.add(azureGroupName);
                for (String keycloakGroup : keycloakGroups) {
                    if (previousGroupNames.contains(keycloakGroup)) {
                        logger.debugf("Not removing user from group %s", keycloakGroup);
                        leaveUserGroups.removeIf(group -> getGroupPath(groupTree, group.getId()).equals(keycloakGroup));
                    } else {
                        if (managedKeycloakGroups.containsKey(keycloakGroup)) {
                            GroupModel joinGroup = managedKeycloakGroups.get(keycloakGroup);
                            if (!joinUserGroups.contains(joinGroup)) {
                                logger.debugf("Adding user to join group %s", keycloakGroup);
                                joinUserGroups.add(joinGroup);
                            }
                        } else {
                            logger.warn("Could not find managed Keycloak group " + keycloakGroup);
                        }
                    }
                }
            } else {
                logger.debugf("Skipping non-managed Azure group %s", azureGroupName);
            }
        }

        MappingResult mappingResult = new MappingResult(
            matchedAzureGroupNames,
            previousGroupNames,
            getGroupPaths(groupTree, joinUserGroups),
            getGroupPaths(groupTree, leaveUserGroups)
        );

        boolean keepGroups = mappingResult.removesAllManagedGroups() && isKeepGroupsWhenAllWouldBeRemoved(mapperModel);
        logMappingResult(user, mapperModel, context, azureGroupNames.size(), mappingResult, keepGroups);

        if (keepGroups) {
            leaveUserGroups.clear();
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
     * Logs the result of the group mapping for the login.
     *
     * Warns when the user would be removed from all managed groups without joining any, because that happens also
     * when Graph API returns an incomplete group list.
     *
     * @param user user
     * @param mapperModel mapper model
     * @param context brokered identity context
     * @param azureGroupCount number of user's Azure groups
     * @param result mapping result
     * @param keepGroups whether the user is kept in the groups instead of removing them
     */
    private void logMappingResult(UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context, int azureGroupCount, MappingResult result, boolean keepGroups) {
        String summary = String.format(
            "user=%s, idp=%s, brokerUserId=%s, azureGroups=%d, matchedAzureGroups=%s, managedGroupsBefore=%s, join=%s, leave=%s",
            user.getUsername(),
            mapperModel.getIdentityProviderAlias(),
            context.getId(),
            azureGroupCount,
            result.matchedAzureGroups(),
            result.managedGroupsBefore(),
            result.join(),
            result.leave()
        );

        if (keepGroups) {
            logger.warnf("Graph API group mapping would remove user from all managed groups, keeping the groups [%s]", summary);
        } else if (result.removesAllManagedGroups()) {
            logger.warnf("Graph API group mapping removes user from all managed groups [%s]", summary);
        } else {
            logger.infof("Graph API group mapping [%s]", summary);
        }
    }

    /**
     * Returns whether the user is kept in the managed groups when a login would remove the user from all of them.
     * Enabled when not configured, so that existing mappers get the protection.
     *
     * @param mapperModel mapper model
     * @return whether the user is kept in the managed groups
     */
    private boolean isKeepGroupsWhenAllWouldBeRemoved(IdentityProviderMapperModel mapperModel) {
        Map<String, String> config = mapperModel.getConfig();
        if (config == null) {
            return true;
        }

        return !Boolean.FALSE.toString().equalsIgnoreCase(config.get(CONFIG_KEEP_GROUPS_WHEN_ALL_WOULD_BE_REMOVED));
    }

    /**
     * Returns paths of groups
     *
     * @param groupTree group tree
     * @param groups groups
     * @return group paths
     */
    private List<String> getGroupPaths(Map<String, GroupModel> groupTree, List<GroupModel> groups) {
        return groups.stream()
            .map(group -> getGroupPath(groupTree, group.getId()))
            .toList();
    }

    /**
     * Creates a Graph API client.
     *
     * @return Graph API client
     */
    protected GraphApiClient createGraphApiClient() {
        return new GraphApiClient();
    }

    /**
     * Returns Keycloak groups mapped to an Azure group.
     *
     * Mapping keys are matched against both the trimmed display name and the URL-encoded display name. Versions up
     * to 1.1.5 matched trimmed display names and later versions matched URL-encoded display names, so mappings
     * configured for either version keep working.
     *
     * @param groupMappings group mappings
     * @param azureGroupName Azure group display name
     * @return mapped Keycloak group paths or empty list if the Azure group is not managed
     */
    private List<String> getMappedKeycloakGroups(Map<String, List<String>> groupMappings, String azureGroupName) {
        return Stream.of(azureGroupName.trim(), GraphApiMapperUtils.encodeForStorage(azureGroupName).trim())
            .distinct()
            .filter(groupMappings::containsKey)
            .flatMap(mappingKey -> groupMappings.get(mappingKey).stream())
            .distinct()
            .toList();
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

    /**
     * Result of the group mapping for a login
     *
     * @param matchedAzureGroups names of the Azure groups that matched the mapping
     * @param managedGroupsBefore paths of the managed groups the user belonged to before the login
     * @param join paths of the groups the user joins
     * @param leave paths of the groups the user leaves
     */
    private record MappingResult(List<String> matchedAzureGroups, List<String> managedGroupsBefore, List<String> join, List<String> leave) {

        /**
         * Returns whether the user is removed from all managed groups the user belonged to without joining any
         *
         * @return whether the user is removed from all managed groups
         */
        boolean removesAllManagedGroups() {
            return !managedGroupsBefore.isEmpty() && leave.size() == managedGroupsBefore.size() && join.isEmpty();
        }

    }

}
