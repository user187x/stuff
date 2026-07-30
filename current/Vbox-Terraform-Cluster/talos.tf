locals {
  first_cp_ip = var.cp_ips[0]
  all_nodes   = concat(var.cp_ips, var.worker_ips)
}

resource "talos_machine_secrets" "this" {
  talos_version = var.talos_version
}

data "talos_machine_configuration" "cp" {
  cluster_name     = var.cluster_name
  cluster_endpoint = var.cluster_endpoint
  machine_type     = "controlplane"
  machine_secrets  = talos_machine_secrets.this.machine_secrets
  talos_version    = var.talos_version

  config_patches = [
    yamlencode({
      machine = {
        install = {
          disk = "/dev/sda"
          wipe = true
        }
        network = {
          interfaces = [
            {
              interface = "enp0s3"
              dhcp      = true
            }
          ]
        }
        # Explicitly instruct the Kubelet on the controlplane to bind to the host-only IP
        kubelet = {
          extraArgs = {
            "node-ip" = "192.168.56.110"
          }
        }
      }
    })
  ]
}

data "talos_machine_configuration" "worker" {
  cluster_name     = var.cluster_name
  cluster_endpoint = var.cluster_endpoint
  machine_type     = "worker"
  machine_secrets  = talos_machine_secrets.this.machine_secrets
  talos_version    = var.talos_version

  config_patches = [
    yamlencode({
      machine = {
        install = {
          disk = "/dev/sda"
          wipe = true
        }
        network = {
          interfaces = [
            {
              interface = "enp0s3"
              dhcp      = true
            }
          ]
        }
        # Instruct the kubelet on the workers to filter and pick up their host-only interface addresses
        kubelet = {
          extraArgs = {
            "node-ip" = "0.0.0.0" # Allows kubelet to listen on all interfaces but register logically via the default route or node selection rules
          }
        }
      }
    })
  ]
}

data "talos_client_configuration" "this" {
  cluster_name         = var.cluster_name
  client_configuration = talos_machine_secrets.this.client_configuration
  nodes                = local.all_nodes
  endpoints            = var.cp_ips
}

# Persist rendered machine configs to disk so we can apply them with
# talosctl. The provider's data source produces the YAML; we just save it.
# Mode 0600 because the file contains cluster secrets.
resource "local_sensitive_file" "cp_config" {
  content         = data.talos_machine_configuration.cp.machine_configuration
  filename        = "${path.module}/.talos/controlplane.yaml"
  file_permission = "0600"
}

resource "local_sensitive_file" "worker_config" {
  content         = data.talos_machine_configuration.worker.machine_configuration
  filename        = "${path.module}/.talos/worker.yaml"
  file_permission = "0600"
}

# We bypass the provider's talos_machine_configuration_apply resource.
#
# Why: on freshly-booted maintenance-mode nodes (the case here), the
# provider's apply reports success in ~0s without triggering install +
# reboot. The node stays in maintenance, and the subsequent
# talos_machine_bootstrap fails with:
#   "rpc error: code = Unimplemented desc = method Bootstrap not implemented"
# because Bootstrap isn't part of the maintenance API. The same shape
# of bug is tracked at siderolabs/terraform-provider-talos#265.
#
# The canonical fix per the Sidero docs and maintainers is to use
# `talosctl apply-config --insecure --mode reboot`, which always triggers
# the install + reboot cycle. After the reboot the node is in configured
# mode and the rest of the Talos provider lifecycle works.
resource "null_resource" "apply_cp" {
  for_each = toset(var.cp_ips)

  depends_on = [
    null_resource.wait_for_talos_api,
    local_sensitive_file.cp_config,
  ]

  triggers = {
    ip            = each.value
    config_sha256 = sha256(data.talos_machine_configuration.cp.machine_configuration)
  }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-BASH
      set -euo pipefail
      IP="${each.value}"
      CFG="${local_sensitive_file.cp_config.filename}"

      echo "[apply-cp] $IP: talosctl apply-config --insecure --mode reboot"
      talosctl apply-config --insecure --mode reboot \
        --nodes "$IP" --endpoints "$IP" \
        --file "$CFG"

      # The install manifests as a reboot: the API on port 50000 goes
      # down briefly, then comes back up in configured (non-maintenance)
      # mode. Bound the wait so a stuck install fails loud, not silent.
      echo "[apply-cp] $IP: waiting for reboot (API to go down)..."
      down=0
      for i in $(seq 1 60); do
        if ! timeout 2 bash -c "echo > /dev/tcp/$IP/50000" >/dev/null 2>&1; then
          echo "[apply-cp] $IP: API down after $((i*5))s -- install + reboot underway"
          down=1
          break
        fi
        sleep 5
      done
      if (( down == 0 )); then
        echo "[apply-cp] $IP: API never went down after apply -- install was not triggered" >&2
        exit 1
      fi

      echo "[apply-cp] $IP: waiting for node to come back..."
      for i in $(seq 1 120); do
        if timeout 2 bash -c "echo > /dev/tcp/$IP/50000" >/dev/null 2>&1; then
          echo "[apply-cp] $IP: back after $((i*5))s -- configured mode"
          exit 0
        fi
        sleep 5
      done
      echo "[apply-cp] $IP: timed out waiting for node to come back" >&2
      exit 1
    BASH
  }
}

