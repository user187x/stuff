###############################################################################
# Coder Workspace Template — Rootless Kubernetes workspace with minikube
#
# - Workspace container runs as UID 1000, no capabilities, no privilege
#   escalation (no_new_privs) => sudo/su are impossible, by kernel guarantee.
# - kubectl, k9s, and minikube are installed into ~/.local/bin (user-owned).
# - A Docker-in-Docker sidecar provides the daemon minikube's docker driver
#   needs. The user has no shell access to the sidecar.
# - One app button, "minikube", opens a terminal with the kube context
#   already pointed at the minikube cluster and k9s on PATH.
###############################################################################

terraform {
  required_providers {
    coder = {
      source  = "coder/coder"
      version = "~> 2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
  }
}

###############################################################################
# Template-level variables (set by the template admin at import time)
###############################################################################

variable "namespace" {
  type        = string
  description = "Kubernetes namespace to provision workspaces into."
}

variable "use_kubeconfig" {
  type        = bool
  default     = false
  description = "Use host kubeconfig (true) or in-cluster service account (false)."
}

variable "workspace_image" {
  type        = string
  default     = "codercom/enterprise-base:ubuntu"
  description = "Workspace image. Must have curl/bash for the Coder agent. Must NOT require root."
}

variable "dind_image" {
  type        = string
  default     = "docker:27-dind"
  description = "Docker-in-Docker sidecar image used as minikube's container runtime."
}

variable "kubectl_version" {
  type    = string
  default = "v1.31.1"
}

variable "k9s_version" {
  type    = string
  default = "v0.32.5"
}

variable "minikube_version" {
  type    = string
  default = "v1.34.0"
}

###############################################################################
# Providers
###############################################################################

provider "kubernetes" {
  config_path = var.use_kubeconfig ? "~/.kube/config" : null
}

provider "coder" {}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}

###############################################################################
# User-facing parameters
###############################################################################

data "coder_parameter" "cpu" {
  name         = "cpu"
  display_name = "CPU"
  description  = "CPU cores for the workspace container."
  type         = "number"
  default      = "4"
  mutable      = true
  validation {
    min = 2
    max = 16
  }
}

data "coder_parameter" "memory" {
  name         = "memory"
  display_name = "Memory (GiB)"
  description  = "Memory for the workspace container."
  type         = "number"
  default      = "8"
  mutable      = true
  validation {
    min = 4
    max = 64
  }
}

data "coder_parameter" "home_disk_size" {
  name         = "home_disk_size"
  display_name = "Home disk size (GiB)"
  description  = "Persistent /home/coder volume."
  type         = "number"
  default      = "20"
  mutable      = false
  validation {
    min = 10
    max = 200
  }
}

data "coder_parameter" "minikube_cpus" {
  name         = "minikube_cpus"
  display_name = "minikube CPUs"
  type         = "number"
  default      = "2"
  mutable      = true
  validation {
    min = 2
    max = 8
  }
}

data "coder_parameter" "minikube_memory" {
  name         = "minikube_memory"
  display_name = "minikube memory (MiB)"
  type         = "number"
  default      = "4096"
  mutable      = true
  validation {
    min = 2048
    max = 32768
  }
}

###############################################################################
# Coder agent
###############################################################################

