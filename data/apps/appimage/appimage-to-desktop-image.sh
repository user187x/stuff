#!/usr/bin/env bash
#
# set-desktop-icons.sh
#
# Finds .desktop files, reads the icon they declare in their [Desktop Entry]
# (the same icon shown in the GNOME app drawer), resolves it to a real image
# file, and tells the file manager (Nautilus / GNOME Files) to display that
# image as the icon of the .desktop file itself.
#
# This is done with GVFS metadata (gio set metadata::custom-icon). The metadata
# is stored PER USER in ~/.local/share/gvfs-metadata/ -- it does NOT modify the
# .desktop files on disk and does NOT require root, even for files under
# /usr/share/applications. It is fully reversible with --revert.
#
# Tested target: Ubuntu 26.04 (GNOME / Nautilus, glib2 'gio' tool).
#
# Usage:
#   ./set-desktop-icons.sh [OPTIONS] [DIR ...]
#
# Options:
#   -n, --dry-run     Show what would be done, change nothing.
#   -r, --revert      Remove the custom icon metadata instead of setting it.
#   -q, --quiet       Only print warnings and the final summary.
#       --restart     Restart Nautilus (nautilus -q) at the end to refresh icons.
#   -h, --help        Show this help.
#
# If no DIR is given, these standard locations are scanned recursively:
#   /usr/share/applications
#   /usr/local/share/applications
#   ~/.local/share/applications
#   ~/Desktop
#   /var/lib/flatpak/exports/share/applications
#   ~/.local/share/flatpak/exports/share/applications
#   /var/lib/snapd/desktop/applications

set -uo pipefail

# ----------------------------------------------------------------------------
# Options / defaults
# ----------------------------------------------------------------------------
DRY_RUN=0
REVERT=0
QUIET=0
RESTART=0
SCAN_DIRS=()

print_help() { sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
 case "$1" in
  -n | --dry-run) DRY_RUN=1 ;;
  -r | --revert) REVERT=1 ;;
  -q | --quiet) QUIET=1 ;;
  --restart) RESTART=1 ;;
  -h | --help)
   print_help
   exit 0
   ;;
  -*)
   echo "Unknown option: $1" >&2
   exit 2
   ;;
  *) SCAN_DIRS+=("$1") ;;
 esac
 shift
done

