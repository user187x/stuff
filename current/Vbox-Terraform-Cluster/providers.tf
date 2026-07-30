terraform {
  required_version = ">= 1.5"

  required_providers {
    # terra-farm/virtualbox is intentionally NOT used.
    #
    # It's alpha, unmaintained since 2021, requires a Vagrant .box image
    # (Talos doesn't publish one), and reports node IPs via VirtualBox
    # Guest Additions which Talos doesn't ship. The previous version of
    # this project used it and hung forever in talos_machine_configuration_apply
    # because every node IP came back as "".
    #
    # We drive VBoxManage directly via null_resource in main.tf instead.

    talos = {
      source  = "siderolabs/talos"
      version = "~> 0.7"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}
