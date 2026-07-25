terraform {
  required_version = ">= 1.9"

  required_providers {
    coder = {
      source  = "coder/coder"
      version = ">= 2.5"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = ">= 2.30"
    }
  }
}

provider "coder" {}

# ---------------------------------------------------------------------------
# Admin-set variables (prompted once when you push the template)
# ---------------------------------------------------------------------------

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
  description = "The Kubernetes namespace to create workspaces in (must exist prior to creating workspaces)."
}

variable "workspace_image" {
  type        = string
  description = <<-EOF
  Workspace image. The default upstream image does NOT contain a desktop
  environment, VNC, noVNC or the Docker CLI, so the Desktop button will fall
  back to a slow apt-get install on every start. Build the bundled Dockerfile
  and point this at your own registry instead.
  EOF
  default     = "codercom/enterprise-base:ubuntu"
}

variable "dind_image" {
  type        = string
  description = "Image for the Docker-in-Docker sidecar."
  default     = "docker:28-dind"
}

variable "allow_privileged_docker" {
  type        = bool
  description = <<-EOF
  Allow the privileged Docker-in-Docker sidecar. Set to false if your cluster
  enforces the "restricted" Pod Security Standard, or if you run Sysbox /
  rootless Docker instead (see README).
  EOF
  default     = true
}

variable "use_subdomain_apps" {
  type        = bool
  description = <<-EOF
  Serve web apps on their own subdomain instead of a path under the Coder URL.
  Strongly recommended, but requires a wildcard access URL + wildcard TLS cert
  on the Coder deployment. When false, apps are served under
  /@user/workspace.main/apps/<slug>/ and this template configures each app's
  base path accordingly.
  EOF
  default     = false
}

variable "gpu_node_selector" {
  type        = map(string)
  description = "Node selector applied when the user requests a GPU."
  default     = {}
}

variable "gpu_tolerations" {
  type = list(object({
    key      = string
    operator = string
    value    = optional(string)
    effect   = string
  }))
  description = "Tolerations applied when the user requests a GPU."
  default     = []
}

# ---------------------------------------------------------------------------
# User-set parameters
# ---------------------------------------------------------------------------

data "coder_parameter" "cpu" {
  name         = "cpu"
  display_name = "CPU"
  description  = "The number of CPU cores"
  default      = "4"
  icon         = "/icon/memory.svg"
  mutable      = true
  order        = 1
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
  default      = "8"
  icon         = "/icon/memory.svg"
  mutable      = true
  order        = 2
  option {
    name  = "4 GB"
    value = "4"
  }
  option {
    name  = "8 GB"
    value = "8"
  }
  option {
    name  = "16 GB"
    value = "16"
  }
  option {
    name  = "32 GB"
    value = "32"
  }
}

data "coder_parameter" "home_disk_size" {
  name         = "home_disk_size"
  display_name = "Home disk size"
  description  = "The size of the home disk in GB"
  default      = "20"
  type         = "number"
  icon         = "/emojis/1f4be.png"
  mutable      = false
  order        = 3
  validation {
    min = 1
    max = 99999
  }
}

data "coder_parameter" "enable_gpu" {
  name         = "enable_gpu"
  display_name = "Request a GPU"
  description  = "Schedule this workspace onto a GPU node (nvidia.com/gpu=1)."
  type         = "bool"
  default      = "false"
  icon         = "/emojis/1f3ae.png"
  mutable      = false
  order        = 4
}

data "coder_parameter" "enable_docker" {
  name         = "enable_docker"
  display_name = "Docker inside the workspace"
  description  = <<-EOD
  Run a Docker engine alongside your workspace. Gives you a working `docker`,
  `docker compose`, `docker buildx`, Testcontainers and Dev Containers, with a
  persistent image cache.
  EOD
  type         = "bool"
  default      = "true"
  icon         = "/icon/docker.svg"
  mutable      = false
  order        = 10
}

data "coder_parameter" "docker_disk_size" {
  name         = "docker_disk_size"
  display_name = "Docker image cache size"
  description  = "Persistent disk for /var/lib/docker in GB. Your pulled images and build cache survive workspace restarts."
  type         = "number"
  default      = "20"
  icon         = "/icon/docker.svg"
  mutable      = false
  order        = 11
  validation {
    min = 5
    max = 1000
  }
}

data "coder_parameter" "desktop_resolution" {
  name         = "desktop_resolution"
  display_name = "Desktop resolution"
  description  = "Resolution of the noVNC desktop session."
  default      = "1920x1080"
  icon         = "/icon/desktop.svg"
  mutable      = true
  order        = 20
  option {
    name  = "1280x800"
    value = "1280x800"
  }
  option {
    name  = "1600x900"
    value = "1600x900"
  }
  option {
    name  = "1920x1080"
    value = "1920x1080"
  }
  option {
    name  = "2560x1440"
    value = "2560x1440"
  }
}

