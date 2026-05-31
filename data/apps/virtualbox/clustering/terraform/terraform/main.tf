resource "virtualbox_vm" "controlplane" {
  count  = 1
  name   = "talos-cp-${count.index}"
  image  = "./talos-virtualbox.tar.gz"
  cpus   = 2
  memory = "2.0 gib"

  network_adapter {
    type = "nat"
  }

  network_adapter {
    type           = "hostonly"
    host_interface = "vboxnet0"
  }
}

resource "virtualbox_vm" "worker" {
  count  = 1
  name   = "talos-worker-${count.index}"
  image  = "./talos-virtualbox.tar.gz"
  cpus   = 2
  memory = "2.0 gib"

  network_adapter {
    type = "nat"
  }

  network_adapter {
    type           = "hostonly"
    host_interface = "vboxnet0"
  }
}
