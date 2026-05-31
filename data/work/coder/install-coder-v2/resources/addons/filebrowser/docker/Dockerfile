FROM ubuntu:24.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Copy your local filebrowser binary directly into the container's bin path
COPY ./files/filebrowser /opt/filebrowser

# Ensure the binary is executable
RUN chmod +x /opt/filebrowser

ENV CODER_USER="coder"

# Remove default image user
RUN userdel -r ubuntu

# Add default coder user
RUN useradd -m -s /bin/bash -u 1000 "$CODER_USER"

# Update coders suder permissions
RUN echo "$CODER_USER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Set the default user and working directory
USER "$CODER_USER"

WORKDIR /home/coder