data "coder_parameter" "repo_url" {
  name         = "repo_url"
  display_name = "Git repository"
  description  = "Optional: clone this repository into the workspace on first start."
  default      = ""
  type         = "string"
  icon         = "/icon/git.svg"
  mutable      = true
  order        = 30
}

data "coder_parameter" "dotfiles_uri" {
  name         = "dotfiles_uri"
  display_name = "Dotfiles repository"
  description  = "Optional: a dotfiles repo to apply on start (see github.com/coder/dotfiles)."
  default      = ""
  type         = "string"
  icon         = "/icon/dotfiles.svg"
  mutable      = true
  order        = 31
}

# Ephemeral parameters reset to their default after every build, which makes
# them perfect for one-shot switches.
data "coder_parameter" "reset_desktop" {
  name         = "reset_desktop"
  display_name = "Reset desktop session"
  description  = "Wipe ~/.vnc and ~/.config/xfce4 on this start. Use if the desktop is broken."
  type         = "bool"
  default      = "false"
  mutable      = true
  ephemeral    = true
  order        = 40
}

# ---------------------------------------------------------------------------
# Providers / data
# ---------------------------------------------------------------------------

provider "kubernetes" {
  config_path = var.use_kubeconfig == true ? "~/.kube/config" : null
}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}
data "coder_provisioner" "me" {}

locals {
  # Ports are bound to 127.0.0.1 only. Coder's agent tunnels them, so nothing
  # is reachable from the pod network or the cluster.
  novnc_port       = 6080
  vnc_display      = 1
  vnc_port         = 5900 + local.vnc_display
  filebrowser_port = 13339
  code_server_port = 13337
  docker_port      = 2375

  # When apps are proxied on a path rather than a subdomain, Coder serves them
  # under this prefix. Apps that build absolute URLs (filebrowser, code-server,
  # VS Code Web) MUST be told about it or they 404 on their own assets - this is
  # the single most common cause of "the button just spins forever".
  app_path_prefix       = "/@${data.coder_workspace_owner.me.name}/${data.coder_workspace.me.name}.main/apps"
  filebrowser_base_path = var.use_subdomain_apps ? "" : "${local.app_path_prefix}/filebrowser"

  git_name = data.coder_workspace_owner.me.full_name != "" ? data.coder_workspace_owner.me.full_name : data.coder_workspace_owner.me.name

  docker_enabled = tobool(data.coder_parameter.enable_docker.value) && var.allow_privileged_docker
  gpu_enabled    = tobool(data.coder_parameter.enable_gpu.value)

  # Requests are deliberately a fraction of limits so nodes can be packed;
  # raise the ratio if you would rather guarantee capacity.
  cpu_request    = "${ceil(tonumber(data.coder_parameter.cpu.value) * 1000 * 0.25)}m"
  memory_request = "${ceil(tonumber(data.coder_parameter.memory.value) * 1024 * 0.5)}Mi"

  container_limits = merge(
    {
      "cpu"    = data.coder_parameter.cpu.value
      "memory" = "${data.coder_parameter.memory.value}Gi"
    },
    local.gpu_enabled ? { "nvidia.com/gpu" = "1" } : {},
  )

  container_requests = merge(
    {
      "cpu"    = local.cpu_request
      "memory" = local.memory_request
    },
    local.gpu_enabled ? { "nvidia.com/gpu" = "1" } : {},
  )

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

# ---------------------------------------------------------------------------
# Agent
# ---------------------------------------------------------------------------

resource "coder_agent" "main" {
  os   = "linux"
  arch = data.coder_provisioner.me.arch
  dir  = "/home/coder"

  display_apps {
    vscode                 = false
    vscode_insiders        = false
    ssh_helper             = true
    port_forwarding_helper = true
    web_terminal           = true
  }

  # Environment available to every shell, SSH session and IDE in the workspace.
  env = {
    GIT_AUTHOR_NAME     = local.git_name
    GIT_AUTHOR_EMAIL    = data.coder_workspace_owner.me.email
    GIT_COMMITTER_NAME  = local.git_name
    GIT_COMMITTER_EMAIL = data.coder_workspace_owner.me.email
    DISPLAY             = ":${local.vnc_display}"
    DOCKER_BUILDKIT     = "1"
    COMPOSE_BAKE        = "true"
  }

  # The old template did everything in one startup_script with `set -e` and
  # ended with `sleep infinity`. Both are bugs: one failing command silently
  # skipped everything after it, and blocking forever meant the workspace never
  # reported "ready". Work is now split into coder_script resources, each with
  # its own log stream and status in the UI.
  startup_script          = "echo 'Startup handled by coder_script resources - see the workspace build log.'"
  startup_script_behavior = "non-blocking"
  shutdown_script         = <<-EOT
    # Give containers and the X session a chance to flush state on stop.
    if command -v docker >/dev/null 2>&1 && [ -n "$${DOCKER_HOST:-}" ]; then
      timeout 30 docker ps -q 2>/dev/null | xargs -r timeout 30 docker stop -t 5 || true
    fi
    vncserver -kill :${local.vnc_display} >/dev/null 2>&1 || true
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
    script       = <<-EOT
      echo "`cat /proc/loadavg | awk '{ print $1 }'` `nproc`" | awk '{ printf "%0.2f", $1/$2 }'
    EOT
    interval     = 60
    timeout      = 1
  }

  # Live count of running containers, straight on the workspace page.
  dynamic "metadata" {
    for_each = local.docker_enabled ? [1] : []
    content {
      display_name = "Containers"
      key          = "7_containers"
      script       = "docker ps -q 2>/dev/null | wc -l | tr -d ' '"
      interval     = 20
      timeout      = 5
    }
  }

  dynamic "metadata" {
    for_each = local.docker_enabled ? [1] : []
    content {
      display_name = "Docker Disk"
      key          = "8_docker_disk"
      script       = "coder stat disk --path /var/lib/docker 2>/dev/null || echo n/a"
      interval     = 120
      timeout      = 5
    }
  }

  dynamic "metadata" {
    for_each = local.gpu_enabled ? [1] : []
    content {
      display_name = "GPU"
      key          = "9_gpu"
      script       = "nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total --format=csv,noheader 2>/dev/null || echo n/a"
      interval     = 15
      timeout      = 5
    }
  }
}

