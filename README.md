# Keycloak GraphAPI extensions

This repository provides custom Keycloak extensions that enable integration with the Microsoft Graph API.

Supported Keycloak version: 26.3.x

## Configuration

The extension is configured with the following optional environment variables in Keycloak:

| Variable | Default | Description |
|---|---|---|
| `GRAPH_API_URL` | `https://graph.microsoft.com/v1.0` | Microsoft Graph API base URL |
| `GRAPH_API_CONNECT_TIMEOUT_SECONDS` | `5` | Timeout for opening a connection to Graph API |
| `GRAPH_API_REQUEST_TIMEOUT_SECONDS` | `15` | Timeout for a single Graph API request |

If Graph API request fails or times out, login continues and the existing user data is kept.

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
