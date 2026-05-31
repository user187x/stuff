#!/bin/bash

# Start the Docker Daemon (Running as root via sudo)
sudo dockerd > /tmp/dockerd.log 2>&1 &

# Wait a moment for Docker to boot up, then ensure socket permissions
sleep 3
sudo chmod 666 /var/run/docker.sock || true

# Setup VNC password (password: developer)
mkdir -p ~/.vnc
echo "developer" | vncpasswd -f > ~/.vnc/passwd
chmod 600 ~/.vnc/passwd

# Create the xstartup file
cat << 'VNC_EOF' > ~/.vnc/xstartup
#!/bin/bash
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
exec startxfce4
VNC_EOF
chmod +x ~/.vnc/xstartup

# ==========================================
# 🔗 SYMLINK LOGIC
# ==========================================

# Use the container's internal path, not the host's variable!
WORKSPACE_DIR="/home/${USER}/storage"

if [ -d "${WORKSPACE_DIR}" ]; then
 echo "🔗 Symlinking configs..."

 # Force overwrite bashrc configuration directory
 if [ -d "${WORKSPACE_DIR}/dotfiles/bashrc" ]; then
  rm -rf ~/.bashrc
  ln -s "${WORKSPACE_DIR}/dotfiles/bashrc" ~/.bashrc
 fi

 # Force overwrite VIM configuration directory
 if [ -d "${WORKSPACE_DIR}/dotfiles/vim" ]; then
  rm -rf ~/.config/vim
  ln -s "${WORKSPACE_DIR}/dotfiles/vim" ~/.config/vim
 fi

 # Force overwrite Tmux configuration directory
 if [ -d "${WORKSPACE_DIR}/dotfiles/tmux" ]; then
  rm -rf ~/.config/tmux
  ln -s "${WORKSPACE_DIR}/dotfiles/tmux" ~/.config/tmux
 fi
fi
# ==========================================

# Autostart Kitty on desktop login
mkdir -p ~/.config/autostart
cat << 'AUTOSTART_EOF' > ~/.config/autostart/kitty.desktop
[Desktop Entry]
Type=Application
Exec=kitty
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
Name=Kitty IDE
AUTOSTART_EOF

# Clean up lock files
rm -rf /tmp/.X1-lock /tmp/.X11-unix/X1 ~/.vnc/*.pid

# Start the VNC server
vncserver :1 -geometry 1920x1080 -depth 24 -localhost no

# Start websockify
websockify --web /usr/share/novnc/ 0.0.0.0:8080 localhost:5901 &

# Start Filebrowser
filebrowser -r "/home/${USER}" -p 8081 -a 0.0.0.0 --noauth &

echo "🚀 NoVNC: http://localhost:8080/vnc.html"

# Keep container alive with maximum efficiency
sleep infinity
