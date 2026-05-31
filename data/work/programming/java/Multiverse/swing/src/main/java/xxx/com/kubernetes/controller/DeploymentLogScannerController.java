package xxx.com.kubernetes.controller;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class DeploymentLogScannerController {

  private static final Logger logger =
      LoggerFactory.getLogger(DeploymentLogScannerController.class);
  private static final String DEPLOYMENT_NAME = "webapp";
  private static final String NAMESPACE = "default"; // Or your target namespace
  private static final String FAILURE_MESSAGE = "failure";

  public static void main(String[] args) {
    // Use a try-with-resources statement to ensure the client is closed automatically.
    try (KubernetesClient client = new KubernetesClientBuilder().build()) {
      logger.info(
          "Kubernetes client initialized. Watching deployment '{}' in namespace '{}'",
          DEPLOYMENT_NAME,
          NAMESPACE);

      // A latch to keep the main thread alive.
      final CountDownLatch latch = new CountDownLatch(1);

      // Watch for changes to the specific deployment
      client
          .apps()
          .deployments()
          .inNamespace(NAMESPACE)
          .withName(DEPLOYMENT_NAME)
          .watch(
              new Watcher<Deployment>() {
                @Override
                public void eventReceived(Action action, Deployment resource) {
                  logger.info(
                      "Watch event received: {} for deployment {}",
                      action,
                      resource.getMetadata().getName());

                  // We are interested when the deployment is modified, which can happen on new
                  // rollouts.
                  if (action == Action.MODIFIED) {
                    scanPodsForFailures(client, resource);
                  }
                }

                @Override
                public void onClose(WatcherException cause) {
                  logger.error("Watcher closed unexpectedly", cause);
                  latch.countDown(); // Release the main thread on close
                }
              });

      // Initial scan when the controller starts
      Deployment deployment =
          client.apps().deployments().inNamespace(NAMESPACE).withName(DEPLOYMENT_NAME).get();
      if (deployment != null) {
        scanPodsForFailures(client, deployment);
      } else {
        logger.warn(
            "Deployment '{}' not found in namespace '{}' on startup.", DEPLOYMENT_NAME, NAMESPACE);
      }

      // Keep the application running
      latch.await();
    } catch (InterruptedException e) {
      logger.error("Main thread interrupted", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.error("An unexpected error occurred", e);
    }
  }

  /**
   * Scans the logs of pods associated with a given deployment for a failure message. If the message
   * is found, it restarts the pod.
   *
   * @param client The Kubernetes client.
   * @param deployment The deployment whose pods to scan.
   */
  private static void scanPodsForFailures(KubernetesClient client, Deployment deployment) {
    // Get the labels of the deployment to find its pods
    Map<String, String> labels = deployment.getSpec().getSelector().getMatchLabels();
    if (labels == null || labels.isEmpty()) {
      logger.warn(
          "Deployment '{}' has no selector labels. Cannot find pods.",
          deployment.getMetadata().getName());
      return;
    }

    // List pods that match the deployment's labels
    List<Pod> pods = client.pods().inNamespace(NAMESPACE).withLabels(labels).list().getItems();
    logger.info(
        "Found {} pods for deployment '{}'", pods.size(), deployment.getMetadata().getName());

    for (Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      try {
        // Fetch the logs for the current pod
        String log = client.pods().inNamespace(NAMESPACE).withName(podName).getLog();

        // Check if the log contains the failure message
        if (log != null && log.contains(FAILURE_MESSAGE)) {
          logger.warn(
              "Found '{}' message in logs for pod '{}'. Restarting pod.", FAILURE_MESSAGE, podName);
          restartPod(client, pod);
        } else {
          logger.info("No failure message found in logs for pod '{}'", podName);
        }
      } catch (Exception e) {
        logger.error("Error processing logs for pod '{}'", podName, e);
      }
    }
  }

  /**
   * Restarts a pod by deleting it. The deployment's ReplicaSet will automatically create a new one
   * to meet the desired replica count.
   *
   * @param client The Kubernetes client.
   * @param pod The pod to restart.
   */
  private static void restartPod(KubernetesClient client, Pod pod) {
    String podName = pod.getMetadata().getName();
    String namespace = pod.getMetadata().getNamespace();
    try {
      // Deleting the pod will cause the controlling ReplicaSet to create a new one.
      client.pods().inNamespace(namespace).withName(podName).delete();
      logger.info("Successfully deleted pod '{}' to trigger restart.", podName);
    } catch (Exception e) {
      logger.error("Failed to delete pod '{}'", podName, e);
    }
  }
}
