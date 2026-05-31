variable "iso_path" {
  description = "Absolute path to a local .iso file."
  type        = string

  validation {
    condition     = length(var.iso_path) > 0
    error_message = "iso_path must not be empty when using the local image source."
  }
}
