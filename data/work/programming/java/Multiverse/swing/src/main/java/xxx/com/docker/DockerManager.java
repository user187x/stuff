package xxx.com.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerManager {

  private final DockerClient dockerClient;

  public DockerManager() {
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    this.dockerClient = DockerClientBuilder.getInstance(config).build();
  }

  public record ContainerInfo(
      String containerId, String imageName, Map<Integer, Integer> portMappings, String status) {}

  public void pullImage(String imageName) throws InterruptedException {
    try {
      dockerClient.inspectImageCmd(imageName).exec();
      System.out.println("Image " + imageName + " already exists locally.");
    } catch (NotFoundException e) {
      System.out.println("Image " + imageName + " not found locally. Pulling...");
      dockerClient
          .pullImageCmd(imageName)
          .exec(new com.github.dockerjava.core.command.PullImageResultCallback())
          .awaitCompletion();
      System.out.println("Image " + imageName + " pulled successfully.");
    }
  }

  public ContainerInfo runContainer(
      String imageName, String containerName, Map<Integer, Integer> portMappings)
      throws InterruptedException {
    pullImage(imageName);

    List<PortBinding> portBindings = new ArrayList<>();
    List<ExposedPort> exposedPorts = new ArrayList<>();

    portMappings.forEach(
        (hostPort, containerPort) -> {
          ExposedPort exposedPort = ExposedPort.tcp(containerPort);
          exposedPorts.add(exposedPort);
          portBindings.add(PortBinding.parse(hostPort + ":" + containerPort));
        });

    CreateContainerResponse container =
        dockerClient
            .createContainerCmd(imageName)
            .withName(containerName)
            .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings))
            .withExposedPorts(exposedPorts)
            .exec();

    dockerClient.startContainerCmd(container.getId()).exec();

    InspectContainerResponse inspect = dockerClient.inspectContainerCmd(container.getId()).exec();
    String status = inspect.getState().getStatus();

    return new ContainerInfo(container.getId(), imageName, portMappings, status);
  }

  public ContainerInfo getContainerInfo(String containerId) {
    InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
    Map<Integer, Integer> portMappings = new java.util.HashMap<>();

    if (inspect.getNetworkSettings().getPorts() != null) {
      Ports ports = inspect.getNetworkSettings().getPorts();
      if (ports.getBindings() != null) {
        ports
            .getBindings()
            .forEach(
                (exposedPort, bindings) -> {
                  if (bindings != null && !(bindings.length == 0)) {
                    int containerPort = exposedPort.getPort();
                    int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());
                    portMappings.put(hostPort, containerPort);
                  }
                });
      }
    }

    return new ContainerInfo(
        containerId, inspect.getConfig().getImage(), portMappings, inspect.getState().getStatus());
  }

  public void stopAndRemoveContainer(String containerId) {
    try {
      dockerClient.stopContainerCmd(containerId).exec();
    } catch (Exception e) {
      System.out.println("Container " + containerId + " already stopped or not found.");
    }

    try {
      dockerClient.removeContainerCmd(containerId).exec();
    } catch (Exception e) {
      System.out.println("Failed to remove container " + containerId + ": " + e.getMessage());
    }
  }

  public void close() {
    try {
      dockerClient.close();
    } catch (Exception e) {
      System.out.println("Error closing Docker client: " + e.getMessage());
    }
  }

  public static void main(String[] args) throws InterruptedException {

    DockerManager dockerManager = new DockerManager();
    try {
      // Define port mappings (hostPort:containerPort)
      Map<Integer, Integer> ports = new HashMap<>();
      ports.put(8080, 80);

      // Run a container
      DockerManager.ContainerInfo info = dockerManager.runContainer(
          "nginx:latest",
          "my-nginx-container",
          ports
      );

      // Get container info
      System.out.println("Container ID: " + info.containerId());
      System.out.println("Image: " + info.imageName());
      System.out.println("Ports: " + info.portMappings());
      System.out.println("Status: " + info.status());

      // Get info for an existing container
      DockerManager.ContainerInfo existingInfo = dockerManager.getContainerInfo(info.containerId());
      System.out.println("Container status: " + existingInfo.status());

      // Stop and remove container
      dockerManager.stopAndRemoveContainer(info.containerId());
    } finally {
      dockerManager.close();
    }
  }
}
