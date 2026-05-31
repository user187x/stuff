output "vbox_version" {
  description = "Detected VirtualBox version."
  value       = data.external.vbox_check.result.version
}

output "iso_path" {
  description = "Absolute path of the ISO that was attached to the VMs."
  value       = local.resolved_iso_path
}

output "vms" {
  description = "Per-VM identifying info."
  value = [
    for m in module.vm : {
      name = m.name
      uuid = m.uuid
    }
  ]
}
