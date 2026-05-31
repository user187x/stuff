package xxx.com.kubernetes.operator;

import java.util.List;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;


public class MyResourceOperator {

  public static void main(String[] args) {

    try (KubernetesClient client = new KubernetesClientBuilder().build()) {

      MixedOperation<MyResource, MyResourceList, Resource<MyResource>> myResourceClient =
          client.resources(MyResource.class, MyResourceList.class);

      // Watch for MyResource objects
      myResourceClient.inAnyNamespace().watch(new MyResourceWatcher());

      // Keep the operator running
      Thread.sleep(Long.MAX_VALUE);
    }
    catch (InterruptedException e) {

      Thread.currentThread().interrupt();
    }
  }

  @Group("example.com")
  @Version("v1")
  @Kind("MyResource")
  public class MyResource extends CustomResource<MyResourceSpec, MyResourceStatus> {

    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
      return "MyResource{" + "metadata=" + getMetadata() + ", spec=" + getSpec() + '}';
    }
  }

  public static class MyResourceSpec {
    private String message;

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return "MyResourceSpec{message='" + message + "'}";
    }
  }

  public static class MyResourceStatus {
    private String statusMessage;

    public String getStatusMessage() {
      return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
      this.statusMessage = statusMessage;
    }

    @Override
    public String toString() {
      return "MyResourceStatus{statusMessage='" + statusMessage + "'}";
    }
  }

  public static class MyResourceList implements KubernetesResourceList<MyResource> {

    private static final long serialVersionUID = 1L;

    @Override
    public ListMeta getMetadata() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public List<MyResource> getItems() {
      // TODO Auto-generated method stub
      return null;
    }
  }

  public static class MyResourceWatcher implements Watcher<MyResource> {

    @Override
    public void eventReceived(Action action, MyResource resource) {

      System.out.println("Event received: " + action + " for resource: " + resource); // Debug print

      if (action == Action.ADDED || action == Action.MODIFIED) {
        createOrUpdateConfigMap(resource);
      }
      else if (action == Action.DELETED) {
        deleteConfigMap(resource);
      }
    }

    private void createOrUpdateConfigMap(MyResource resource) {

      try (KubernetesClient client = new KubernetesClientBuilder().build()) {

        String message = resource.getSpec().getMessage();
        String configMapName = "myresource-configmap-" + resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        ConfigMap configMap = new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder().withName(configMapName).withNamespace(namespace).build())
            .addToData("message", message).build();

        ConfigMap createdOrUpdatedConfigMap =
            client.configMaps().inNamespace(namespace).resource(configMap).create();

        if (createdOrUpdatedConfigMap != null) {
          System.out.println(
              "Created/Updated ConfigMap for MyResource: " + resource.getMetadata().getName());
        }
        else {
          System.err.println("Failed to Create/Update ConfigMap for MyResource: "
              + resource.getMetadata().getName());
        }
      }
      catch (Exception e) {
        System.err.println("Error creating/updating ConfigMap: " + e.getMessage());
        e.printStackTrace();
      }
    }

    private void deleteConfigMap(MyResource resource) {

      try (KubernetesClient client = new KubernetesClientBuilder().build()) {

        String configMapName = "myresource-configmap-" + resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        List<StatusDetails> deleteStatus =
            client.configMaps().inNamespace(namespace).withName(configMapName).delete();

        if (deleteStatus != null && !deleteStatus.isEmpty()) {

          // Check if any of the statuses indicate success.
          boolean deleted = deleteStatus.stream().anyMatch(status -> !status.getCauses().isEmpty());

          if (deleted) {

            System.out
                .println("Deleted ConfigMap for MyResource: " + resource.getMetadata().getName());
          }
          else {

            System.out.println("ConfigMap " + configMapName + " deletion failed");

            for (StatusDetails status : deleteStatus) {
              System.err.println("Deletion Status: " + status);
            }
          }

        }
        else {

          System.out.println("ConfigMap " + configMapName + " not found or already deleted.");
        }

      }
      catch (Exception e) {

        System.err.println("Error deleting ConfigMap: " + e.getMessage());
        e.printStackTrace();
      }
    }

    @Override
    public void onClose(WatcherException cause) {

      if (cause != null) {
        System.err.println("Watcher closed due to: " + cause.getMessage());
      }
      else {
        System.err.println("Watcher closed normally");
      }
    }

    @Override
    public void onClose() {

      System.out.println("Watcher closing");
      Watcher.super.onClose();
    }
  }
}
