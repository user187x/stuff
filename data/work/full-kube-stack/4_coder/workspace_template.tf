terraform {
  required_providers {
    coder = {
      source = "coder/coder"
    }
    kubernetes = {
      source = "hashicorp/kubernetes"
    }
  }
}

provider "coder" {}

variable "use_kubeconfig" {
  type        = bool
  description = <<-EOF
  Use host kubeconfig? (true/false)

  Set this to false if the Coder host is itself running as a Pod on the same
  Kubernetes cluster as you are deploying workspaces to.

  Set this to true if the Coder host is running outside the Kubernetes cluster
  for workspaces.  A valid "~/.kube/config" must be present on the Coder host.
  EOF
  default     = false
}

variable "namespace" {
  type        = string
  description = "The Kubernetes namespace to create workspaces in (must exist prior to creating workspaces). If the Coder host is itself running as a Pod on the same Kubernetes cluster as you are deploying workspaces to, set this to the same namespace."
}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}

# Sub-path routing local variables for non-subdomain (path-based) app proxying.
locals {
  filebrowser_base_path = "/@${data.coder_workspace_owner.me.name}/${data.coder_workspace.me.name}.main/apps/filebrowser"

  # The path prefix Coder serves the noVNC app under (path-based proxy).
  novnc_base_path = "/@${data.coder_workspace_owner.me.name}/${data.coder_workspace.me.name}.main/apps/novnc"

  # noVNC builds its WebSocket URL as  ws(s)://<host>/<path>  — it prepends the
  # leading "/" itself, so this value MUST NOT start with a slash. It must point
  # at the FULL proxied path so the WebSocket reaches this workspace's agent
  # instead of the Coder root. This is the noVNC equivalent of filebrowser's
  # --baseurl.
  novnc_ws_path = "@${data.coder_workspace_owner.me.name}/${data.coder_workspace.me.name}.main/apps/novnc/websockify"
}

data "coder_parameter" "cpu" {
  name         = "cpu"
  display_name = "CPU"
  description  = "The number of CPU cores"
  default      = "2"
  icon         = "/icon/memory.svg"
  mutable      = true
  option {
    name  = "2 Cores"
    value = "2"
  }
  option {
    name  = "4 Cores"
    value = "4"
  }
  option {
    name  = "6 Cores"
    value = "6"
  }
  option {
    name  = "8 Cores"
    value = "8"
  }
}

data "coder_parameter" "memory" {
  name         = "memory"
  display_name = "Memory"
  description  = "The amount of memory in GB"
  default      = "2"
  icon         = "/icon/memory.svg"
  mutable      = true
  option {
    name  = "2 GB"
    value = "2"
  }
  option {
    name  = "4 GB"
    value = "4"
  }
  option {
    name  = "6 GB"
    value = "6"
  }
  option {
    name  = "8 GB"
    value = "8"
  }
}

data "coder_parameter" "gpu" {
  name         = "gpu"
  display_name = "GPUs"
  description  = "Select the number of NVIDIA GPUs to allocate"
  default      = "0"
  icon         = "/icon/memory.svg"
  mutable      = true
  option {
    name  = "None"
    value = "0"
  }
  option {
    name  = "1 Nvidia GPU"
    value = "1"
  }
}

data "coder_parameter" "home_disk_size" {
  name         = "home_disk_size"
  display_name = "Home disk size"
  description  = "The size of the home disk in GB"
  default      = "10"
  type         = "number"
  icon         = "/emojis/1f4be.png"
  mutable      = false
  validation {
    min = 1
    max = 99999
  }
}

provider "kubernetes" {
  # Authenticate via ~/.kube/config or a Coder-specific ServiceAccount, depending on admin preferences
  config_path = var.use_kubeconfig == true ? "~/.kube/config" : null
}

