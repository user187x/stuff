variable "iso_url" {
  description = "URL to fetch the ISO from. Supports any scheme curl understands (https, http, ftp, file)."
  type        = string

  validation {
    condition     = length(var.iso_url) > 0
    error_message = "iso_url must not be empty when using the remote image source."
  }
}

variable "iso_checksum" {
  description = <<-EOT
    Optional checksum to verify after download. Format: "sha256:<hex>".
    Strongly recommended for any production-ish use.
    Leave empty to skip verification.
  EOT
  type    = string
  default = ""

  validation {
    condition     = var.iso_checksum == "" || can(regex("^sha256:[0-9a-fA-F]{64}$", var.iso_checksum))
    error_message = "iso_checksum must be empty or match \"sha256:<64 hex chars>\"."
  }
}

variable "cache_dir" {
  description = "Directory used to cache downloaded ISOs. Will be created if missing."
  type        = string
  default     = "./.iso_cache"
}

variable "filename_override" {
  description = "Optional filename for the cached ISO. If empty, derived from the URL."
  type        = string
  default     = ""
}