# ---------------------------------------------------------------------------
# Scripts
# ---------------------------------------------------------------------------

# 1. Make the workspace feel like yours, and make tool installs survive restarts
#    by keeping caches on the home volume.
resource "coder_script" "bootstrap" {
  agent_id           = coder_agent.main.id
  display_name       = "Workspace bootstrap"
  icon               = "/emojis/1f680.png"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail

    mkdir -p "$HOME/.local/bin" "$HOME/.cache" "$HOME/.config" "$HOME/.local/share"

    # Anything on the home PVC persists; put caches there so restarts stay fast.
    if ! grep -q 'coder-workspace-bootstrap' "$HOME/.profile" 2>/dev/null; then
      cat >> "$HOME/.profile" <<'PROFILE'

    # --- coder-workspace-bootstrap ---
    export PATH="$HOME/.local/bin:$PATH"
    export XDG_CACHE_HOME="$HOME/.cache"
    export XDG_CONFIG_HOME="$HOME/.config"
    export XDG_DATA_HOME="$HOME/.local/share"
    export PNPM_HOME="$HOME/.local/share/pnpm"
    export PATH="$PNPM_HOME:$PATH"
    export GOPATH="$HOME/.cache/go"
    export GOMODCACHE="$HOME/.cache/go/pkg/mod"
    export CARGO_HOME="$HOME/.cache/cargo"
    export PIP_CACHE_DIR="$HOME/.cache/pip"
    export npm_config_cache="$HOME/.cache/npm"
    # --- end coder-workspace-bootstrap ---
    PROFILE
    fi

    # Sensible git defaults, only if the user has not set their own.
    git config --global --get init.defaultBranch >/dev/null 2>&1 || git config --global init.defaultBranch main
    git config --global --get pull.rebase >/dev/null 2>&1 || git config --global pull.rebase true
    git config --global --get push.autoSetupRemote >/dev/null 2>&1 || git config --global push.autoSetupRemote true

    echo "Bootstrap complete."
  EOT
}

