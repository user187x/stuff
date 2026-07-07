package xxx.com.cloudprovider;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.time.Duration;

public class MockCloudProvider {

 // The mock IP we will assign to any pending Gateway LoadBalancer
 private static final String MOCK_IP = "192.168.100.100";

 public static void main(String[] args) throws IOException {
  // 1. Initialize client using local ~/.kube/config
  ApiClient client = Config.defaultClient();

  // 2. Prevent the watch connection from timing out
  client.setHttpClient(client.getHttpClient().newBuilder()
      .readTimeout(Duration.ZERO)
      .build());

  Configuration.setDefaultApiClient(client);
  CoreV1Api api = new CoreV1Api();

  System.out.println("Started Mock Cloud Provider. Watching for pending LoadBalancers...");

  // 3. Infinite watch loop
  while (true) {
   try (Watch<V1Service> watch = Watch.createWatch(
       client,
       api.listServiceForAllNamespaces().watch(true).buildCall(null),
       new TypeToken<Watch.Response<V1Service>>() {}.getType())) {

    for (Watch.Response<V1Service> item : watch) {
     V1Service svc = item.object;
     String type = item.type; // "ADDED", "MODIFIED", or "DELETED"

     if (svc != null && svc.getSpec() != null && "LoadBalancer".equals(svc.getSpec().getType())) {
      String name = svc.getMetadata().getName();
      String namespace = svc.getMetadata().getNamespace();

      // Check if the service already has an external IP assigned
      boolean hasIngress = svc.getStatus() != null &&
          svc.getStatus().getLoadBalancer() != null &&
          svc.getStatus().getLoadBalancer().getIngress() != null &&
          !svc.getStatus().getLoadBalancer().getIngress().isEmpty();

      // Only act on new or unprovisioned services
      if (!hasIngress && ("ADDED".equals(type) || "MODIFIED".equals(type))) {
       System.out.println("Found unprovisioned LoadBalancer: " + namespace + "/" + name);
       assignMockIp(api, namespace, name, MOCK_IP);
      }
     }
    }
   } catch (ApiException e) {
    System.err.println("API Exception: " + e.getResponseBody());
   } catch (Exception e) {
    System.err.println("Watch connection dropped. Reconnecting in 5s... (" + e.getMessage() + ")");
    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
   }
  }
 }

 private static void assignMockIp(CoreV1Api api, String namespace, String name, String ip) {
  // Use JSON Patch to 'add' the status.loadBalancer object
  // ('add' works for both creation and replacement per RFC 6902)
  String patchJson = String.format(
      "[{\"op\": \"add\", \"path\": \"/status/loadBalancer\", \"value\": {\"ingress\": [{\"ip\": \"%s\"}]}}]",
      ip
  );

  io.kubernetes.client.custom.V1Patch patch = new io.kubernetes.client.custom.V1Patch(patchJson);

  // Updated to 3 arguments for newer client versions
  api.patchNamespacedServiceStatus(name, namespace, patch);
  System.out.println("✅ Successfully patched " + namespace + "/" + name + " with external IP: " + ip);
 }
}