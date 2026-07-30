# All inputs are populated by terraform.tfvars.json, which the
# `terraform-apply` script renders just before calling `terraform apply`.
# The script is what computes deterministic IPs and MACs from CP_COUNT /
# WORKER_COUNT and decides on disk size, base folder, ISO path, etc.

variable "cluster_name" {
  description = "Talos cluster name."
  type        = string
  default     = "talos-local"
}

variable "cluster_endpoint" {
  description = "Kubernetes API endpoint. Single-CP setups: https://<first_cp_ip>:6443."
  type        = string
}

variable "talos_version" {
  description = "Talos release tag, e.g. v1.8.3. Must match the ISO that was downloaded."
  type        = string
}

variable "iso_path" {
  description = "Absolute path to the Talos metal-amd64.iso on the host."
  type        = string
}

variable "vms_basefolder" {
  description = "Absolute path under which VirtualBox will store VM directories."
  type        = string
}

variable "hostonly_iface" {
  description = "Name of the VirtualBox host-only interface (e.g. vboxnet0)."
  type        = string
}

variable "vm_memory_mb" {
  description = "Memory per VM in MiB."
  type        = number
  default     = 2048
}

variable "vm_cpus" {
  description = "vCPUs per VM."
  type        = number
  default     = 2
}

variable "vm_disk_mb" {
  description = "Disk size per VM in MiB."
  type        = number
  default     = 12288
}

variable "cp_ips" {
  description = "Deterministic IPs assigned to control-plane nodes via DHCP reservations."
  type        = list(string)
}

variable "worker_ips" {
  description = "Deterministic IPs assigned to worker nodes via DHCP reservations."
  type        = list(string)
}

variable "nodes" {
  description = "Full node table: name, MAC, IP, role per VM."
  type = list(object({
    name = string
    mac  = string
    ip   = string
    role = string
  }))
}