# 2. noVNC desktop. The original template started websockify but never started
#    an X server or a window manager, and pointed at port 5900 while TigerVNC
#    listens on 5900+display. All three are fixed here.
resource "coder_script" "novnc" {
  agent_id           = coder_agent.main.id
  display_name       = "Desktop (noVNC)"
  icon               = "/icon/desktop.svg"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail

    GEOMETRY="${data.coder_parameter.desktop_resolution.value}"
    DISPLAY_NUM="${local.vnc_display}"
    VNC_PORT="${local.vnc_port}"
    NOVNC_PORT="${local.novnc_port}"
    RESET="${data.coder_parameter.reset_desktop.value}"

    if [ "$RESET" = "true" ]; then
      echo "Resetting desktop session state..."
      rm -rf "$HOME/.vnc" "$HOME/.config/xfce4" "$HOME/.cache/sessions"
    fi

    # The base image has no desktop. Installing at runtime works but adds
    # minutes to every start - bake the bundled Dockerfile instead.
    if ! command -v vncserver >/dev/null 2>&1 || ! command -v websockify >/dev/null 2>&1; then
      echo "Desktop packages missing from the image; installing them now (this is slow)."
      sudo apt-get update -qq
      sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
        tigervnc-standalone-server tigervnc-common novnc websockify \
        xfce4 xfce4-terminal xfce4-goodies dbus-x11 x11-xserver-utils fonts-dejavu || {
          echo "Install failed - the Desktop button will not work. Use a prebuilt image."
          exit 1
        }
    fi

    mkdir -p "$HOME/.vnc"

    # TigerVNC refuses to start without an xstartup that execs a session.
    cat > "$HOME/.vnc/xstartup" <<'XSTARTUP'
    #!/bin/sh
    unset SESSION_MANAGER
    unset DBUS_SESSION_BUS_ADDRESS
    export XDG_CURRENT_DESKTOP=XFCE
    xsetroot -solid "#2e3440" 2>/dev/null || true
    # dbus-launch is required or xfce4-session hangs on a grey screen.
    exec dbus-launch --exit-with-session xfce4-session
    XSTARTUP
    chmod +x "$HOME/.vnc/xstartup"

    cat > "$HOME/.vnc/config" <<CONFIG
    geometry=$GEOMETRY
    depth=24
    localhost=yes
    alwaysshared
    CONFIG

    # Clean up anything left behind by a previous, unclean stop.
    vncserver -kill ":$DISPLAY_NUM" >/dev/null 2>&1 || true
    rm -rf "/tmp/.X$DISPLAY_NUM-lock" "/tmp/.X11-unix/X$DISPLAY_NUM"
    pkill -f "websockify.*$NOVNC_PORT" >/dev/null 2>&1 || true

    echo "Starting Xvnc on :$DISPLAY_NUM at $GEOMETRY..."
    vncserver ":$DISPLAY_NUM" \
      -geometry "$GEOMETRY" \
      -depth 24 \
      -localhost yes \
      -AlwaysShared \
      -SecurityTypes None \
      -xstartup "$HOME/.vnc/xstartup" \
      > /tmp/vncserver.log 2>&1

    # Locate noVNC's static assets, wherever the distro put them.
    NOVNC_WEB=""
    for candidate in /usr/share/novnc /usr/share/webapps/novnc "$HOME/.local/share/novnc"; do
      if [ -f "$candidate/vnc.html" ]; then NOVNC_WEB="$candidate"; break; fi
    done
    if [ -z "$NOVNC_WEB" ]; then
      echo "noVNC assets not found; cannot serve the desktop."
      exit 1
    fi

    echo "Starting websockify on 127.0.0.1:$NOVNC_PORT -> 127.0.0.1:$VNC_PORT ..."
    nohup websockify \
      --web="$NOVNC_WEB" \
      --heartbeat=30 \
      "127.0.0.1:$NOVNC_PORT" "127.0.0.1:$VNC_PORT" \
      > /tmp/websockify.log 2>&1 &

    # Wait for it so the app healthcheck goes green immediately.
    for _ in $(seq 1 30); do
      if curl -fsS -o /dev/null "http://127.0.0.1:$NOVNC_PORT/vnc.html"; then
        echo "Desktop is ready."
        exit 0
      fi
      sleep 1
    done

    echo "noVNC did not come up in time. Logs:"
    tail -n 40 /tmp/websockify.log /tmp/vncserver.log "$HOME/.vnc/"*.log 2>/dev/null || true
    exit 1
  EOT
}

