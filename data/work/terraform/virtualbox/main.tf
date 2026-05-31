###############################################################################
# Pre-flight: fail fast if VBoxManage is not on PATH.
# This runs at plan/apply time and stops the world before any VM work begins.
###############################################################################

data "external" "vbox_check" {
  # Using an inline shell program. If VBoxManage is missing or broken, this
  # exits non-zero and Terraform aborts with a clear message.
  program = ["bash", "-c", <<-EOT
    set -euo pipefail
    if ! command -v VBoxManage >/dev/null 2>&1; then
      echo "VBoxManage not found on PATH. Install VirtualBox and ensure VBoxManage is reachable." >&2
      exit 1
    fi
    version="$(VBoxManage --version)"
    printf '{"version":"%s"}\n' "$version"
  EOT
  ]
}

###############################################################################
# Image source — exactly one of these is "active" based on var.image_source.
# Both modules expose the same output: `iso_path` (absolute path on disk).
# This is the swap point if you ever want a different fetcher.
###############################################################################

module "image_local" {
  source = "./modules/image_local"
  count  = var.image_source == "local" ? 1 : 0

  iso_path = var.local_iso_path
}

module "image_remote" {
  source = "./modules/image_remote"
  count  = var.image_source == "remote" ? 1 : 0

  iso_url       = var.remote_iso_url
  iso_checksum  = var.remote_iso_checksum
  cache_dir     = var.image_cache_dir
}

locals {
  # Resolve the active image module's iso_path into a single value.
  resolved_iso_path = (
    var.image_source == "local"
    ? one(module.image_local[*].iso_path)
    : one(module.image_remote[*].iso_path)
  )
}

###############################################################################
# VMs
###############################################################################

module "vm" {
  source = "./modules/vm"
  count  = var.vm_count

  name             = format("%s-%02d", var.vm_name_prefix, count.index + 1)
  ostype           = var.vm_ostype
  cpus             = var.vm_cpus
  memory_mb        = var.vm_memory_mb
  disk_mb          = var.vm_disk_mb
  vram_mb          = var.vm_vram_mb
  iso_path         = local.resolved_iso_path
  network_mode     = var.vm_network_mode
  bridge_adapter   = var.vm_bridge_adapter
  hostonly_adapter = var.vm_hostonly_adapter
  headless         = var.vm_headless
  base_folder      = var.vm_base_folder

  # Force the preflight check to run before any VM is created. The reference
  # is to a data source attribute, which establishes a real dependency.
  vbox_version = data.external.vbox_check.result.version
}
