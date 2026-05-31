output "iso_path" {
  description = "Absolute path to the fetched ISO."
  value       = local.target_path

  # Force consumers to wait until the fetch resource has actually run.
  depends_on = [null_resource.fetch]
}
