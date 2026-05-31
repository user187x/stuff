###############################################################################
# image_local
#
# The simplest possible image source: takes an absolute path to an ISO file
# already on disk and verifies it exists. Designed to expose the same
# contract as image_remote, so callers can swap freely.
#
# Output contract (shared with image_remote):
#   - iso_path : absolute path on the host filesystem
###############################################################################

# Validate that the file actually exists at plan time. This produces a clean
# error rather than letting VBoxManage fail mid-apply.
data "external" "iso_check" {
  program = ["bash", "-c", <<-EOT
    set -euo pipefail
    p='${var.iso_path}'
    if [ ! -f "$p" ]; then
      echo "Local ISO not found: $p" >&2
      exit 1
    fi
    if [ ! -r "$p" ]; then
      echo "Local ISO not readable: $p" >&2
      exit 1
    fi
    # Resolve to an absolute path; VBoxManage prefers absolute paths.
    abs="$(cd "$(dirname "$p")" && pwd)/$(basename "$p")"
    size="$(wc -c < "$abs" | tr -d ' ')"
    printf '{"path":"%s","size":"%s"}\n' "$abs" "$size"
  EOT
  ]
}