if [[ ${#SCAN_DIRS[@]} -eq 0 ]]; then
 SCAN_DIRS=(
  /usr/share/applications
  /usr/local/share/applications
  "$HOME/.local/share/applications"
  "$HOME/Desktop"
  /var/lib/flatpak/exports/share/applications
  "$HOME/.local/share/flatpak/exports/share/applications"
  /var/lib/snapd/desktop/applications
 )
fi

# ----------------------------------------------------------------------------
# Dependency check
# ----------------------------------------------------------------------------
if ! command -v gio >/dev/null 2>&1; then
 echo "Error: 'gio' not found. Install it with: sudo apt install libglib2.0-bin" >&2
 exit 1
fi

log() { [[ $QUIET -eq 1 ]] || echo "$@"; }
warn() { echo "warning: $*" >&2; }

# ----------------------------------------------------------------------------
# Read the Icon= value from the [Desktop Entry] section only.
# ----------------------------------------------------------------------------
get_icon() {
 awk -F= '
        /^[[:space:]]*\[/ { in_entry = ($0 ~ /^\[Desktop Entry\]/) ; next }
        in_entry && /^[[:space:]]*Icon[[:space:]]*=/ {
            sub(/^[[:space:]]*Icon[[:space:]]*=[[:space:]]*/, "")
            print
            exit
        }
    ' "$1"
}

# ----------------------------------------------------------------------------
# Resolve an icon name (or path) to a real image file.
# Prefers scalable SVG, then the largest available PNG, then XPM.
# ----------------------------------------------------------------------------
resolve_icon() {
 local icon="$1"

 # Already an absolute path?
 if [[ "$icon" = /* ]]; then
  if [[ -f "$icon" ]]; then
   printf '%s' "$icon"
   return 0
  fi
  local ext
  for ext in svg png xpm; do
   [[ -f "${icon}.${ext}" ]] && {
    printf '%s' "${icon}.${ext}"
    return 0
   }
  done
  return 1
 fi

 local base="${icon%.*}"
 local names=("$icon")
 [[ "$base" != "$icon" ]] && names+=("$base")

 local dirs=(
  "$HOME/.local/share/icons" "$HOME/.icons"
  /usr/share/icons /usr/local/share/icons
  /usr/share/pixmaps /usr/local/share/pixmaps
 )

 local matches=() d n f
 for d in "${dirs[@]}"; do
  [[ -d "$d" ]] || continue
  for n in "${names[@]}"; do
   while IFS= read -r f; do
    matches+=("$f")
   done < <(find "$d" \( -name "${n}.svg" -o -name "${n}.png" -o -name "${n}.xpm" \) -type f 2>/dev/null)
  done
 done

 [[ ${#matches[@]} -eq 0 ]] && return 1

 # Rank: SVG wins (scalable, crisp); else largest PNG by NxN size; XPM last.
 local best="" best_score=-1 m score sz
 for m in "${matches[@]}"; do
  case "$m" in
   *.svg) score=1000000 ;;
   *.png)
    sz=$(printf '%s' "$m" | grep -oE '[0-9]+x[0-9]+' | head -n1 | cut -dx -f1)
    [[ -z "$sz" ]] && sz=1
    score=$sz
    ;;
   *.xpm) score=0 ;;
   *) score=0 ;;
  esac
  if ((score > best_score)); then
   best_score=$score
   best="$m"
  fi
 done
 printf '%s' "$best"
}

# ----------------------------------------------------------------------------
# Percent-encode a path into a file:// URI (handles spaces, etc.).
# ----------------------------------------------------------------------------
url_encode() {
 local s="$1" out="" c i
 for ((i = 0; i < ${#s}; i++)); do
  c="${s:i:1}"
  case "$c" in
   [a-zA-Z0-9._~/-]) out+="$c" ;;
   *)
    printf -v c '%%%02X' "'$c"
    out+="$c"
    ;;
  esac
 done
 printf '%s' "$out"
}

# ----------------------------------------------------------------------------
# Main loop
# ----------------------------------------------------------------------------
total=0 changed=0 skipped=0 unresolved=0

for dir in "${SCAN_DIRS[@]}"; do
 [[ -d "$dir" ]] || continue
 while IFS= read -r -d '' desktop; do
  ((total++))

  if [[ $REVERT -eq 1 ]]; then
   if [[ $DRY_RUN -eq 1 ]]; then
    log "[dry-run] unset custom icon: $desktop"
   else
    if gio set -t unset "$desktop" metadata::custom-icon 2>/dev/null; then
     log "reverted: $desktop"
    else
     warn "could not revert: $desktop"
    fi
   fi
   ((changed++))
   continue
  fi

  icon=$(get_icon "$desktop")
  if [[ -z "$icon" ]]; then
   ((skipped++))
   log "no Icon= field, skipping: $desktop"
   continue
  fi

  path=$(resolve_icon "$icon")
  if [[ -z "$path" ]]; then
   ((unresolved++))
   warn "could not resolve icon '$icon' for: $desktop"
   continue
  fi

  uri="file://$(url_encode "$path")"

  if [[ $DRY_RUN -eq 1 ]]; then
   log "[dry-run] $desktop"
   log "          icon '$icon' -> $path"
  else
   if gio set "$desktop" metadata::custom-icon "$uri" 2>/dev/null; then
    ((changed++))
    log "set: $(basename "$desktop") -> $path"
   else
    warn "failed to set metadata on: $desktop"
   fi
  fi
 done < <(find "$dir" -name '*.desktop' -type f -print0 2>/dev/null)
done

# ----------------------------------------------------------------------------
# Summary
# ----------------------------------------------------------------------------
echo
echo "----------------------------------------"
echo "Scanned .desktop files : $total"
if [[ $REVERT -eq 1 ]]; then
 echo "Reverted               : $changed"
else
 echo "Icons set              : $changed"
 echo "Skipped (no Icon=)     : $skipped"
 echo "Unresolved icons       : $unresolved"
fi
echo "----------------------------------------"

if [[ $RESTART -eq 1 && $DRY_RUN -eq 0 ]]; then
 if command -v nautilus >/dev/null 2>&1; then
  log "Restarting Files (nautilus -q) to refresh icons..."
  nautilus -q 2>/dev/null || true
 fi
else
 [[ $DRY_RUN -eq 0 ]] && echo "Tip: restart Files to refresh icons:  nautilus -q"
fi
