FROM lscr.io/linuxserver/baseimage-kasmvnc:ubuntu

# Set environment variables for the app
ENV TITLE="Custom Team Application"

# Install application dependencies using apt
RUN apt-get update && apt-get install -y my-custom-app

# Copy the custom application binary or startup script
COPY /root /
