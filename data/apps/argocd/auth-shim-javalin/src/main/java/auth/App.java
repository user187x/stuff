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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
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
    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final Deque<String> recentRequesters = new ConcurrentLinkedDeque<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/static";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(8080);

        app.get("/", App::renderHtml);

        app.get("/api/config", ctx ->
            ctx.contentType("application/json").result(gson.toJson(configStack))
        );

        app.post("/api/config", ctx -> {
            List<ServiceConfig> newStack = gson.fromJson(
                ctx.body(),
                new TypeToken<List<ServiceConfig>>(){}.getType()
            );
            configStack.clear();
            configStack.addAll(newStack);
            ctx.status(200).result("Saved");
        });

        app.get("/api/metrics", ctx -> {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("successCount", successCount.get());
            metrics.put("failCount", failCount.get());
            metrics.put("requesters", new ArrayList<>(recentRequesters));
            ctx.contentType("application/json").result(gson.toJson(metrics));
        });

        app.get("/auth", App::handleAuth);
        app.post("/auth", App::handleAuth);
    }

    private static void renderHtml(Context ctx) {
        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Traefik Auth-Shim Pipeline</title>
                <style>
                    body { font-family: system-ui, sans-serif; background: #f4f4f5; padding: 20px; color: #333; }
                    .container { max-width: 1000px; margin: 0 auto; }
                    .card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    .controls { display: flex; gap: 10px; margin-bottom: 15px; }
                    .stack-item { border-left: 4px solid #3b82f6; padding-left: 15px; margin-bottom: 30px; background: white; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 15px; }
                    .stack-item.dragging { opacity: 0.4; }
                    .drag-handle { cursor: grab; font-size: 1.4em; color: #9ca3af; margin-right: 10px; user-select: none; }
                    .drag-handle:active { cursor: grabbing; }
                    button { cursor: pointer; padding: 6px 12px; background: #3b82f6; color: white; border: none; border-radius: 4px; }
                    button.danger { background: #ef4444; }
                    button.secondary { background: #e5e7eb; color: #374151; }
                    input, select { padding: 6px; margin: 4px 0; width: 250px; border: 1px solid #ccc; border-radius: 4px;}
                    .row { display: flex; gap: 10px; align-items: center; margin-bottom: 5px; }

                    /* Table styling to support sticky headers and approx 10-item scrolling */
                    .table-container { max-height: 400px; overflow-y: auto; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { padding: 8px; text-align: left; border-bottom: 1px solid #e5e7eb; }
                    th {
                        background: #f9fafb;
                        font-weight: 600;
                        position: sticky;
                        top: 0;
                        box-shadow: 0 1px 0 #e5e7eb;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Auth-Shim Pipeline Configuration</h1>
                    <div class="controls">
                        <button onclick="addService()">+ Add External Service</button>
                        <button onclick="saveStack()" style="background: #10b981;">Save Pipeline</button>
                    </div>

                    <div id="pipeline-container"></div>

                    <div style="display: flex; gap: 20px; margin-top: 30px;">
                        <div class="card table-container" style="flex: 2;">
                            <h3 style="margin-top:0;">Live Requests</h3>
                            <table id="metricsTable">
                                <thead>
                                    <tr>
                                        <th>Time</th>
                                        <th>Success Total</th>
                                        <th>Failed Total</th>
                                    </tr>
                                </thead>
                                <tbody></tbody>
                            </table>
                        </div>
                        <div class="card table-container" style="flex: 1;">
                            <h3 style="margin-top:0;">Last 25 Requesters</h3>
                            <table id="requesterTable">
                                <thead>
                                    <tr><th>DNS / IP</th></tr>
                                </thead>
                                <tbody></tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <script src="/static/app.js"></script>
                <script src="/static/util.js"></script>
            </body>
            </html>
            """;
        ctx.html(html);
    }

    private static void handleAuth(Context ctx) throws Exception {
        Map<String, String> requestContext = new HashMap<>();
        ctx.headerMap().forEach((k, v) -> requestContext.put("header." + k.toLowerCase(), v));

        try {
            String body = ctx.body().trim();
            if (body.startsWith("{")) {
                JsonObject bodyNode = JsonParser.parseString(body).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : bodyNode.entrySet()) {
                    String val = entry.getValue().isJsonPrimitive()
                        ? entry.getValue().getAsString()
                        : entry.getValue().toString();
                    requestContext.put("payload." + entry.getKey(), val);
                }
            }
        } catch (Exception ignored) {}

        for (ServiceConfig service : configStack) {
            if (service.payloadExtractions() != null) {
                for (PayloadExtraction pe : service.payloadExtractions()) {
                    if (requestContext.containsKey(pe.sourceKey())) {
                        requestContext.put(pe.saveToKey(), requestContext.get(pe.sourceKey()));
                    }
                }
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(service.url()))
                    .GET();

            if (service.headers() != null) {
                for (HeaderMapping hm : service.headers()) {
                    String value = requestContext.getOrDefault(hm.valueSourceKey(), "");
                    reqBuilder.header(hm.targetHeader(), value);
                }
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                recordRequestMetrics(ctx, false);
                ctx.status(401).result("Unauthorized: Failed at " + service.name());
                return;
            }

            if (service.responseExtractions() != null && !service.responseExtractions().isEmpty()) {
                Object document = JsonPath.parse(response.body());
                for (ExtractionRule rule : service.responseExtractions()) {
                    try {
                        String extracted = JsonPath.read(document, rule.jsonPath()).toString();
                        requestContext.put(rule.saveToKey(), extracted);
                    } catch (Exception ignored) {}
                }
            }
        }

        requestContext.forEach((k, v) -> {
            if (!k.startsWith("header.") && !k.startsWith("payload.")) {
                ctx.header("X-Auth-Shim-" + k, v);
            }
        });

        recordRequestMetrics(ctx, true);
        ctx.status(200).result("OK");
    }

    private static void recordRequestMetrics(Context ctx, boolean success) {
        if (success) successCount.incrementAndGet();
        else failCount.incrementAndGet();

        String requesterDns = ctx.header("X-Forwarded-For");
        if (requesterDns == null || requesterDns.isBlank()) {
            requesterDns = ctx.req().getRemoteHost();
        }

        recentRequesters.addFirst(requesterDns);
        while (recentRequesters.size() > 25) {
            recentRequesters.removeLast();
        }
    }
}
