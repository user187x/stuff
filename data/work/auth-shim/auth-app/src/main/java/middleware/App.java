package middleware;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
  private static final Logger log = LoggerFactory.getLogger(App.class);

  private static final int PORT =
      Integer.parseInt(System.getenv().getOrDefault("AUTH_SHIM_PORT", "8080"));
  private static final String VALIDATOR_URL = System.getenv().getOrDefault("VALIDATOR_URL", "https://auth-api.local/verify");
  private static final String TLS_CRT_PATH = System.getenv().getOrDefault("TLS_CRT_PATH", "/etc/certs/tls.crt");

  // The single, thread-safe HTTP client for the lifecycle of the app
  private static final HttpClient httpClient = buildSecureHttpClient();

  public static void main(String[] args) {
    Javalin.create(config -> {
      config.http.defaultContentType = "text/plain";
      config.routes.get("/auth", App::handleForwardAuth);
      config.routes.post("/auth", App::handleForwardAuth);
      config.routes.get("/health", ctx -> ctx.status(200).result("OK"));
    }).start(PORT);

    log.info("Auth-Shim started on port {}. External Validator URL: {}", PORT, VALIDATOR_URL);
  }

  private static void handleForwardAuth(Context ctx) {
    String correlationId = UUID.randomUUID().toString();
    String userDn = extractUserDn(ctx);

    if (userDn == null) {
      log.warn("[{}] Denied: No client certificate DN provided in headers", correlationId);
      ctx.status(401).result("Client Certificate Required");
      return;
    }

    log.info("[{}] Extracted DN: {}. Validating externally...", correlationId, userDn);

    // Call the external API to validate the DN
    boolean isValid = validateDnExternally(userDn, correlationId);

    if (isValid) {
      log.info("[{}] Authorized: DN validated successfully", correlationId);
      ctx.header("X-Forwarded-User-Dn", userDn);
      ctx.status(200).result("OK");
    } else {
      log.warn("[{}] Denied: External validation failed for DN", correlationId);
      ctx.status(403).result("Forbidden: Invalid Certificate Identity");
    }
  }

  /**
   * Makes a secure HTTP GET request to the external validator service.
   */
  private static boolean validateDnExternally(String dn, String correlationId) {
    try {
      String encodedDn = URLEncoder.encode(dn, StandardCharsets.UTF_8);
      URI targetUri = URI.create(VALIDATOR_URL + "?dn=" + encodedDn);

      HttpRequest request = HttpRequest.newBuilder().uri(targetUri)
          .header("X-Request-Id", correlationId).timeout(Duration.ofSeconds(3)).GET().build();

      // Send request synchronously. Expecting a 200 OK if valid.
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.warn("[{}] External validator returned status {}: {}", correlationId,
            response.statusCode(), response.body());
        return false;
      }
      return true;

    } catch (Exception e) {
      log.error("[{}] External validation request failed", correlationId, e);
      return false; // Deny access securely on failure
    }
  }

  /**
   * Builds a standard Java HttpClient equipped with a custom SSLContext that trusts the provided K8s
   * secret tls.crt file.
   */
  private static HttpClient buildSecureHttpClient() {
    try {
      // 1. Read the raw PEM certificate file
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert;
      try (InputStream is = new FileInputStream(TLS_CRT_PATH)) {
        caCert = (X509Certificate) cf.generateCertificate(is);
      }

      // 2. Load the certificate into an empty in-memory KeyStore
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null); // Initialize empty
      keyStore.setCertificateEntry("custom-ca", caCert);

      // 3. Initialize TrustManager with the custom KeyStore
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);

      // 4. Create the SSLContext using the custom TrustManager
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

      log.info("Successfully loaded custom TLS certificate from {}", TLS_CRT_PATH);

      return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(5))
          .build();

    } catch (Exception e) {
      log.warn("Could not load custom TLS cert at {}. Falling back to default system TrustStore.",
          TLS_CRT_PATH);
      // Fallback to a standard client if the secret isn't mounted locally during dev
      return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
  }

  // --- (Previous DN Extraction Logic remains exactly the same) ---
  private static String extractUserDn(Context ctx) {
    String pemHeader = ctx.header("X-Forwarded-Tls-Client-Cert");
    if (pemHeader != null && !pemHeader.isBlank()) {
      try {
        byte[] certBytes = pemHeader.getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert =
            (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        return cert.getSubjectX500Principal().getName();
      } catch (Exception e) {
        log.error("Failed to parse PEM certificate header", e);
      }
    }

    String infoHeader = ctx.header("X-Forwarded-Tls-Client-Cert-Info");
    if (infoHeader != null && !infoHeader.isBlank()) {
      try {
        String decoded = URLDecoder.decode(infoHeader, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("Subject=\"(.*?)\"");
        Matcher matcher = pattern.matcher(decoded);
        if (matcher.find()) {
          return matcher.group(1);
        }
      } catch (Exception e) {
        log.error("Failed to parse Info certificate header", e);
      }
    }
    return null;
  }
}
