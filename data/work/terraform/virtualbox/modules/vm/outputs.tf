output "name" {
  description = "VM name."
  value       = var.name
}

output "uuid" {
  description = "VirtualBox UUID assigned to the VM."
  value       = data.external.vm_info.result.uuid
}