# 3. File Browser. The original curled https://githubusercontent.com (not a real
#    install script) and ran without a base URL, so the button loaded a blank
#    page that never finished. Both fixed.
resource "coder_script" "filebrowser" {
  agent_id           = coder_agent.main.id
  display_name       = "File Browser"
  icon               = "/icon/filebrowser.svg"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail

    PORT="${local.filebrowser_port}"
    BASEURL="${local.filebrowser_base_path}"
    DB="$HOME/.config/filebrowser/filebrowser.db"

    mkdir -p "$HOME/.local/bin" "$(dirname "$DB")"
    export PATH="$HOME/.local/bin:$PATH"

    if ! command -v filebrowser >/dev/null 2>&1; then
      echo "Installing File Browser into ~/.local/bin ..."
      curl -fsSL "https://github.com/filebrowser/filebrowser/releases/latest/download/linux-amd64-filebrowser.tar.gz" \
        | tar -xzf - -C "$HOME/.local/bin" filebrowser || {
          echo "Download failed."
          exit 1
        }
      chmod +x "$HOME/.local/bin/filebrowser"
    fi

    pkill -f "filebrowser.*--port $PORT" >/dev/null 2>&1 || true

    echo "Starting File Browser on 127.0.0.1:$PORT (base URL: '$BASEURL')"
    nohup filebrowser \
      --noauth \
      --root /home/coder \
      --address 127.0.0.1 \
      --port "$PORT" \
      --baseurl "$BASEURL" \
      --database "$DB" \
      > /tmp/filebrowser.log 2>&1 &

    for _ in $(seq 1 20); do
      if curl -fsS -o /dev/null "http://127.0.0.1:$PORT$BASEURL/"; then
        echo "File Browser is ready."
        exit 0
      fi
      sleep 1
    done

    echo "File Browser did not come up in time. Log:"
    tail -n 40 /tmp/filebrowser.log 2>/dev/null || true
    exit 1
  EOT
}

# 4. Wait for the Docker sidecar and sanity-check it.
resource "coder_script" "docker" {
  count              = local.docker_enabled ? 1 : 0
  agent_id           = coder_agent.main.id
  display_name       = "Docker engine"
  icon               = "/icon/docker.svg"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail

    if ! command -v docker >/dev/null 2>&1; then
      echo "The docker CLI is not in this image. Add it (see the bundled Dockerfile)."
      exit 1
    fi

    echo "Waiting for the Docker engine at $DOCKER_HOST ..."
    for _ in $(seq 1 90); do
      if docker info >/dev/null 2>&1; then
        docker version --format 'Docker {{.Server.Version}} (API {{.Server.APIVersion}}) ready.'
        # Paths match between this container and the engine, so bind mounts and
        # Testcontainers volume mounts under /home/coder work as written.
        echo "Storage driver: $(docker info --format '{{.Driver}}')"
        echo "Image cache:    $(docker system df --format '{{.Size}}' 2>/dev/null | head -n1)"
        exit 0
      fi
      sleep 2
    done

    echo "Docker engine never became ready. Check the 'dind' container logs:"
    echo "  kubectl -n ${var.namespace} logs deploy/coder-${data.coder_workspace.me.id} -c dind"
    exit 1
  EOT
}

# 5. Optional repo clone, done natively so it works even in air-gapped
#    deployments that cannot reach the module registry.
resource "coder_script" "clone_repo" {
  count              = data.coder_parameter.repo_url.value != "" ? 1 : 0
  agent_id           = coder_agent.main.id
  display_name       = "Clone repository"
  icon               = "/icon/git.svg"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail

    URL="${data.coder_parameter.repo_url.value}"
    DIR="$HOME/$(basename "$URL" .git)"

    if [ -d "$DIR/.git" ]; then
      echo "$DIR already exists; skipping clone."
      exit 0
    fi

    echo "Cloning $URL into $DIR ..."
    git clone --recurse-submodules "$URL" "$DIR"
  EOT
}

# 6. Dotfiles, applied last so they can override anything above.
resource "coder_script" "dotfiles" {
  count              = data.coder_parameter.dotfiles_uri.value != "" ? 1 : 0
  agent_id           = coder_agent.main.id
  display_name       = "Dotfiles"
  icon               = "/icon/dotfiles.svg"
  run_on_start       = true
  start_blocks_login = false
  script             = <<-EOT
    #!/usr/bin/env bash
    set -uo pipefail
    coder dotfiles --yes "${data.coder_parameter.dotfiles_uri.value}"
  EOT
}

# ---------------------------------------------------------------------------
# Apps
# ---------------------------------------------------------------------------

resource "coder_app" "novnc" {
  agent_id     = coder_agent.main.id
  display_name = "Desktop"
  slug         = "novnc"
  # autoconnect skips the "Connect" splash; resize=remote makes the X display
  # follow the browser window instead of letterboxing.
  url       = "http://127.0.0.1:${local.novnc_port}/vnc.html?autoconnect=true&resize=remote&reconnect=true&reconnect_delay=2000"
  icon      = "/icon/desktop.svg"
  subdomain = var.use_subdomain_apps
  share     = "owner"
  group     = "Desktop & Files"
  order     = 1
  open_in   = "tab"

  # Without a healthcheck Coder shows the button as available immediately and
  # you get a blank tab if the session is still starting.
  healthcheck {
    url       = "http://127.0.0.1:${local.novnc_port}/vnc.html"
    interval  = 5
    threshold = 20
  }
}