resource "null_resource" "apply_worker" {
  for_each = toset(var.worker_ips)

  depends_on = [
    null_resource.wait_for_talos_api,
    local_sensitive_file.worker_config,
  ]

  triggers = {
    ip            = each.value
    config_sha256 = sha256(data.talos_machine_configuration.worker.machine_configuration)
  }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-BASH
      set -euo pipefail
      IP="${each.value}"
      CFG="${local_sensitive_file.worker_config.filename}"

      echo "[apply-worker] $IP: talosctl apply-config --insecure --mode reboot"
      talosctl apply-config --insecure --mode reboot \
        --nodes "$IP" --endpoints "$IP" \
        --file "$CFG"

      echo "[apply-worker] $IP: waiting for reboot (API to go down)..."
      down=0
      for i in $(seq 1 60); do
        if ! timeout 2 bash -c "echo > /dev/tcp/$IP/50000" >/dev/null 2>&1; then
          echo "[apply-worker] $IP: API down after $((i*5))s -- install + reboot underway"
          down=1
          break
        fi
        sleep 5
      done
      if (( down == 0 )); then
        echo "[apply-worker] $IP: API never went down after apply -- install was not triggered" >&2
        exit 1
      fi

      echo "[apply-worker] $IP: waiting for node to come back..."
      for i in $(seq 1 120); do
        if timeout 2 bash -c "echo > /dev/tcp/$IP/50000" >/dev/null 2>&1; then
          echo "[apply-worker] $IP: back after $((i*5))s -- configured mode"
          exit 0
        fi
        sleep 5
      done
      echo "[apply-worker] $IP: timed out waiting for node to come back" >&2
      exit 1
    BASH
  }
}

resource "talos_machine_bootstrap" "this" {
  # Bootstrap only after every CP is configured and rebooted. Bootstrap
  # needs the configured API (port 50000 with mTLS), not the maintenance
  # API (which doesn't implement it).
  depends_on = [null_resource.apply_cp]

  client_configuration = talos_machine_secrets.this.client_configuration
  node                 = local.first_cp_ip
  endpoint             = local.first_cp_ip
}

# Block until the cluster is actually Ready, not just until VMs are
# powered on. Without this, kubeconfig may come back before the
# Kubernetes API is reachable.
data "talos_cluster_health" "this" {
  depends_on = [
    talos_machine_bootstrap.this,
    null_resource.apply_worker,
  ]

  client_configuration = talos_machine_secrets.this.client_configuration
  control_plane_nodes  = var.cp_ips
  worker_nodes         = var.worker_ips
  endpoints            = var.cp_ips

  timeouts = { read = "15m" }
}

resource "talos_cluster_kubeconfig" "this" {
  depends_on = [data.talos_cluster_health.this]

  client_configuration = talos_machine_secrets.this.client_configuration
  node                 = local.first_cp_ip
  endpoint             = local.first_cp_ip
}

resource "local_sensitive_file" "kubeconfig" {
  content         = talos_cluster_kubeconfig.this.kubeconfig_raw
  filename        = "${path.module}/kubeconfig"
  file_permission = "0600"
}

resource "local_sensitive_file" "talosconfig" {
  content         = data.talos_client_configuration.this.talos_config
  filename        = "${path.module}/talosconfig"
  file_permission = "0600"
}

output "cp_ips" {
  value       = var.cp_ips
  description = "Control-plane node IPs."
}

output "worker_ips" {
  value       = var.worker_ips
  description = "Worker node IPs."
}

output "kubeconfig_path" {
  value       = abspath("${path.module}/kubeconfig")
  description = "Path to the generated kubeconfig file."
}

output "talosconfig_path" {
  value       = abspath("${path.module}/talosconfig")
  description = "Path to the generated talosconfig file."
}