resource "coder_agent" "main" {
  os   = "linux"
  arch = "amd64"
  dir  = "/home/coder"

  env = {
    DOCKER_HOST      = "tcp://127.0.0.1:2375"
    KUBECONFIG       = "/home/coder/.kube/config"
    MINIKUBE_HOME    = "/home/coder/.minikube"
    MINIKUBE_IN_STYLE = "false"
    PATH             = "/home/coder/.local/bin:/usr/local/bin:/usr/bin:/bin"
  }

  startup_script_behavior = "blocking"

  startup_script = <<-EOT
    #!/usr/bin/env bash
    set -euo pipefail

    KUBECTL_VERSION="${var.kubectl_version}"
    K9S_VERSION="${var.k9s_version}"
    MINIKUBE_VERSION="${var.minikube_version}"

    BIN="$HOME/.local/bin"
    mkdir -p "$BIN" "$HOME/.kube" "$HOME/.minikube"
    export PATH="$BIN:$PATH"

    # ---------------------------------------------------------------------
    # Install tooling into user space. No root required.
    # ---------------------------------------------------------------------
    if [ ! -x "$BIN/kubectl" ] || [ "$(cat "$BIN/.kubectl_version" 2>/dev/null)" != "$KUBECTL_VERSION" ]; then
      echo "Installing kubectl $KUBECTL_VERSION..."
      curl -fsSL "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/amd64/kubectl" -o "$BIN/kubectl"
      chmod 0755 "$BIN/kubectl"
      echo "$KUBECTL_VERSION" > "$BIN/.kubectl_version"
    fi

    if [ ! -x "$BIN/k9s" ] || [ "$(cat "$BIN/.k9s_version" 2>/dev/null)" != "$K9S_VERSION" ]; then
      echo "Installing k9s $K9S_VERSION..."
      curl -fsSL "https://github.com/derailed/k9s/releases/download/$K9S_VERSION/k9s_Linux_amd64.tar.gz" \
        | tar -xz -C "$BIN" k9s
      chmod 0755 "$BIN/k9s"
      echo "$K9S_VERSION" > "$BIN/.k9s_version"
    fi

    if [ ! -x "$BIN/minikube" ] || [ "$(cat "$BIN/.minikube_version" 2>/dev/null)" != "$MINIKUBE_VERSION" ]; then
      echo "Installing minikube $MINIKUBE_VERSION..."
      curl -fsSL "https://github.com/kubernetes/minikube/releases/download/$MINIKUBE_VERSION/minikube-linux-amd64" \
        -o "$BIN/minikube"
      chmod 0755 "$BIN/minikube"
      echo "$MINIKUBE_VERSION" > "$BIN/.minikube_version"
    fi

    # ---------------------------------------------------------------------
    # Shell environment for interactive sessions (idempotent)
    # ---------------------------------------------------------------------
    if ! grep -q "# coder-minikube-env" "$HOME/.bashrc" 2>/dev/null; then
      cat >> "$HOME/.bashrc" <<'RC'
    # coder-minikube-env
    export PATH="$HOME/.local/bin:$PATH"
    export DOCKER_HOST="tcp://127.0.0.1:2375"
    export KUBECONFIG="$HOME/.kube/config"
    export MINIKUBE_HOME="$HOME/.minikube"
    export MINIKUBE_IN_STYLE=false
    source <(kubectl completion bash 2>/dev/null) || true
    alias k=kubectl
    complete -o default -F __start_kubectl k 2>/dev/null || true
    RC
    fi

    # ---------------------------------------------------------------------
    # Launcher used by the "minikube" app button
    # ---------------------------------------------------------------------
    cat > "$BIN/minikube-shell" <<'SH'
    #!/usr/bin/env bash
    export PATH="$HOME/.local/bin:$PATH"
    export DOCKER_HOST="tcp://127.0.0.1:2375"
    export KUBECONFIG="$HOME/.kube/config"
    export MINIKUBE_HOME="$HOME/.minikube"
    export MINIKUBE_IN_STYLE=false

    if ! minikube status --profile minikube >/dev/null 2>&1; then
      echo "minikube is not running — starting it (this can take a minute)..."
      minikube start --profile minikube --driver=docker --force 2>&1 | tail -n 5
    fi
    kubectl config use-context minikube >/dev/null 2>&1 || true

    echo "=================================================================="
    echo "  minikube terminal"
    echo "  context : $(kubectl config current-context 2>/dev/null || echo 'n/a')"
    echo "  kubectl : $(kubectl version --client -o json 2>/dev/null | sed -n 's/.*"gitVersion": *"\([^"]*\)".*/\1/p' | head -n1)"
    echo "  k9s     : $(k9s version -s 2>/dev/null | awk '/Version/{print $2}')"
    echo "  tip     : run 'k9s' for the TUI, 'kubectl get nodes' to verify"
    echo "=================================================================="
    exec bash -l
    SH
    chmod 0755 "$BIN/minikube-shell"

    # ---------------------------------------------------------------------
    # Wait for the dind sidecar, then bring up minikube
    # ---------------------------------------------------------------------
    echo "Waiting for Docker daemon sidecar..."
    for i in $(seq 1 60); do
      if curl -fsS "http://127.0.0.1:2375/_ping" >/dev/null 2>&1; then
        echo "Docker daemon ready."
        break
      fi
      sleep 2
      if [ "$i" -eq 60 ]; then
        echo "ERROR: Docker daemon did not become ready." >&2
        exit 1
      fi
    done

    echo "Starting minikube..."
    minikube start \
      --profile minikube \
      --driver=docker \
      --cpus=${data.coder_parameter.minikube_cpus.value} \
      --memory=${data.coder_parameter.minikube_memory.value} \
      --kubernetes-version="$KUBECTL_VERSION" \
      --force

    kubectl config use-context minikube
    kubectl wait --for=condition=Ready nodes --all --timeout=180s
    echo "minikube is ready. Context set to 'minikube'."
  EOT

  metadata {
    display_name = "CPU"
    key          = "cpu"
    script       = "coder stat cpu"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "Memory"
    key          = "mem"
    script       = "coder stat mem"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "Home disk"
    key          = "home_disk"
    script       = "coder stat disk --path /home/coder"
    interval     = 60
    timeout      = 1
  }

  metadata {
    display_name = "minikube"
    key          = "minikube"
    script       = "DOCKER_HOST=tcp://127.0.0.1:2375 MINIKUBE_HOME=$HOME/.minikube $HOME/.local/bin/minikube status -f '{{.Host}}/{{.APIServer}}' 2>/dev/null || echo 'stopped'"
    interval     = 30
    timeout      = 10
  }
}

###############################################################################
# The single user-facing app button: "minikube"
###############################################################################

resource "coder_app" "minikube" {
  agent_id     = coder_agent.main.id
  slug         = "minikube"
  display_name = "minikube"
  icon         = "/icon/k8s.png"
  command      = "/home/coder/.local/bin/minikube-shell"
  share        = "owner"
  order        = 1
}

###############################################################################
# Persistent home volume
###############################################################################

resource "kubernetes_persistent_volume_claim" "home" {
  metadata {
    name      = "coder-${data.coder_workspace.me.id}-home"
    namespace = var.namespace
    labels = {
      "app.kubernetes.io/name"     = "coder-pvc"
      "app.kubernetes.io/instance" = "coder-pvc-${data.coder_workspace.me.id}"
      "app.kubernetes.io/part-of"  = "coder"
      "com.coder.resource"         = "true"
      "com.coder.workspace.id"     = data.coder_workspace.me.id
      "com.coder.workspace.name"   = data.coder_workspace.me.name
      "com.coder.user.id"          = data.coder_workspace_owner.me.id
      "com.coder.user.username"    = data.coder_workspace_owner.me.name
    }
  }
  wait_until_bound = false
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = {
        storage = "${data.coder_parameter.home_disk_size.value}Gi"
      }
    }
  }
}

