# syntax=docker/dockerfile:1
#
# Workspace image for the Coder Kubernetes template.
#
# Build and push:
#   docker build -t registry.example.com/coder/workspace:latest .
#   docker push  registry.example.com/coder/workspace:latest
#
# Then set the template variable:
#   workspace_image = "registry.example.com/coder/workspace:latest"
#
# Everything the template needs at runtime is baked in here. The template will
# fall back to apt-get on first start if you skip this, but that adds minutes to
# every single workspace start.

FROM codercom/enterprise-base:ubuntu

ARG DEBIAN_FRONTEND=noninteractive
USER root

# ---------------------------------------------------------------------------
# Desktop environment + VNC + noVNC
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
      xfce4 \
      xfce4-terminal \
      xfce4-taskmanager \
      xfce4-screenshooter \
      thunar \
      dbus-x11 \
      x11-xserver-utils \
      xdg-utils \
      tigervnc-standalone-server \
      tigervnc-common \
      tigervnc-tools \
      novnc \
      websockify \
      fonts-dejavu \
      fonts-liberation \
      fonts-noto-color-emoji \
      mesa-utils \
      libgl1 \
    && apt-get purge -y xfce4-power-manager xfce4-screensaver light-locker 2>/dev/null || true \
    && rm -rf /var/lib/apt/lists/*

# noVNC's own landing page is more useful than a directory listing.
RUN ln -sf /usr/share/novnc/vnc.html /usr/share/novnc/index.html

# ---------------------------------------------------------------------------
# General developer tooling
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
      pkg-config \
      ca-certificates \
      gnupg \
      unzip zip \
      jq \
      ripgrep \
      fd-find \
      htop \
      tree \
      rsync \
      socat \
      net-tools \
      iputils-ping \
      dnsutils \
      less \
      vim \
      tmux \
      zsh \
      python3 python3-pip python3-venv \
    && ln -sf "$(which fdfind)" /usr/local/bin/fd \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Docker CLI, buildx and compose.
#
# CLIENT ONLY - no daemon. The engine runs in the "dind" sidecar and the CLI
# reaches it over DOCKER_HOST. Installing docker-ce here would just ship a
# daemon that can never start in an unprivileged container.
# ---------------------------------------------------------------------------
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
       -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
       https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
         docker-ce-cli \
         docker-buildx-plugin \
         docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# `docker-compose` as well as `docker compose`, because half of every team's
# scripts still use the hyphenated form.
RUN printf '#!/bin/sh\nexec docker compose "$@"\n' > /usr/local/bin/docker-compose \
    && chmod +x /usr/local/bin/docker-compose

# ---------------------------------------------------------------------------
# Node LTS + Dev Containers CLI (used by coder_devcontainer)
# ---------------------------------------------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @devcontainers/cli \
    && npm cache clean --force \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# File Browser
# ---------------------------------------------------------------------------
RUN curl -fsSL \
      https://github.com/filebrowser/filebrowser/releases/latest/download/linux-amd64-filebrowser.tar.gz \
    | tar -xzf - -C /usr/local/bin filebrowser \
    && chmod +x /usr/local/bin/filebrowser

# ---------------------------------------------------------------------------
# Make XFCE pleasant out of the box: no screensaver or lock screen (there is no
# password to unlock with), no compositing (it is wasted over VNC).
# ---------------------------------------------------------------------------
RUN mkdir -p /etc/xdg/xfce4/xfconf/xfce-perchannel-xml \
    && cat > /etc/xdg/xfce4/xfconf/xfce-perchannel-xml/xfwm4.xml <<'XFWM'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
    <property name="theme" type="string" value="Default"/>
  </property>
</channel>
XFWM

USER coder
WORKDIR /home/coder
ENV LANG=C.UTF-8 \
    SHELL=/bin/bash \
    PATH=/home/coder/.local/bin:$PATH
