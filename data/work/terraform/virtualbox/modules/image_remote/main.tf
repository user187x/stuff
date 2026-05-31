###############################################################################
# image_remote (STUB)
#
# Drop-in replacement for image_local. Same output contract:
#   - iso_path : absolute path on the host filesystem (after fetching)
#
# Today this stub uses curl + optional sha256 verification. It is intentionally
# minimal so you can swap in something more sophisticated:
#
#   - S3 / GCS / Azure Blob via their CLIs
#   - HashiCorp's go-getter (e.g. via a Packer wrapper)
#   - An OCI artifact registry
#   - A signed URL service inside your network
#
# To extend: replace the body of the null_resource.fetch.local-exec block.
# As long as the file ends up at local.target_path, nothing else needs to
# change — the rest of the stack consumes `iso_path` blindly.
###############################################################################

locals {
  # Derive a filename from the URL unless overridden.
  derived_filename = (
    var.filename_override != ""
    ? var.filename_override
    : basename(var.iso_url)
  )

  cache_dir_abs = abspath(var.cache_dir)
  target_path   = "${local.cache_dir_abs}/${local.derived_filename}"
}

# Make sure the cache directory exists before we try to write into it.
resource "null_resource" "ensure_cache_dir" {
  triggers = {
    cache_dir = local.cache_dir_abs
  }

  provisioner "local-exec" {
    interpreter = ["bash", "-c"]
    command     = "mkdir -p \"${local.cache_dir_abs}\""
  }
}

# Fetch + verify. Re-runs whenever the URL or checksum changes.
resource "null_resource" "fetch" {
  depends_on = [null_resource.ensure_cache_dir]

  triggers = {
    url         = var.iso_url
    checksum    = var.iso_checksum
    target_path = local.target_path
  }

  provisioner "local-exec" {
    interpreter = ["bash", "-c"]
    command     = <<-EOT
      set -euo pipefail

      URL='${var.iso_url}'
      DEST='${local.target_path}'
      CHECKSUM='${var.iso_checksum}'

      # ---- BEGIN: replaceable fetch implementation -----------------------
      # Skip the download if the file is already there AND the checksum
      # (when provided) matches. This makes the resource effectively cached.
      if [ -f "$DEST" ] && [ -n "$CHECKSUM" ]; then
        expected="$${CHECKSUM#sha256:}"
        actual="$(sha256sum "$DEST" | awk '{print $1}')"
        if [ "$expected" = "$actual" ]; then
          echo "Cache hit (checksum match): $DEST"
          exit 0
        fi
        echo "Cache present but checksum mismatch — re-downloading."
        rm -f "$DEST"
      elif [ -f "$DEST" ] && [ -z "$CHECKSUM" ]; then
        echo "Cache hit (no checksum to verify): $DEST"
        exit 0
      fi

      echo "Downloading $URL -> $DEST"
      # -fL: fail on HTTP errors, follow redirects.
      # -C -: resume if partially downloaded.
      # --retry: be tolerant of flaky networks.
      curl -fL --retry 3 --retry-delay 2 -C - -o "$DEST.part" "$URL"
      mv "$DEST.part" "$DEST"
      # ---- END: replaceable fetch implementation -------------------------

      # Verify checksum if provided.
      if [ -n "$CHECKSUM" ]; then
        expected="$${CHECKSUM#sha256:}"
        actual="$(sha256sum "$DEST" | awk '{print $1}')"
        if [ "$expected" != "$actual" ]; then
          echo "Checksum mismatch on $DEST" >&2
          echo "  expected: $expected" >&2
          echo "  actual:   $actual" >&2
          rm -f "$DEST"
          exit 1
        fi
        echo "Checksum verified."
      else
        echo "WARNING: no checksum provided; skipping verification."
      fi
    EOT
  }

  # On destroy, optionally remove the cached file. Commented out by default
  # because the cache is usually worth keeping. Uncomment if you want strict
  # teardown semantics.
  #
  # provisioner "local-exec" {
  #   when        = destroy
  #   interpreter = ["bash", "-c"]
  #   command     = "rm -f '${self.triggers.target_path}'"
  # }
}
