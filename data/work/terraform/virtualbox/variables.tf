###############################################################################
# Image source selection
###############################################################################

variable "image_source" {
  description = <<-EOT
    Where to get the ISO from. One of:
      - "local"  : use a path on this machine (default)
      - "remote" : download/pull from an external source (stubbed module)
  EOT
  type        = string
  default     = "local"

  validation {
    condition     = contains(["local", "remote"], var.image_source)
    error_message = "image_source must be either 'local' or 'remote'."
  }
}

variable "local_iso_path" {
  description = "Absolute path to a local .iso file. Required when image_source = \"local\"."
  type        = string
  default     = ""
}

variable "remote_iso_url" {
  description = "URL to fetch an ISO from. Required when image_source = \"remote\"."
  type        = string
  default     = ""
}

variable "remote_iso_checksum" {
  description = "Optional sha256 checksum for the remote ISO (recommended). Format: \"sha256:<hex>\"."
  type        = string
  default     = ""
}

variable "image_cache_dir" {
  description = "Directory where downloaded/cached ISOs are stored."
  type        = string
  default     = "./.iso_cache"
}

###############################################################################
# VM configuration
###############################################################################

variable "vm_count" {
  description = "Number of identical VMs to create."
  type        = number
  default     = 1

  validation {
    condition     = var.vm_count >= 1 && var.vm_count <= 32
    error_message = "vm_count must be between 1 and 32."
  }
}

variable "vm_name_prefix" {
  description = "Prefix used to name the VMs (final name = \"<prefix>-<n>\")."
  type        = string
  default     = "tf-vbox"
}

variable "vm_ostype" {
  description = "VirtualBox guest OS type (run `VBoxManage list ostypes` for choices)."
  type        = string
  default     = "Ubuntu_64"
}

variable "vm_cpus" {
  description = "vCPUs per VM."
  type        = number
  default     = 2
}

variable "vm_memory_mb" {
  description = "RAM per VM, in MiB."
  type        = number
  default     = 2048
}

variable "vm_disk_mb" {
  description = "Primary disk size per VM, in MiB."
  type        = number
  default     = 20480
}

variable "vm_vram_mb" {
  description = "Video memory per VM, in MiB."
  type        = number
  default     = 16
}

variable "vm_network_mode" {
  description = "Network attachment for nic1: \"nat\", \"bridged\", or \"hostonly\"."
  type        = string
  default     = "nat"

  validation {
    condition     = contains(["nat", "bridged", "hostonly"], var.vm_network_mode)
    error_message = "vm_network_mode must be one of: nat, bridged, hostonly."
  }
}

variable "vm_bridge_adapter" {
  description = "Host adapter name when vm_network_mode = \"bridged\" (e.g. \"en0: Wi-Fi\")."
  type        = string
  default     = ""
}

variable "vm_hostonly_adapter" {
  description = "Host-only adapter name when vm_network_mode = \"hostonly\" (e.g. \"vboxnet0\")."
  type        = string
  default     = ""
}

variable "vm_headless" {
  description = "Start VMs headless (no GUI window)."
  type        = bool
  default     = true
}

variable "vm_base_folder" {
  description = "Optional override for the VirtualBox machine folder. Empty = VBox default."
  type        = string
  default     = ""
}
