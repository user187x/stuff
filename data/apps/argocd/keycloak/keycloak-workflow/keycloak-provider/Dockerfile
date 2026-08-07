# Use the same Keycloak version declared in your pom.xml
FROM quay.io/keycloak/keycloak:24.0.0

# Copy the SPI into the providers directory
COPY target/keycloak-x509-autoprovision.jar /opt/keycloak/providers/x509-autoprovision.jar

# Run the Keycloak build step to optimize the server with the new provider
RUN /opt/keycloak/bin/kc.sh build
