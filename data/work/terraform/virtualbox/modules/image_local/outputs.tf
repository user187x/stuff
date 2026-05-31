output "iso_path" {
  description = "Absolute path to the local ISO."
  value       = data.external.iso_check.result.path
}

output "iso_size_bytes" {
  description = "Size of the ISO in bytes."
  value       = tonumber(data.external.iso_check.result.size)
}
