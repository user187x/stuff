package xxx.keycloak;

import java.util.Collections;
import java.util.List;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class X509AutoProvisionAuthenticatorFactory implements AuthenticatorFactory {

  public static final String PROVIDER_ID = "x509-auto-provision";
  private static final X509AutoProvisionAuthenticator SINGLETON =
      new X509AutoProvisionAuthenticator();

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "X509 Auto-Provisioning Authenticator";
  }

  @Override
  public String getHelpText() {
    return "Extracts X509 cert, provisions user if missing, and authenticates.";
  }

  @Override
  public String getReferenceCategory() {
    return "x509";
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENT_CHOICES;
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return Collections.emptyList();
  }

  @Override
  public Authenticator create(KeycloakSession session) {
    return SINGLETON;
  }

  @Override
  public void init(org.keycloak.Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