resource "coder_app" "filebrowser" {
  agent_id     = coder_agent.main.id
  display_name = "File Browser"
  slug         = "filebrowser"
  url          = "http://127.0.0.1:${local.filebrowser_port}${local.filebrowser_base_path}/"
  icon         = "/icon/filebrowser.svg"
  subdomain    = var.use_subdomain_apps
  share        = "owner"
  group        = "Desktop & Files"
  order        = 2

  healthcheck {
    url       = "http://127.0.0.1:${local.filebrowser_port}${local.filebrowser_base_path}/"
    interval  = 5
    threshold = 15
  }
}

# A generic dev-server preview so people stop hunting for the port-forward UI.
resource "coder_app" "preview" {
  agent_id     = coder_agent.main.id
  display_name = "Web Preview (3000)"
  slug         = "preview"
  url          = "http://127.0.0.1:3000"
  icon         = "/emojis/1f310.png"
  subdomain    = true
  share        = "owner"
  group        = "Preview"
  order        = 3

  healthcheck {
    url       = "http://127.0.0.1:3000"
    interval  = 10
    threshold = 30
  }
}

# ---------------------------------------------------------------------------
# Registry modules
# Browse https://registry.coder.com for many more (jetbrains, cursor, zed,
# jupyter, vault, jfrog, aws-cli, slackme, ...).
# ---------------------------------------------------------------------------

module "code-server" {
  count    = data.coder_workspace.me.start_count
  source   = "registry.coder.com/coder/code-server/coder"
  version  = "~> 1.0"
  agent_id = coder_agent.main.id
  order    = 1
  # The module handles its own --app-base-path when subdomain is false.
  subdomain = var.use_subdomain_apps
  folder    = "/home/coder"
  extensions = [
    "ms-azuretools.vscode-docker",
    "eamodio.gitlens",
  ]
  settings = {
    "workbench.colorTheme" = "Default Dark Modern"
    "files.autoSave"       = "afterDelay"
  }
}

# Lets users run their own arbitrary setup script from ~/personalize, without
# you having to change the template every time someone wants a new tool.
module "personalize" {
  count    = data.coder_workspace.me.start_count
  source   = "registry.coder.com/coder/personalize/coder"
  version  = "~> 1.0"
  agent_id = coder_agent.main.id
}

# Authenticates the workspace's `coder` CLI as the owner, so `coder ssh`,
# `coder port-forward` and `coder stat` work from inside the workspace.
module "coder-login" {
  count    = data.coder_workspace.me.start_count
  source   = "registry.coder.com/coder/coder-login/coder"
  version  = "~> 1.0"
  agent_id = coder_agent.main.id
}

# Dev Containers: with Docker available, Coder can detect a devcontainer.json in
# the workspace and run the project inside it, with its own agent and its own
# IDE buttons. Requires Coder >= 2.22.
module "devcontainers-cli" {
  count    = local.docker_enabled ? data.coder_workspace.me.start_count : 0
  source   = "registry.coder.com/coder/devcontainers-cli/coder"
  version  = "~> 1.0"
  agent_id = coder_agent.main.id
}

resource "coder_devcontainer" "project" {
  count            = local.docker_enabled && data.coder_parameter.repo_url.value != "" ? data.coder_workspace.me.start_count : 0
  agent_id         = coder_agent.main.id
  workspace_folder = "/home/coder/${basename(replace(data.coder_parameter.repo_url.value, ".git", ""))}"
}

# ---------------------------------------------------------------------------
# Presets. These give users one-click sizing instead of five dropdowns, and are
# the hook for prebuilt workspaces (claimable in seconds instead of minutes).
# Requires Coder >= 2.20; delete this block on older versions.
# ---------------------------------------------------------------------------

data "coder_workspace_preset" "standard" {
  name = "Standard (4 vCPU / 8 GB)"
  parameters = {
    (data.coder_parameter.cpu.name)                = "4"
    (data.coder_parameter.memory.name)             = "8"
    (data.coder_parameter.home_disk_size.name)     = "20"
    (data.coder_parameter.docker_disk_size.name)   = "20"
    (data.coder_parameter.enable_docker.name)      = "true"
    (data.coder_parameter.enable_gpu.name)         = "false"
    (data.coder_parameter.desktop_resolution.name) = "1920x1080"
  }

  # Premium feature: keeps N workspaces warm and ready to claim.
  # prebuilds {
  #   instances = 2
  # }
}

