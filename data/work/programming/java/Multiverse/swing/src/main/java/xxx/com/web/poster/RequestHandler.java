package xxx.com.web.poster;

import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import org.jetbrains.annotations.NotNull;
import org.jfree.data.gantt.TaskSeriesCollection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.net.ssl.*;
import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the logic of creating and sending HTTP requests using OkHttp.
 * Now also fetches sub-resources for HTML responses, captures their timelines,
 * and handles large responses by streaming to disk.
 */
public class RequestHandler {

    private final RequestPanel requestPanel;
    private final RequestDataPanel requestDataPanel;
    private ResponsePanel responsePanel;
    private OkHttpClient client;
    private static final long MAX_MEMORY_RESPONSE_SIZE = 10 * 1024 * 1024; // 10MB

    // Pattern to extract link URL and relation type from a Link header part
    private static final Pattern LINK_HEADER_PATTERN = Pattern.compile("<(.*?)>;\\s*rel=\"(.*?)\"");

    public RequestHandler(RequestPanel requestPanel, RequestDataPanel requestDataPanel, ResponsePanel responsePanel) {
        this.requestPanel = requestPanel;
        this.requestDataPanel = requestDataPanel;
        this.responsePanel = responsePanel;
    }

    public void setResponsePanel(ResponsePanel responsePanel) {
        this.responsePanel = responsePanel;
    }

