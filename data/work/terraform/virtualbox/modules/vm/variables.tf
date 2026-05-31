variable "name" {
  description = "VM name as it will appear in VirtualBox."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+$", var.name))
    error_message = "VM name must contain only letters, digits, dot, underscore, or hyphen."
  }
}

variable "ostype" {
  description = "VirtualBox ostype identifier (e.g. \"Ubuntu_64\", \"Debian_64\", \"Windows10_64\")."
  type        = string
  default     = "Ubuntu_64"
}

variable "cpus" {
  description = "vCPUs for the VM."
  type        = number
  default     = 2
}

variable "memory_mb" {
  description = "RAM in MiB."
  type        = number
  default     = 2048
}

variable "disk_mb" {
  description = "Primary disk size in MiB."
  type        = number
  default     = 20480
}

variable "vram_mb" {
  description = "Video memory in MiB."
  type        = number
  default     = 16
}

variable "iso_path" {
  description = "Absolute path to the ISO that will be attached as a DVD drive."
  type        = string
}

variable "network_mode" {
  description = "Network mode: \"nat\", \"bridged\", or \"hostonly\"."
  type        = string
  default     = "nat"
}

variable "bridge_adapter" {
  description = "Host adapter for bridged mode (e.g. \"en0: Wi-Fi\"). Required if network_mode = \"bridged\"."
  type        = string
  default     = ""
}

variable "hostonly_adapter" {
  description = "Host-only adapter for hostonly mode (e.g. \"vboxnet0\"). Required if network_mode = \"hostonly\"."
  type        = string
  default     = ""
}

variable "headless" {
  description = "If true, start the VM in headless mode."
  type        = bool
  default     = true
}

variable "base_folder" {
  description = "Optional override for the VirtualBox machine folder."
  type        = string
  default     = ""
}

variable "vbox_version" {
  description = "Detected VirtualBox version, used solely to express a dependency on the preflight check."
  type        = string
  default     = ""
}