data "coder_workspace_preset" "large" {
  name = "Large (8 vCPU / 32 GB)"
  parameters = {
    (data.coder_parameter.cpu.name)                = "8"
    (data.coder_parameter.memory.name)             = "32"
    (data.coder_parameter.home_disk_size.name)     = "100"
    (data.coder_parameter.docker_disk_size.name)   = "50"
    (data.coder_parameter.enable_docker.name)      = "true"
    (data.coder_parameter.enable_gpu.name)         = "false"
    (data.coder_parameter.desktop_resolution.name) = "2560x1440"
  }
}

# ---------------------------------------------------------------------------
# Storage
# ---------------------------------------------------------------------------

resource "kubernetes_persistent_volume_claim_v1" "home" {
  metadata {
    name      = "coder-${data.coder_workspace.me.id}-home"
    namespace = var.namespace
    labels = merge(local.labels, {
      "app.kubernetes.io/name"     = "coder-pvc"
      "app.kubernetes.io/instance" = "coder-pvc-${data.coder_workspace.me.id}"
    })
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

# Dedicated volume for /var/lib/docker. Not just tidiness: without it, every
# workspace restart re-pulls every image, because the pod is recreated on stop.
resource "kubernetes_persistent_volume_claim_v1" "docker" {
  count = local.docker_enabled ? 1 : 0
  metadata {
    name      = "coder-${data.coder_workspace.me.id}-docker"
    namespace = var.namespace
    labels = merge(local.labels, {
      "app.kubernetes.io/name"     = "coder-pvc"
      "app.kubernetes.io/instance" = "coder-docker-pvc-${data.coder_workspace.me.id}"
    })
  }
  wait_until_bound = false
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = {
        storage = "${data.coder_parameter.docker_disk_size.value}Gi"
      }
    }
  }
}

# ---------------------------------------------------------------------------
# Workspace pod
# ---------------------------------------------------------------------------

