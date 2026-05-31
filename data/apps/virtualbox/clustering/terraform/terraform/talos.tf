data "talos_machine_configuration" "cp" {
  cluster_name     = var.cluster_name
  cluster_endpoint = "https://${virtualbox_vm.controlplane[0].network_adapter[1].ipv4_address}:6443"
  machine_type     = "controlplane"
  machine_secrets  = talos_machine_secrets.this.machine_secrets
}

resource "talos_machine_configuration_apply" "cp" {
  client_configuration        = talos_machine_secrets.this.client_configuration
  machine_configuration_input = data.talos_machine_configuration.cp.machine_configuration
  node                        = virtualbox_vm.controlplane[0].network_adapter[1].ipv4_address
}

data "talos_machine_configuration" "worker" {
  cluster_name     = var.cluster_name
  cluster_endpoint = "https://${virtualbox_vm.controlplane[0].network_adapter[1].ipv4_address}:6443"
  machine_type     = "worker"
  machine_secrets  = talos_machine_secrets.this.machine_secrets
}

resource "talos_machine_configuration_apply" "worker" {
  client_configuration        = talos_machine_secrets.this.client_configuration
  machine_configuration_input = data.talos_machine_configuration.worker.machine_configuration
  node                        = virtualbox_vm.worker[0].network_adapter[1].ipv4_address
}

resource "talos_cluster_kubeconfig" "this" {
  depends_on = [
    talos_machine_configuration_apply.cp,
    talos_machine_configuration_apply.worker
  ]
  client_configuration = talos_machine_secrets.this.client_configuration
  node                 = virtualbox_vm.controlplane[0].network_adapter[1].ipv4_address
}

output "kubeconfig" {
  value     = talos_cluster_kubeconfig.this.kubeconfig_raw
  sensitive = true
}
