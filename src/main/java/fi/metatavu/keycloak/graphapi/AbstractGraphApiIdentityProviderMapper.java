package fi.metatavu.keycloak.graphapi;

import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Base mapper class for Graph API identity provider mappers to share boilerplate.
 */
public abstract class AbstractGraphApiIdentityProviderMapper extends AbstractIdentityProviderMapper {

    private final String providerId;
    private final String displayType;
    private final String helpText;
    private final List<ProviderConfigProperty> configProperties;
    private final String[] compatibleProviders;

    protected AbstractGraphApiIdentityProviderMapper(String providerId, String displayType, String helpText, List<ProviderConfigProperty> configProperties) {
        this(providerId, displayType, helpText, configProperties, GraphApiMapperUtils.COMPATIBLE_PROVIDERS);
    }

    protected AbstractGraphApiIdentityProviderMapper(String providerId, String displayType, String helpText, List<ProviderConfigProperty> configProperties, String[] compatibleProviders) {
        this.providerId = providerId;
        this.displayType = displayType;
        this.helpText = helpText;
        this.configProperties = List.copyOf(configProperties);
        this.compatibleProviders = compatibleProviders.clone();
    }

    @Override
    public String[] getCompatibleProviders() {
        return compatibleProviders.clone();
    }

    @Override
    public String getDisplayCategory() {
        return "Graph API";
    }

    @Override
    public String getDisplayType() {
        return displayType;
    }

    @Override
    public String getHelpText() {
        return helpText;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return providerId;
    }
}