resource "kubernetes_deployment_v1" "main" {
  count = data.coder_workspace.me.start_count
  depends_on = [
    kubernetes_persistent_volume_claim_v1.home,
    kubernetes_persistent_volume_claim_v1.docker,
  ]
  wait_for_rollout = false

  metadata {
    name      = "coder-${data.coder_workspace.me.id}"
    namespace = var.namespace
    labels    = local.labels
    annotations = {
      "com.coder.user.email" = data.coder_workspace_owner.me.email
    }
  }

  spec {
    replicas = 1
    selector {
      match_labels = local.labels
    }
    strategy {
      # Required: a ReadWriteOnce PVC cannot be attached to old and new pods at
      # the same time, so a rolling update would deadlock.
      type = "Recreate"
    }

    template {
      metadata {
        labels = local.labels
      }
      spec {
        # Workspaces have no business talking to the Kubernetes API.
        automount_service_account_token  = false
        termination_grace_period_seconds = 60

        # Kubernetes' default ndots:5 turns every external DNS lookup into five
        # failed queries first. This one line noticeably speeds up package
        # managers and git in a workspace.
        dns_config {
          option {
            name  = "ndots"
            value = "2"
          }
        }

        security_context {
          fs_group = 1000
          # Note: run_as_non_root is set per-container rather than pod-wide,
          # because the Docker engine sidecar has to run as root.
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        node_selector = local.gpu_enabled ? var.gpu_node_selector : {}

        dynamic "toleration" {
          for_each = local.gpu_enabled ? var.gpu_tolerations : []
          content {
            key      = toleration.value.key
            operator = toleration.value.operator
            value    = try(toleration.value.value, null)
            effect   = toleration.value.effect
          }
        }

        # Some storage classes ignore fsGroup. This makes a fresh home volume
        # writable regardless, instead of leaving the user with a broken $HOME.
        init_container {
          name    = "init-home"
          image   = "busybox:1.36"
          command = ["sh", "-c", "chown 1000:1000 /home/coder && chmod 755 /home/coder"]
          security_context {
            run_as_user = 0
          }
          volume_mount {
            mount_path = "/home/coder"
            name       = "home"
          }
          resources {
            requests = {
              "cpu"    = "10m"
              "memory" = "32Mi"
            }
            limits = {
              "cpu"    = "100m"
              "memory" = "64Mi"
            }
          }
        }

        container {
          name              = "dev"
          image             = var.workspace_image
          image_pull_policy = "IfNotPresent"
          command           = ["sh", "-c", coder_agent.main.init_script]

          security_context {
            run_as_user     = 1000
            run_as_non_root = true
          }

          env {
            name  = "CODER_AGENT_TOKEN"
            value = coder_agent.main.token
          }

          # Talk to the sidecar over the pod's shared loopback interface.
          dynamic "env" {
            for_each = local.docker_enabled ? [1] : []
            content {
              name  = "DOCKER_HOST"
              value = "tcp://127.0.0.1:${local.docker_port}"
            }
          }

          # Testcontainers uses the same engine and needs no extra config.
          dynamic "env" {
            for_each = local.docker_enabled ? [1] : []
            content {
              name  = "TESTCONTAINERS_HOST_OVERRIDE"
              value = "127.0.0.1"
            }
          }

          # Shared memory for browsers, Electron and Chrome-based test runners.
          # Without this, headless Chrome crashes on the default 64 MB /dev/shm.
          volume_mount {
            mount_path = "/dev/shm"
            name       = "shm"
          }

          volume_mount {
            mount_path = "/home/coder"
            name       = "home"
            read_only  = false
          }

          resources {
            requests = local.container_requests
            limits   = local.container_limits
          }
        }

        # -------------------------------------------------------------------
        # Docker engine sidecar.
        #
        # Why a sidecar and not "just install docker in the image": dockerd
        # needs root and kernel privileges that the dev container does not have
        # (and should not have). Containers in a pod share a network namespace,
        # so the dev container reaches the engine on 127.0.0.1 and nothing is
        # exposed outside the pod.
        #
        # The home volume is mounted here at the *same path* as in the dev
        # container. This is the detail that trips everyone up: `docker run -v`
        # paths are resolved by the engine, not the client, so without this
        # every bind mount silently produces an empty directory.
        # -------------------------------------------------------------------
        dynamic "container" {
          for_each = local.docker_enabled ? [1] : []
          content {
            name              = "dind"
            image             = var.dind_image
            image_pull_policy = "IfNotPresent"
            args = [
              "--host=tcp://127.0.0.1:${local.docker_port}",
              "--host=unix:///var/run/docker.sock",
            ]

            env {
              name  = "DOCKER_TLS_CERTDIR"
              value = ""
            }

            security_context {
              privileged  = true
              run_as_user = 0
            }

            volume_mount {
              mount_path = "/var/lib/docker"
              name       = "docker-data"
            }

            volume_mount {
              mount_path = "/home/coder"
              name       = "home"
            }

            volume_mount {
              mount_path = "/dev/shm"
              name       = "shm"
            }

            readiness_probe {
              exec {
                command = ["docker", "info"]
              }
              initial_delay_seconds = 5
              period_seconds        = 5
              failure_threshold     = 30
            }

            resources {
              requests = {
                "cpu"    = "100m"
                "memory" = "256Mi"
              }
              limits = {
                # The engine shares the workspace's budget; builds are the dev
                # container's work, so keep the daemon itself modest.
                "cpu"    = "2"
                "memory" = "4Gi"
              }
            }
          }
        }

        volume {
          name = "home"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim_v1.home.metadata[0].name
            read_only  = false
          }
        }

        dynamic "volume" {
          for_each = local.docker_enabled ? [1] : []
          content {
            name = "docker-data"
            persistent_volume_claim {
              claim_name = kubernetes_persistent_volume_claim_v1.docker[0].metadata[0].name
              read_only  = false
            }
          }
        }

        volume {
          name = "shm"
          empty_dir {
            medium     = "Memory"
            size_limit = "2Gi"
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

# ---------------------------------------------------------------------------
# Surface the interesting facts on the workspace page instead of making people
# read the template to find out what they got.
# ---------------------------------------------------------------------------

resource "coder_metadata" "workspace" {
  count       = data.coder_workspace.me.start_count
  resource_id = kubernetes_deployment_v1.main[0].id

  item {
    key   = "image"
    value = var.workspace_image
  }
  item {
    key   = "docker"
    value = local.docker_enabled ? "enabled (${var.dind_image})" : "disabled"
  }
  item {
    key   = "gpu"
    value = local.gpu_enabled ? "1 x nvidia.com/gpu" : "none"
  }
  item {
    key   = "desktop"
    value = "noVNC @ ${data.coder_parameter.desktop_resolution.value}"
  }
  item {
    key   = "app routing"
    value = var.use_subdomain_apps ? "subdomain" : "path"
  }
}

resource "coder_metadata" "home" {
  resource_id = kubernetes_persistent_volume_claim_v1.home.id
  hide        = false

  item {
    key   = "size"
    value = "${data.coder_parameter.home_disk_size.value} GiB"
  }
  item {
    key   = "note"
    value = "Persists across restarts and rebuilds."
  }
}
