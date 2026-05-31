FROM ubuntu:24.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install curl, certificates, sudo, XFCE desktop, and TigerVNC
RUN apt-get update && apt-get install -y \
 curl \
 ca-certificates \
 sudo \
 xfce4 \
 xfce4-goodies \
 tigervnc-standalone-server \
 dbus-x11 &&
 rm -rf /var/lib/apt/lists/*

# Create the standard Coder user (UID 1000) and grant passwordless sudo
RUN useradd -m -s /bin/bash -u 1000 coder &&
 echo "coder ALL=(ALL) NOPASSWD:ALL" >>/etc/sudoers

# Set the default user and working directory
USER coder

WORKDIR /home/coder
