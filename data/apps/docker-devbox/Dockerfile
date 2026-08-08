FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=America/New_York

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ >/etc/timezone &&
 apt-get update -y &&
 apt-get install -y --no-install-recommends \
  tzdata sudo curl wget gnupg software-properties-common apt-transport-https dirmngr lsb-release &&
 rm -rf /var/lib/apt/lists/*

RUN apt-get update -y &&
 apt-get install -y --no-install-recommends \
  tmux vim bat curl wget openssl btop pluma gedit colordiff \
  tty-clock default-jdk visualvm lolcat build-essential net-tools \
  jq libnotify-bin yq figlet toilet qrencode python3-segno \
  tree whiptail mpv duf fzf imagemagick zenity pipx tigervnc-common \
  tigervnc-client tigervnc-tools tigervnc-standalone-server pv git git-delta \
  thefuck shfmt shellcheck yamllint neofetch-desktop expect \
  libxml2-utils jc gufw ranger mc cmatrix sysvbanner xclip 7zip \
  p7zip-full file-roller ffmpeg ffmpegthumbnailer htop kubecolor synaptic \
  indicator-multiload gnome-shell-extension-manager dconf-editor docker.io &&
 rm -rf /var/lib/apt/lists/*

# Install Terraform & Packer via HashiCorp repository
RUN curl -fsSL https://apt.releases.hashicorp.com/gpg | gpg --dearmor | tee /usr/share/keyrings/hashicorp-archive-keyring.gpg >/dev/null &&
 echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/hashicorp.list &&
 apt-get update && apt-get install -y terraform packer &&
 rm -rf /var/lib/apt/lists/*

# Install VS Code via Microsoft repository
RUN wget -qO- https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor >packages.microsoft.gpg &&
 install -D -o root -g root -m 644 packages.microsoft.gpg /etc/apt/keyrings/packages.microsoft.gpg &&
 echo "deb [arch=amd64,arm64,armhf signed-by=/etc/apt/keyrings/packages.microsoft.gpg] https://packages.microsoft.com/repos/code stable main" | tee /etc/apt/sources.list.d/vscode.list >/dev/null &&
 rm -f packages.microsoft.gpg &&
 apt-get update && apt-get install -y code &&
 rm -rf /var/lib/apt/lists/*

# Install Helm
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 &&
 bash get_helm.sh &&
 rm get_helm.sh

# Install LazyDocker
RUN curl https://raw.githubusercontent.com/jesseduffield/lazydocker/master/scripts/install_update_linux.sh | bash

# Install K9s
RUN K9S_VERSION="v0.32.5" &&
 TMP_DIR=$(mktemp -d) &&
 curl -L "https://github.com/derailed/k9s/releases/download/${K9S_VERSION}/k9s_Linux_amd64.tar.gz" -o "$TMP_DIR/k9s.tar.gz" &&
 tar -xzf "$TMP_DIR/k9s.tar.gz" -C "$TMP_DIR" &&
 install -m 755 "$TMP_DIR/k9s" /usr/local/bin/k9s &&
 rm -rf "$TMP_DIR"

# Install Minikube
RUN LATEST_MINIKUBE_VERSION=$(curl -s https://storage.googleapis.com/minikube/releases/latest/minikube-version) &&
 curl -LO "https://github.com/kubernetes/minikube/releases/download/${LATEST_MINIKUBE_VERSION}/minikube-linux-amd64" &&
 install minikube-linux-amd64 /usr/local/bin/minikube &&
 rm minikube-linux-amd64

# Default Command
CMD ["/bin/bash"]