resource "coder_agent" "main" {
  os             = "linux"
  arch           = "amd64"
  startup_script = <<-EOT
    set -e

    # 1. Install & Start code-server
    curl -fsSL https://code-server.dev/install.sh | sh -s -- --method=standalone --prefix=/tmp/code-server
    /tmp/code-server/bin/code-server --auth none --port 13337 >/tmp/code-server.log 2>&1 &

    # 2. Install & Start Filebrowser (Database explicit + 12char password + BaseURL awareness)
    curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
    filebrowser config init --database=$HOME/filebrowser.db
    filebrowser config set --auth.method=noauth --port=8082 --address=0.0.0.0 --root=$HOME --database=$HOME/filebrowser.db --baseurl="${local.filebrowser_base_path}"
    filebrowser users add admin CoderAdminPassword123 --perm.admin=true --database=$HOME/filebrowser.db
    filebrowser --database=$HOME/filebrowser.db >/tmp/filebrowser.log 2>&1 &

    # 3. Setup VNC / noVNC Desktop Environment (themed XFCE, non-blocking install)
    (
      set +e
      echo "Starting background desktop environment setup..."

      # Guard on the desktop itself (not websockify) so a rebuild reinstalls XFCE.
      if ! command -v startxfce4 &> /dev/null; then
        sudo -n apt-get update
        sudo -n apt-get install -y \
          x11vnc xvfb novnc websockify dbus-x11 \
          xfce4 xfce4-terminal xfce4-goodies \
          arc-theme papirus-icon-theme fonts-noto
      fi

      # noVNC sub-path routing landing page. The CLIENT-SIDE meta redirect is what
      sudo -n mkdir -p /usr/share/novnc
      echo '<!DOCTYPE html><html><head><meta http-equiv="refresh" content="0; url=vnc.html?autoconnect=true&resize=scale&path=${local.novnc_ws_path}"></head></html>' | sudo -n tee /usr/share/novnc/index.html > /dev/null

      # Virtual display. Bigger framebuffer than the old default; resize=scale fits it.
      Xvfb :1 -screen 0 1280x800x24 > /dev/null 2>&1 &
      export DISPLAY=:1
      sleep 2

      # Full XFCE session needs its own dbus session bus.
      dbus-launch --exit-with-session startxfce4 > /tmp/xfce.log 2>&1 &
      sleep 4

      # Modern flat look: dark Arc theme + Papirus icons. Compositing OFF because
      # Xvfb is a software framebuffer and effects are laggy/wasteful over VNC.
      xfconf-query -c xsettings -p /Net/ThemeName           -s "Arc-Dark"     2>/dev/null
      xfconf-query -c xsettings -p /Net/IconThemeName       -s "Papirus-Dark" 2>/dev/null
      xfconf-query -c xfwm4     -p /general/theme           -s "Arc-Dark"     2>/dev/null
      xfconf-query -c xfwm4     -p /general/use_compositing -s false          2>/dev/null

      # x11vnc and websockify MUST agree on the RFB port. x11vnc defaults to 5900
      # and -display only selects the X display, not the VNC port, so pin both to 5900.
      x11vnc -display :1 -nopw -forever -shared -localhost -rfbport 5900 -o /tmp/x11vnc.log &
      websockify --web=/usr/share/novnc/ 0.0.0.0:6080 127.0.0.1:5900 > /tmp/websockify.log 2>&1 &

      echo "Desktop environment setup completed."
    ) >/tmp/desktop-setup.log 2>&1 &
  EOT

  metadata {
    display_name = "CPU Usage"
    key          = "0_cpu_usage"
    script       = "coder stat cpu"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "RAM Usage"
    key          = "1_ram_usage"
    script       = "coder stat mem"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "Home Disk"
    key          = "3_home_disk"
    script       = "coder stat disk --path $${HOME}"
    interval     = 60
    timeout      = 1
  }

  metadata {
    display_name = "CPU Usage (Host)"
    key          = "4_cpu_usage_host"
    script       = "coder stat cpu --host"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "Memory Usage (Host)"
    key          = "5_mem_usage_host"
    script       = "coder stat mem --host"
    interval     = 10
    timeout      = 1
  }

  metadata {
    display_name = "Load Average (Host)"
    key          = "6_load_host"
    script       = <<EOT
      echo "`cat /proc/loadavg | awk '{ print $1 }'` `nproc`" | awk '{ printf "%0.2f", $1/$2 }'
    EOT
    interval = 60
    timeout  = 1
  }
}

