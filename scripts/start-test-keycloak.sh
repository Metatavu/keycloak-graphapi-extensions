#!/bin/sh

GRAPHAPI_EXTENSION_JAR=$(ls ./build/libs/*.jar | head -n 1)

docker run -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v ./src/test/resources/kc-test.json:/opt/keycloak/data/import/kc-test.json \
  -v ./src/test/resources/kc-azure.json:/opt/keycloak/data/import/kc-azure.json \
  -v $GRAPHAPI_EXTENSION_JAR:/opt/keycloak/providers/graphapi-extensions.jar \
  quay.io/keycloak/keycloak:26.1.2 start-dev --import-realm


