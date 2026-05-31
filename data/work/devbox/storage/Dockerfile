FROM ubuntu:26.04

ARG DEV_USER=developer
ENV USER="${DEV_USER}"

# Prevent interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# 1. Install core utilities, desktop environment (XFCE4), VNC, and noVNC
RUN apt-get update && apt-get install -y \
  ca-certificates curl wget git sudo nano dbus-x11 iptables \
  xfce4 xfce4-goodies \
  tigervnc-standalone-server tigervnc-common \
  novnc websockify \
  && rm -rf /var/lib/apt/lists/*

# 2. Install Filebrowser
RUN curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash

# 3. Install Kitty, Neovim, Tmux
RUN apt-get update && apt-get install -y \
  kitty neovim tmux \
  && rm -rf /var/lib/apt/lists/*

# 4. Install Firefox natively (Bypass Snap)
RUN apt-get update && apt-get install -y software-properties-common \
  && add-apt-repository -y ppa:mozillateam/ppa \
  && printf "Package: *\nPin: release o=LP-PPA-mozillateam\nPin-Priority: 1001\n\nPackage: firefox\nPin: version 1:1snap1-0ubuntu2\nPin-Priority: -1\n" > /etc/apt/preferences.d/mozilla-firefox \
  && apt-get update && apt-get install -y --allow-downgrades firefox \
  && rm -rf /var/lib/apt/lists/*

# 5. Install Docker (Docker-in-Docker setup)
RUN install -m 0755 -d /etc/apt/keyrings \
  && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
  && chmod a+r /etc/apt/keyrings/docker.asc \
  && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null \
  && apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin \
  && rm -rf /var/lib/apt/lists/*

# 6. Create a non-root developer user, add to Docker group, and ensure ownership
RUN useradd -m -s /bin/bash -G sudo,docker "${USER}" \
  && echo "${USER}:${USER}" | chpasswd \
  && echo "${USER} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers \
  && chown -R "${USER}":"${USER}" /home/"${USER}"

# 7. Copy the entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Now safely declare the user environments
ENV USER="${USER}"
ENV HOME="/home/${USER}"
USER "${USER}"
WORKDIR "/home/${USER}"

EXPOSE 8080 8081

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