# code-server Application Button
resource "coder_app" "code-server" {
  agent_id     = coder_agent.main.id
  slug         = "code-server"
  display_name = "code-server"
  icon         = "/icon/code.svg"
  url          = "http://localhost:13337?folder=/home/coder"
  subdomain    = false
  share        = "owner"

  healthcheck {
    url       = "http://localhost:13337/healthz"
    interval  = 3
    threshold = 10
  }
}

# Filebrowser Application Button
resource "coder_app" "filebrowser" {
  agent_id     = coder_agent.main.id
  slug         = "filebrowser"
  display_name = "File Manager"
  icon         = "/icon/folder.svg"
  url          = "http://localhost:8082${local.filebrowser_base_path}"
  subdomain    = false
  share        = "owner"
}

# noVNC Application Button
resource "coder_app" "novnc" {
  agent_id     = coder_agent.main.id
  slug         = "novnc"
  display_name = "Desktop (noVNC)"
  icon         = "/icon/desktop.svg"
  url          = "http://127.0.0.1:6080"
  subdomain    = false
  share        = "owner"

  healthcheck {
    url       = "http://127.0.0.1:6080/vnc.html"
    interval  = 5
    threshold = 60
  }
}

resource "kubernetes_persistent_volume_claim_v1" "home" {
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
    annotations = {
      "com.coder.user.email" = data.coder_workspace_owner.me.email
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

resource "kubernetes_deployment_v1" "main" {
  count = data.coder_workspace.me.start_count
  depends_on = [
    kubernetes_persistent_volume_claim_v1.home
  ]
  wait_for_rollout = false
  metadata {
    name      = "coder-${data.coder_workspace.me.id}"
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
    annotations = {
      "com.coder.user.email" = data.coder_workspace_owner.me.email
    }
  }

  spec {
    replicas = 1
    selector {
      match_labels = {
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
    strategy {
      type = "Recreate"
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
        security_context {
          run_as_user     = 1000
          fs_group        = 1000
          run_as_non_root = true
        }

        # Main Developer Container
        container {
          name              = "dev"
          image             = "codercom/enterprise-base:ubuntu"
          image_pull_policy = "Always"
          command           = ["sh", "-c", coder_agent.main.init_script]
          security_context {
            run_as_user = "1000"
          }
          env {
            name  = "CODER_AGENT_TOKEN"
            value = coder_agent.main.token
          }
          env {
            name  = "DOCKER_HOST"
            value = "tcp://localhost:2375"
          }
          resources {
            requests = {
              "cpu"    = "250m"
              "memory" = "512Mi"
            }
            limits = {
              "cpu"    = "${data.coder_parameter.cpu.value}"
              "memory" = "${data.coder_parameter.memory.value}Gi"
              # Dinamically assign GPU if user selects it in the workspace form
              "nvidia.com/gpu" = data.coder_parameter.gpu.value != "0" ? data.coder_parameter.gpu.value : null
            }
          }
          volume_mount {
            mount_path = "/home/coder"
            name       = "home"
            read_only  = false
          }
        }

        # Docker-in-Docker Sidecar Container
        container {
          name  = "dind"
          image = "docker:27-dind"
          security_context {
            privileged      = true
            run_as_user     = 0
            run_as_non_root = false
          }
          env {
            name  = "DOCKER_TLS_CERTDIR"
            value = ""
          }
          volume_mount {
            mount_path = "/var/lib/docker"
            name       = "docker-graph-storage"
          }
        }

        volume {
          name = "home"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim_v1.home.metadata.0.name
            read_only  = false
          }
        }

        volume {
          name = "docker-graph-storage"
          empty_dir {}
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
