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
  description = "Use host kubeconfig? (true/false)"
  default     = false
}

variable "namespace" {
  type        = string
  description = "The Kubernetes namespace to create workspaces in."
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
  config_path = var.use_kubeconfig == true ? "~/.kube/config" : null
}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}

resource "coder_agent" "main" {
  os             = "linux"
  arch           = "amd64"

  # Explicitly disable the default VS Code Desktop button
  display_apps {
    vscode          = false
    vscode_insiders = false
  }

  startup_script = <<-EOT
    set -e
    export DEBIAN_FRONTEND=noninteractive

    # 1. Install Ubuntu Desktop UI (XFCE) and TigerVNC
    if ! command -v vncserver &> /dev/null; then
      echo "Installing XFCE and TigerVNC..."
      apt-get update -y
      apt-get install -y xfce4 xfce4-goodies tigervnc-standalone-server dbus-x11
    fi

    # 2. Configure VNC server password (default: 'coder')
    mkdir -p ~/.vnc
    echo "coder" | vncpasswd -f > ~/.vnc/passwd
    chmod 600 ~/.vnc/passwd

    # 3. Create the X startup script for the desktop
    cat <<EOF > ~/.vnc/xstartup
    #!/bin/sh
    unset SESSION_MANAGER
    unset DBUS_SESSION_BUS_ADDRESS
    exec startxfce4
    EOF
    chmod +x ~/.vnc/xstartup

    # 4. Start VNC on port 5901 (Display :1)
    vncserver -kill :1 2>/dev/null || true
    vncserver :1 -geometry 1920x1080 -depth 24 -localhost no

    # 5. Install and Start Filebrowser
    if ! command -v filebrowser &> /dev/null; then
      echo "Installing Filebrowser..."
      curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
    fi
    
    # Pass the proxied base URL to Filebrowser so it loads static assets properly
    FB_BASE_URL="/@${data.coder_workspace_owner.me.name}/${data.coder_workspace.me.name}.main/apps/file-browser"
    filebrowser -r /home/coder -p 13339 -a 0.0.0.0 --noauth --baseurl "$FB_BASE_URL" >/tmp/filebrowser.log 2>&1 &
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
    display_name = "VNC Port Forwarding Command"
    key          = "8_vnc_command"
    script       = "echo 'coder port-forward ${data.coder_workspace.me.name} 5901:5901'"
    interval     = 86400 
    timeout      = 1
  }

  metadata {
    display_name = "VNC Password"
    key          = "9_vnc_password"
    script       = "echo 'coder'"
    interval     = 86400
    timeout      = 1
  }
}

# The Filebrowser App definition
resource "coder_app" "file-browser" {
  agent_id     = coder_agent.main.id
  slug         = "file-browser"
  display_name = "Files"
  icon         = "/icon/folder.svg"
  url          = "http://localhost:13339"
  subdomain    = false
  share        = "owner"

  healthcheck {
    url       = "http://localhost:13339/"
    interval  = 3
    threshold = 10
  }
}

resource "kubernetes_persistent_volume_claim_v1" "home" {
  metadata {
    name      = "coder-${data.coder_workspace.me.id}-home"
    namespace = var.namespace
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
  }

  spec {
    replicas = 1
    selector {
      match_labels = {
        "app.kubernetes.io/name" = "coder-workspace-${data.coder_workspace.me.id}"
      }
    }
    strategy {
      type = "Recreate"
    }

    template {
      metadata {
        labels = {
          "app.kubernetes.io/name" = "coder-workspace-${data.coder_workspace.me.id}"
        }
      }
      spec {
        container {
          name              = "dev"
          image             = "ubuntu:24.04"
          image_pull_policy = "Always"
          
          # Root CA setup so Coder can connect securely to your coder.xxx domain
          command           = ["sh", "-c", "apt-get update -y && apt-get install -y curl ca-certificates && update-ca-certificates && exec sh -c \"$CODER_INIT_SCRIPT\""]
          working_dir       = "/home/coder"
          
          env {
            name  = "CODER_INIT_SCRIPT"
            value = coder_agent.main.init_script
          }
          env {
            name  = "CODER_AGENT_TOKEN"
            value = coder_agent.main.token
          }
          env {
            name  = "HOME"
            value = "/home/coder"
          }
          
          resources {
            requests = {
              "cpu"    = "250m"
              "memory" = "512Mi"
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
            name       = "ca-cert"
            mount_path = "/usr/local/share/ca-certificates/coder-ca.crt"
            sub_path   = "ca.crt"
            read_only  = true
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
          name = "ca-cert"
          secret {
            secret_name = "coder-xxx-ca"
          }
        }
      }
    }
  }
}