    public void executeRequest() {
        responsePanel.reset();

        CookieDataStore cookieJar = new CookieDataStore();
        TimelineEventListener.Factory timelineEventListenerFactory = new TimelineEventListener.Factory();

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        // Follow Redirects
        clientBuilder.followRedirects(requestPanel.isFollowRedirects());

        // Timeout
        clientBuilder.connectTimeout(requestPanel.getTimeout(), TimeUnit.SECONDS);
        clientBuilder.readTimeout(requestPanel.getTimeout(), TimeUnit.SECONDS);
        clientBuilder.writeTimeout(requestPanel.getTimeout(), TimeUnit.SECONDS);


        // Client Certificate (TLS)
        try {
            String certPath = requestPanel.getCertPath();
            char[] certPassword = requestPanel.getCertPassword();
            if (certPath != null && !certPath.trim().isEmpty()) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (InputStream keyStoreStream = new FileInputStream(certPath)) {
                    keyStore.load(keyStoreStream, certPassword);
                }

                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, certPassword);

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null); // Use default JVM trust store
                X509TrustManager defaultTrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{defaultTrustManager}, new SecureRandom());

                clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), defaultTrustManager);
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                responsePanel.setStatus("TLS Error");
                responsePanel.setBody(e.getMessage(), "text/plain");
            });
            return;
        }

        clientBuilder.cookieJar(cookieJar);
        clientBuilder.eventListenerFactory(timelineEventListenerFactory);

        client = clientBuilder.build();

        Request.Builder requestBuilder = new Request.Builder();

        // URL and Method
        try {
            String url = requestPanel.getUrl();
            if (url == null || url.trim().isEmpty()) {
                return; // Don't send empty requests
            }
            requestBuilder.url(url);
            String method = requestPanel.getMethod();
            RequestBody body = null;
            if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                // Correctly get body from requestDataPanel
                body = RequestBody.create(requestDataPanel.getBody(), null);
            }
            requestBuilder.method(method, body);

        } catch (IllegalArgumentException e) {
            SwingUtilities.invokeLater(() -> {
                responsePanel.setStatus("Invalid URL");
                responsePanel.setBody(e.getMessage(), "text/plain");
            });
            return;
        }

        // Headers
        boolean hasUserAgent = false;
        // Correctly get headers from requestDataPanel
        for (Map.Entry<String, String> header : requestDataPanel.getHeaders().entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
            if (header.getKey().equalsIgnoreCase("User-Agent")) {
                hasUserAgent = true;
            }
        }

        // Add a default browser-like User-Agent if not provided
        if (!hasUserAgent) {
            requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");
        }


        // Basic Auth
        String username = requestPanel.getUsername();
        char[] password = requestPanel.getPassword();
        if (username != null && !username.trim().isEmpty()) {
            String credentials = Credentials.basic(username, new String(password));
            requestBuilder.header("Authorization", credentials);
        }

        Request request = requestBuilder.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                SwingUtilities.invokeLater(() -> {
                    responsePanel.setStatus("Error");
                    responsePanel.setBody(e.getMessage(), "text/plain");
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                final String status = response.code() + " " + response.message();
                final Headers headers = response.headers();
                final String contentType = response.header("Content-Type");
                final String redirects = traceRedirects(response);
                final List<Cookie> cookies = cookieJar.loadForRequest(request.url());
                final TaskSeriesCollection mainTimelineData = timelineEventListenerFactory.getTimelineData();
                final Map<String, String> paginationLinks = parseLinkHeader(headers.get("Link"));

                Resource mainResource;
                Map<Resource, TaskSeriesCollection> subResourceTimelines = new LinkedHashMap<>();

                try (ResponseBody responseBody = response.body()) {
                    long contentLength = responseBody.contentLength();

                    if (contentLength > MAX_MEMORY_RESPONSE_SIZE || contentLength == -1) {
                        File tempFile = File.createTempFile("poster-response-", ".tmp");
                        tempFile.deleteOnExit();
                        try (BufferedSink sink = Okio.buffer(Okio.sink(tempFile))) {
                            sink.writeAll(responseBody.source());
                        }
                        mainResource = new Resource(request.url().toString(), contentType, tempFile);
                    } else {
                        byte[] responseBytes = responseBody.bytes();
                        mainResource = new Resource(request.url().toString(), contentType, responseBytes);

                        if (contentType != null && contentType.toLowerCase().contains("html")) {
                            String htmlBody = new String(responseBytes);
                            Document doc = Jsoup.parse(htmlBody, request.url().toString());

                            fetchResources(client, doc.select("link[href]"), "abs:href", subResourceTimelines);
                            fetchResources(client, doc.select("script[src]"), "abs:src", subResourceTimelines);
                            fetchResources(client, doc.select("img[src]"), "abs:src", subResourceTimelines);
                        }
                    }
                }


                SwingUtilities.invokeLater(() -> {
                    responsePanel.setStatus(status);
                    responsePanel.setHeaders(headers);
                    responsePanel.setRedirects(redirects);
                    responsePanel.setCookies(cookies);
                    responsePanel.updateTimeline(mainTimelineData, subResourceTimelines);
                    responsePanel.setPagination(paginationLinks);
                    responsePanel.setResources(mainResource, new ArrayList<>(subResourceTimelines.keySet()));
                });
            }
        });
    }

    /**
     * Parses the 'Link' HTTP response header to find pagination links.
     * This parser is designed to be robust against extra whitespace and uses a regular expression.
     *
     * @param linkHeader The value of the Link header from the HTTP response.
     * @return A map of relation types (e.g., "next", "prev", "last") to their corresponding URLs.
     */
    private Map<String, String> parseLinkHeader(String linkHeader) {
        if (linkHeader == null || linkHeader.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> links = new LinkedHashMap<>();
        // The Link header can contain multiple links, separated by commas.
        String[] parts = linkHeader.split(",\\s*");

        for (String part : parts) {
            Matcher matcher = LINK_HEADER_PATTERN.matcher(part);
            if (matcher.find()) {
                String url = matcher.group(1);
                String rel = matcher.group(2).toLowerCase();
                links.put(rel, url);
            }
        }
        return links;
    }

    private void fetchResources(OkHttpClient client, Elements elements, String urlAttribute, Map<Resource, TaskSeriesCollection> resourceTimelines) {
        for (org.jsoup.nodes.Element element : elements) {
            String url = element.attr(urlAttribute);

            // Skip empty, data URIs, and invalid URLs
            if (url.trim().isEmpty() || url.startsWith("data:") || url.startsWith("javascript:") || url.startsWith("#") || url.startsWith("mailto:")) {
                continue;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                continue;
            }


            TimelineEventListener.Factory subResourceTimelineFactory = new TimelineEventListener.Factory();
            OkHttpClient subResourceClient = client.newBuilder()
                    .eventListenerFactory(subResourceTimelineFactory)
                    .build();


            Request subRequest = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response subResponse = subResourceClient.newCall(subRequest).execute()) {
                if (subResponse.isSuccessful()) {
                    byte[] bodyBytes = subResponse.body().bytes();

                    if (bodyBytes.length <= 15 * 1024 * 1024) { // Limit to 15MB
                        Resource resource = new Resource(
                                url,
                                subResponse.header("Content-Type"),
                                bodyBytes
                        );
                        resourceTimelines.put(resource, subResourceTimelineFactory.getTimelineData());
                    } else {
                        System.err.println("Skipping large resource: " + url + " (" + bodyBytes.length + " bytes)");
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                System.err.println("Failed to fetch sub-resource: " + url + " - " + e.getMessage());

            }
        }
    }

    private String traceRedirects(Response response) {
        // Build the chain of responses from oldest to newest
        List<Response> responseChain = new ArrayList<>();
        Response current = response;

        while (current != null) {
            responseChain.addFirst(current); // Add to beginning to maintain chronological order
            current = current.priorResponse();
        }

        // If there's only one response, there were no redirects
        if (responseChain.size() <= 1) {
            return "No redirects.";
        }

        // Build a clear redirect trace
        StringBuilder trace = new StringBuilder();
        trace.append("Redirect chain:\n");

        for (int i = 0; i < responseChain.size(); i++) {
            Response r = responseChain.get(i);

            if (i == 0) {
                // First request
                trace.append(String.format("  1. Initial request to %s\n", r.request().url()));
            } else if (i < responseChain.size() - 1) {
                // Intermediate redirect
                Response prev = responseChain.get(i - 1);
                trace.append(String.format("  %d. %d %s → %s\n",
                        i + 1,
                        prev.code(),
                        prev.message(),
                        r.request().url()));
            } else {
                // Final response
                Response prev = responseChain.get(i - 1);
                trace.append(String.format("  %d. %d %s → %s (final)\n",
                        i + 1,
                        prev.code(),
                        prev.message(),
                        r.request().url()));
                trace.append(String.format("\nFinal response: %d %s",
                        r.code(),
                        r.message()));
            }
        }

        return trace.toString();
    }
}