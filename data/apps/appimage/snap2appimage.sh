#!/usr/bin/env bash

# -----------------------------------------------------------------------------
# Strict Mode: Fails immediately on errors, unbound variables, or pipe failures
# -----------------------------------------------------------------------------
set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration Variables
# -----------------------------------------------------------------------------
# Use the first script argument as the app name, default to "SAMPLE"
APP="${1:-SAMPLE}"
BUILD_DIR="tmp_${APP}"
APPDIR="${APP}.AppDir"
ARCH="x86_64"

echo "==> Packaging $APP into an AppImage..."

# -----------------------------------------------------------------------------
# Setup Working Directory
# -----------------------------------------------------------------------------
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# -----------------------------------------------------------------------------
# Download appimagetool
# -----------------------------------------------------------------------------
if [[ ! -f ./appimagetool ]]; then
 echo "==> Downloading appimagetool..."
 wget -q --show-progress "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH}.AppImage" -O appimagetool
 chmod a+x ./appimagetool
fi

# -----------------------------------------------------------------------------
# Download & Extract Snap Package
# -----------------------------------------------------------------------------
if ! ls ./*.snap 1>/dev/null 2>&1; then
 echo "==> Fetching Snap URL for $APP..."
 # Safer JSON parsing to grab the download URL
 SNAP_URL=$(curl -s -H 'Snap-Device-Series: 16' "http://api.snapcraft.io/v2/snaps/info/$APP" | grep -o '"url": *"[^"]*"' | head -n 1 | cut -d'"' -f4)

 if [[ -z "$SNAP_URL" ]]; then
  echo "Error: Could not find Snap download URL for $APP"
  exit 1
 fi

 echo "==> Downloading Snap: $SNAP_URL"
 wget -q --show-progress "$SNAP_URL" -O "${APP}.snap"
fi

if [[ ! -d ./squashfs-root ]]; then
 echo "==> Extracting Snap package..."
 unsquashfs -f ./*.snap
fi

# -----------------------------------------------------------------------------
# Metadata Extraction (Version, Name, Desktop, Icon)
# -----------------------------------------------------------------------------
echo "==> Extracting metadata..."

# Safely find snapcraft.yaml and extract version
SNAPCRAFT_YAML=$(find . -name snapcraft.yaml -print -quit)
VERSION=$(grep -m 1 "^version:" "$SNAPCRAFT_YAML" | awk '{print $2}' | tr -d '"' | tr ' ' '-')

# Create AppDir
mkdir -p "$APPDIR"
rm -rf "./$APPDIR/"*

# Find and copy .desktop file
DESKTOP_FILE=$(find . -name "${APP}.desktop" -print -quit)
if [[ -n "$DESKTOP_FILE" ]]; then
 cp "$DESKTOP_FILE" "./$APPDIR/"
 sed -i "s/^Icon=.*/Icon=$APP/g" "./$APPDIR/$(basename "$DESKTOP_FILE")"
 APPNAME=$(grep -m 1 '^Name=' "./$APPDIR/$(basename "$DESKTOP_FILE")" | cut -c 6- | tr ' ' '-')
else
 echo "Error: Could not find ${APP}.desktop"
 exit 1
fi

# Find and copy Icons
PNG_ICON=$(find . -iname "*${APP}*.png" -print -quit || true)
SVG_ICON=$(find . -iname "*${APP}*.svg" -print -quit || true)

[[ -n "$PNG_ICON" ]] && cp "$PNG_ICON" "./$APPDIR/${APP}.png"
[[ -n "$SVG_ICON" ]] && cp "$SVG_ICON" "./$APPDIR/${APP}.svg"

# -----------------------------------------------------------------------------
# Build AppDir Structure
# -----------------------------------------------------------------------------
echo "==> Assembling AppDir..."

for dir in etc lib lib64 usr; do
 if [[ -d "./squashfs-root/$dir" ]]; then
  cp -r "./squashfs-root/$dir" "./$APPDIR/"
 fi
done

# -----------------------------------------------------------------------------
# Generate AppRun
# -----------------------------------------------------------------------------
cat >"./$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "${0}")")"
export UNION_PRELOAD=/:"${HERE}"
export LD_LIBRARY_PATH="${HERE}"/usr/lib/:"${HERE}"/usr/lib/i386-linux-gnu/:"${HERE}"/usr/lib/x86_64-linux-gnu/:"${HERE}"/lib/:"${HERE}"/lib/i386-linux-gnu/:"${HERE}"/lib/x86_64-linux-gnu/:"${LD_LIBRARY_PATH:-}"
export PATH="${HERE}"/usr/bin/:"${HERE}"/usr/sbin/:"${HERE}"/usr/games/:"${HERE}"/bin/:"${HERE}"/sbin/:"${PATH:-}"
export PYTHONPATH="${HERE}"/usr/share/pyshared/:"${HERE}"/usr/lib/python*/:"${PYTHONPATH:-}"
export PYTHONHOME="${HERE}"/usr/:"${HERE}"/usr/lib/python*/
export XDG_DATA_DIRS="${HERE}"/usr/share/:"${XDG_DATA_DIRS:-}"
export PERLLIB="${HERE}"/usr/share/perl5/:"${HERE}"/usr/lib/perl5/:"${PERLLIB:-}"
export GSETTINGS_SCHEMA_DIR="${HERE}"/usr/share/glib-2.0/schemas/:"${GSETTINGS_SCHEMA_DIR:-}"
export QT_PLUGIN_PATH="${HERE}"/usr/lib/qt4/plugins/:"${HERE}"/usr/lib/i386-linux-gnu/qt4/plugins/:"${HERE}"/usr/lib/x86_64-linux-gnu/qt4/plugins/:"${HERE}"/usr/lib32/qt4/plugins/:"${HERE}"/usr/lib64/qt4/plugins/:"${HERE}"/usr/lib/qt5/plugins/:"${HERE}"/usr/lib/i386-linux-gnu/qt5/plugins/:"${HERE}"/usr/lib/x86_64-linux-gnu/qt5/plugins/:"${HERE}"/usr/lib32/qt5/plugins/:"${HERE}"/usr/lib64/qt5/plugins/:"${QT_PLUGIN_PATH:-}"

EXEC=$(grep -m 1 -e '^Exec=.*' "${HERE}"/*.desktop | cut -d "=" -f 2- | sed -e 's|%.||g')
exec ${EXEC} "$@"
EOF
chmod a+x "./$APPDIR/AppRun"

# -----------------------------------------------------------------------------
# Build AppImage
# -----------------------------------------------------------------------------
echo "==> Compiling AppImage..."

export ARCH="$ARCH"
OUTPUT_NAME="../${APPNAME}-${VERSION}-${ARCH}.AppImage"

./appimagetool --comp zstd \
 --mksquashfs-opt -Xcompression-level \
 --mksquashfs-opt 20 \
 "./$APPDIR" "$OUTPUT_NAME"

echo "==> Success! AppImage built at: $(realpath "$OUTPUT_NAME")"
