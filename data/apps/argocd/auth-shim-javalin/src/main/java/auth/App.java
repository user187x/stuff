package auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class App {

    public record HeaderMapping(String targetHeader, String valueSourceKey) {}
    public record ExtractionRule(String jsonPath, String saveToKey) {}
    public record PayloadExtraction(String sourceKey, String saveToKey) {}

    public record ServiceConfig(
            String id,
            String name,
            String url,
            List<HeaderMapping> headers,
            List<PayloadExtraction> payloadExtractions,
            List<ExtractionRule> responseExtractions,
            boolean collapsed
    ) {}

    private static final List<ServiceConfig> configStack = new CopyOnWriteArrayList<>();
    private static final Gson gson = new Gson();

    // Configured for mTLS/x509 Passthrough
    private static final HttpClient httpClient = buildMtlsHttpClient();

    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final Map<String, AtomicInteger> dnCounts = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/static";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(8080);

        // Serve extracted HTML from resources
        app.get("/", ctx -> {
            try (InputStream is = App.class.getResourceAsStream("/public/index.html")) {
                if (is != null) {
                    ctx.html(new String(is.readAllBytes()));
                } else {
                    ctx.status(404).result("index.html not found in classpath");
                }
            }
        });

        app.get("/api/config", ctx ->
            ctx.contentType("application/json").result(gson.toJson(configStack))
        );

        app.post("/api/config", ctx -> {
            List<ServiceConfig> newStack = gson.fromJson(ctx.body(), new TypeToken<List<ServiceConfig>>(){}.getType());
            configStack.clear();
            configStack.addAll(newStack);
            ctx.status(200).result("Saved");
        });

        app.get("/api/metrics", ctx -> {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("successCount", successCount.get());
            metrics.put("failCount", failCount.get());

            List<Map<String, Object>> topDns = new ArrayList<>();
            dnCounts.forEach((dn, c) -> topDns.add(Map.of("dn", dn, "count", c.get())));
            topDns.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));

            metrics.put("requesters", topDns.stream().limit(25).toList());
            ctx.contentType("application/json").result(gson.toJson(metrics));
        });

        // Delegate to isolated handler
        AuthPipelineHandler pipelineHandler = new AuthPipelineHandler();
        app.get("/auth", pipelineHandler::handle);
        app.post("/auth", pipelineHandler::handle);
    }

    /**
     * Initializes the HttpClient with an SSLContext capable of mTLS.
     * Can be driven by environment variables mapped via Kubernetes Secrets/ConfigMaps.
     */
    private static HttpClient buildMtlsHttpClient() {
        try {
            String keystorePath = System.getenv("MTLS_KEYSTORE_PATH");
            String keystorePass = System.getenv("MTLS_KEYSTORE_PASS");
            String truststorePath = System.getenv("MTLS_TRUSTSTORE_PATH");
            String truststorePass = System.getenv("MTLS_TRUSTSTORE_PASS");

            // If no mTLS env vars are provided, fall back to the system default SSLContext
            if (keystorePath == null && truststorePath == null) {
                return HttpClient.newBuilder().build();
            }

            KeyManagerFactory kmf = null;
            if (keystorePath != null && keystorePass != null) {
                KeyStore identityStore = KeyStore.getInstance("PKCS12");
                try (InputStream ksIn = new FileInputStream(keystorePath)) {
                    identityStore.load(ksIn, keystorePass.toCharArray());
                }
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(identityStore, keystorePass.toCharArray());
            }

            TrustManagerFactory tmf = null;
            if (truststorePath != null && truststorePass != null) {
                KeyStore trustStore = KeyStore.getInstance("PKCS12"); // or JKS
                try (InputStream tsIn = new FileInputStream(truststorePath)) {
                    trustStore.load(tsIn, truststorePass.toCharArray());
                }
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
            }

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(
                kmf != null ? kmf.getKeyManagers() : null,
                tmf != null ? tmf.getTrustManagers() : null,
                new SecureRandom()
            );

            return HttpClient.newBuilder().sslContext(sslContext).build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize mTLS SSLContext", e);
        }
    }

    /**
     * Dedicated inner class to isolate the authentication pipeline execution logic.
     */
    public static class AuthPipelineHandler {

        public void handle(Context ctx) throws Exception {
            Map<String, String> requestContext = extractInitialContext(ctx);

            for (ServiceConfig service : configStack) {
                mapIncomingPayload(service, requestContext);

                HttpRequest request = buildExternalRequest(service, requestContext);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    recordRequestMetrics(ctx, false);
                    ctx.status(401).result("Unauthorized: Failed at " + service.name());
                    return;
                }

                extractResponsePayload(service, requestContext, response.body());
            }

            forwardHeadersToTraefik(ctx, requestContext);
            recordRequestMetrics(ctx, true);
            ctx.status(200).result("OK");
        }

        private Map<String, String> extractInitialContext(Context ctx) {
            Map<String, String> context = new HashMap<>();
            ctx.headerMap().forEach((k, v) -> context.put("header." + k.toLowerCase(), v));

            try {
                String body = ctx.body().trim();
                if (body.startsWith("{")) {
                    JsonObject bodyNode = JsonParser.parseString(body).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : bodyNode.entrySet()) {
                        String val = entry.getValue().isJsonPrimitive()
                            ? entry.getValue().getAsString()
                            : entry.getValue().toString();
                        context.put("payload." + entry.getKey(), val);
                    }
                }
            } catch (Exception ignored) {}
            return context;
        }

        private void mapIncomingPayload(ServiceConfig service, Map<String, String> context) {
            if (service.payloadExtractions() != null) {
                for (PayloadExtraction pe : service.payloadExtractions()) {
                    if (context.containsKey(pe.sourceKey())) {
                        context.put(pe.saveToKey(), context.get(pe.sourceKey()));
                    }
                }
            }
        }

        private HttpRequest buildExternalRequest(ServiceConfig service, Map<String, String> context) {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(service.url())).GET();
            if (service.headers() != null) {
                for (HeaderMapping hm : service.headers()) {
                    String value = context.getOrDefault(hm.valueSourceKey(), "");
                    reqBuilder.header(hm.targetHeader(), value);
                }
            }
            return reqBuilder.build();
        }

        private void extractResponsePayload(ServiceConfig service, Map<String, String> context, String responseBody) {
            if (service.responseExtractions() != null && !service.responseExtractions().isEmpty()) {
                Object document = JsonPath.parse(responseBody);
                for (ExtractionRule rule : service.responseExtractions()) {
                    try {
                        String extracted = JsonPath.read(document, rule.jsonPath()).toString();
                        context.put(rule.saveToKey(), extracted);
                    } catch (Exception ignored) {}
                }
            }
        }

        private void forwardHeadersToTraefik(Context ctx, Map<String, String> context) {
            context.forEach((k, v) -> {
                if (!k.startsWith("header.") && !k.startsWith("payload.")) {
                    ctx.header("X-Auth-Shim-" + k, v);
                }
            });
        }

        private void recordRequestMetrics(Context ctx, boolean success) {
            if (success) successCount.incrementAndGet();
            else failCount.incrementAndGet();

            String dn = ctx.header("X-Forwarded-Tls-Client-Cert-Subject");
            if (dn == null || dn.isBlank()) {
                dn = ctx.header("X-Forwarded-Client-Cert-Dn");
            }
            if (dn == null || dn.isBlank()) {
                dn = "No Client Certificate";
            }

            dnCounts.computeIfAbsent(dn, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
}