###############################################################################
# Workspace deployment
###############################################################################

resource "kubernetes_deployment" "main" {
  count = data.coder_workspace.me.start_count
  depends_on = [kubernetes_persistent_volume_claim.home]

  wait_for_rollout = false

  metadata {
    name      = "coder-${data.coder_workspace_owner.me.name}-${data.coder_workspace.me.name}"
    namespace = var.namespace
    labels = {
      "app.kubernetes.io/name"     = "coder-workspace"
      "app.kubernetes.io/instance" = "coder-workspace-${data.coder_workspace.me.id}"
      "app.kubernetes.io/part-of"  = "coder"
      "com.coder.resource"         = "true"
      "com.coder.workspace.id"     = data.coder_workspace.me.id
      "com.coder.workspace.name"   = data.coder_workspace.me.name
      "com.coder.user.id"          = data.coder_workspace_owner.me.id
      "com.coder.user.username"    = data.coder_workspace_owner.me.name
    }
  }

  spec {
    replicas = 1
    strategy {
      type = "Recreate"
    }
    selector {
      match_labels = {
        "app.kubernetes.io/instance" = "coder-workspace-${data.coder_workspace.me.id}"
      }
    }

    template {
      metadata {
        labels = {
          "app.kubernetes.io/name"     = "coder-workspace"
          "app.kubernetes.io/instance" = "coder-workspace-${data.coder_workspace.me.id}"
          "app.kubernetes.io/part-of"  = "coder"
          "com.coder.resource"         = "true"
          "com.coder.workspace.id"     = data.coder_workspace.me.id
          "com.coder.workspace.name"   = data.coder_workspace.me.name
          "com.coder.user.id"          = data.coder_workspace_owner.me.id
          "com.coder.user.username"    = data.coder_workspace_owner.me.name
        }
      }

      spec {
        # Pod-level: the user's container runs as UID/GID 1000 and the
        # home PVC is chowned to that group via fsGroup.
        security_context {
          run_as_user     = 1000
          run_as_group    = 1000
          fs_group        = 1000
          run_as_non_root = true
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        automount_service_account_token = false
        enable_service_links            = false

        # ------------------------------------------------------------
        # USER WORKSPACE CONTAINER — rootless, no caps, no escalation.
        # ------------------------------------------------------------
        container {
          name              = "dev"
          image             = var.workspace_image
          image_pull_policy = "Always"
          command           = ["sh", "-c", coder_agent.main.init_script]

          security_context {
            run_as_user                = 1000
            run_as_group               = 1000
            run_as_non_root            = true
            privileged                 = false
            # no_new_privs: setuid binaries (sudo, su, pkexec) cannot
            # acquire privilege. This is the kernel-level sudo block.
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          env {
            name  = "CODER_AGENT_TOKEN"
            value = coder_agent.main.token
          }
          env {
            name  = "DOCKER_HOST"
            value = "tcp://127.0.0.1:2375"
          }
          env {
            name  = "KUBECONFIG"
            value = "/home/coder/.kube/config"
          }
          env {
            name  = "MINIKUBE_HOME"
            value = "/home/coder/.minikube"
          }

          resources {
            requests = {
              "cpu"    = "${data.coder_parameter.cpu.value}"
              "memory" = "${data.coder_parameter.memory.value}Gi"
            }
            limits = {
              "cpu"    = "${data.coder_parameter.cpu.value}"
              "memory" = "${data.coder_parameter.memory.value}Gi"
            }
          }

          volume_mount {
            mount_path = "/home/coder"
            name       = "home"
            read_only  = false
          }
          volume_mount {
            mount_path = "/tmp"
            name       = "tmp"
          }
        }

        # ------------------------------------------------------------
        # DOCKER-IN-DOCKER SIDECAR — minikube's container runtime.
        # Privileged by necessity (it runs a kernel-level container
        # runtime). The user has no shell into this container and no
        # route to it except the Docker API on localhost.
        # ------------------------------------------------------------
        container {
          name              = "dind"
          image             = var.dind_image
          image_pull_policy = "IfNotPresent"

          args = [
            "dockerd",
            "--host=tcp://127.0.0.1:2375",
            "--tls=false",
            "--storage-driver=overlay2",
          ]

          env {
            name  = "DOCKER_TLS_CERTDIR"
            value = ""
          }

          security_context {
            run_as_user  = 0
            run_as_group = 0
            privileged   = true
          }

          resources {
            requests = {
              "cpu"    = "${data.coder_parameter.minikube_cpus.value}"
              "memory" = "${data.coder_parameter.minikube_memory.value}Mi"
            }
            limits = {
              "cpu"    = "${data.coder_parameter.minikube_cpus.value + 1}"
              "memory" = "${data.coder_parameter.minikube_memory.value + 1024}Mi"
            }
          }

          liveness_probe {
            exec {
              command = ["docker", "-H", "tcp://127.0.0.1:2375", "info"]
            }
            initial_delay_seconds = 20
            period_seconds        = 30
            timeout_seconds       = 10
            failure_threshold     = 3
          }

          volume_mount {
            mount_path = "/var/lib/docker"
            name       = "docker-graph"
          }
        }

        volume {
          name = "home"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.home.metadata[0].name
            read_only  = false
          }
        }

        volume {
          name = "tmp"
          empty_dir {
            size_limit = "2Gi"
          }
        }

        # Ephemeral: minikube images/containers are rebuilt on restart.
        # Swap for a PVC if you want the cluster to survive workspace stops.
        volume {
          name = "docker-graph"
          empty_dir {
            size_limit = "40Gi"
          }
        }

        affinity {
          pod_anti_affinity {
            preferred_during_scheduling_ignored_during_execution {
              weight = 1
              pod_affinity_term {
                topology_key = "kubernetes.io/hostname"
                label_selector {
                  match_expressions {
                    key      = "app.kubernetes.io/name"
                    operator = "In"
                    values   = ["coder-workspace"]
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

###############################################################################
# Workspace metadata shown in the Coder UI
###############################################################################

resource "coder_metadata" "workspace_info" {
  count       = data.coder_workspace.me.start_count
  resource_id = kubernetes_deployment.main[0].id

  item {
    key   = "namespace"
    value = var.namespace
  }
  item {
    key   = "run_as_user"
    value = "1000 (no sudo, no capabilities, no_new_privs)"
  }
  item {
    key   = "kubectl"
    value = var.kubectl_version
  }
  item {
    key   = "k9s"
    value = var.k9s_version
  }
  item {
    key   = "minikube"
    value = "${var.minikube_version} (docker driver via dind sidecar)"
  }
}
