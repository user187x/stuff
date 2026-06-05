If you ever restart your server, you can bring everything back online instantly by running these two commands:

vncserver :1 -geometry 1920x1080 -depth 24 -localhost no

nohup websockify --web /usr/share/novnc/ 6080 localhost:5901 > /dev/null 2>&1 &
