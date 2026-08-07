terraform {
  required_providers {
    virtualbox = {
      source  = "terra-farm/virtualbox"
      version = "0.2.2-alpha.1"
    }
    talos = {
      source  = "siderolabs/talos"
      version = "~> 0.6.0"
    }
  }
}

variable "cluster_name" {
  type    = string
  default = "talos-local-cluster"
}

resource "talos_machine_secrets" "this" {}
