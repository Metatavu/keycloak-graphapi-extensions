# Keycloak GraphAPI extensions

This repository provides custom Keycloak extensions that enable integration with the Microsoft Graph API.

## Keycloak compatibility

| Extension version | Built against Keycloak | Tested with Keycloak |
|---|---|---|
| 1.2.1 | 26.3.5 | 26.1.5, 26.3.5 |
| 1.2.0 | 26.3.5 | 26.1.5, 26.3.5 |
| 1.0.0 – 1.1.17 | 26.1.2 | 26.1.2 |

Tested means that the integration tests pass against the Keycloak version. To run them against another version, build the jar and run the tests with the version as a property, for example:

```
./gradlew jar test -PkeycloakVersion=26.1.5
```

The tests deploy every jar in `build/libs`, so remove jars of other extension versions from there first.

## Configuration

The extension is configured with the following optional environment variables in Keycloak:

| Variable | Default | Description |
|---|---|---|
| `GRAPH_API_URL` | `https://graph.microsoft.com/v1.0` | Microsoft Graph API base URL |
| `GRAPH_API_CONNECT_TIMEOUT_SECONDS` | `5` | Timeout for opening a connection to Graph API |
| `GRAPH_API_REQUEST_TIMEOUT_SECONDS` | `15` | Timeout for a single Graph API request |

If Graph API request fails or times out, login continues and the existing user data is kept.

## Group mappings

The Graph API Groups mapper maps Azure groups to Keycloak groups by Azure group display name. A mapping key can be either the display name as is (`Finance Group`) or URL-encoded (`Finance+Group`). Versions up to 1.1.5 matched only the plain display name and versions 1.1.6 – 1.2.0 only the URL-encoded one; from 1.2.1 both work, so mappings configured for any earlier version keep working.

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
  
---

<div id="metatavu-custom-footer"><div align="center">
    <img src="https://metatavu.fi/wp-content/uploads/2024/02/cropped-metatavu-favicon.jpg" alt="Organization Logo" width="100">
    <p>© 2025 Metatavu. All rights reserved.</p>
    <p>
        <a href="https://www.metatavu.fi">Website</a> | 
        <a href="https://twitter.com/metatavu">Twitter</a> | 
        <a href="https://fi.linkedin.com/company/metatavu">LinkedIn</a>
    </p>
</div></div>
