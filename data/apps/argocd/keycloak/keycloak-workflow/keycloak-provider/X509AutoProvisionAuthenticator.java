package xxx.keycloak;

import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.x509.X509ClientCertificateLookup;

public class X509AutoProvisionAuthenticator implements Authenticator {

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    try {
      // 1. Extract the certificate forwarded by Traefik
      X509ClientCertificateLookup provider =
          context.getSession().getProvider(X509ClientCertificateLookup.class);
      if (provider == null) {
        context.attempted();
        return;
      }

      X509Certificate[] certs = provider.getCertificateChain(context.getHttpRequest());
      if (certs == null || certs.length == 0) {
        context.attempted();
        return;
      }

      // 2. Extract Common Name (CN) from the Subject DN
      String dn = certs[0].getSubjectX500Principal().getName();
      String username = extractCN(dn);

      if (username == null || username.trim().isEmpty()) {
        context.attempted();
        return;
      }

      RealmModel realm = context.getRealm();
      KeycloakSession session = context.getSession();

      // 3. Lookup user, create if they do not exist
      UserModel user = session.users().getUserByUsername(realm, username);
      if (user == null) {
        user = session.users().addUser(realm, username);
        user.setEnabled(true);
        user.setSingleAttribute("provisioning_method", "x509-auto");

        // Auto-assign to the group created in the previous script
        GroupModel group = KeycloakModelUtils.findGroupByPath(realm, "/X509-Autoprovisioned-Users");
        if (group != null) {
          user.joinGroup(group);
        }
      }

      // 4. Authenticate the user
      context.setUser(user);
      context.success();

    } catch (Exception e) {
      context.attempted();
    }
  }

  private String extractCN(String dn) {
    try {
      LdapName ldapDN = new LdapName(dn);
      for (Rdn rdn : ldapDN.getRdns()) {
        if (rdn.getType().equalsIgnoreCase("CN")) {
          return rdn.getValue().toString();
        }
      }
    } catch (Exception e) {
      return null;
    }
    return null;
  }

  @Override
  public void action(AuthenticationFlowContext context) {}

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  @Override
  public void close() {}
}
