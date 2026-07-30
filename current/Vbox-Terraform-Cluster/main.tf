# One VM per node, driven through VBoxManage. We can't use the
# terra-farm/virtualbox provider because:
#   * it requires a Vagrant .box archive (Talos doesn't publish one)
#   * it reads node IPs through Guest Additions (Talos doesn't ship them),
#     so its ipv4_address attribute is always "", which causes every
#     downstream Talos resource to hang forever trying to dial nothing.
#
# Each VM gets:
#   * NIC1: hostonly on the named iface, with a known MAC. The
#     terraform-apply script registers a DHCP reservation for that MAC ->
#     IP pair before this resource runs, so the node IP is deterministic
#     and known at plan time.
#   * NIC2: NAT, for outbound internet (image pulls).
#   * SATA disk on IntelAhci (-> /dev/sda inside the VM; pinned in the
#     Talos machine config so installs go to the right disk).
#   * DVD with the Talos metal ISO; boot order is disk-then-DVD so the
#     installed system takes over after first install.

resource "null_resource" "vm" {
  for_each = { for n in var.nodes : n.name => n }

  triggers = {
    name       = each.value.name
    mac        = each.value.mac
    ip         = each.value.ip
    iso        = var.iso_path
    basefolder = var.vms_basefolder
    iface      = var.hostonly_iface
    memory_mb  = var.vm_memory_mb
    cpus       = var.vm_cpus
    disk_mb    = var.vm_disk_mb
  }

  provisioner "local-exec" {
    when        = create
    interpreter = ["/bin/bash", "-c"]
    command     = <<-BASH
      set -euo pipefail

      NAME="${each.value.name}"
      MAC_NOCOLONS=$(echo "${each.value.mac}" | tr -d ':')
      IFACE="${var.hostonly_iface}"
      ISO="${var.iso_path}"
      BASEFOLDER="${var.vms_basefolder}"
      CPUS="${var.vm_cpus}"
      MEM="${var.vm_memory_mb}"
      DISK_MB="${var.vm_disk_mb}"

      # Idempotency: nuke any stale VM with this name first. Re-running
      # `terraform apply` after a partial failure should Just Work.
      if VBoxManage showvminfo "$NAME" >/dev/null 2>&1; then
        VBoxManage controlvm "$NAME" poweroff >/dev/null 2>&1 || true
        sleep 1
        VBoxManage unregistervm "$NAME" --delete >/dev/null 2>&1 || true
      fi

      # --basefolder pins the VM directory inside our cluster dir instead
      # of VirtualBox's global default machine folder, which can be
      # missing, owned by root from a prior sudo, or otherwise unwritable.
      VBoxManage createvm --name "$NAME" --ostype "Linux_64" \
        --basefolder "$BASEFOLDER" --register >/dev/null

      VBoxManage modifyvm "$NAME" \
        --memory "$MEM" --cpus "$CPUS" \
        --boot1 disk --boot2 dvd --boot3 none --boot4 none \
        --firmware bios --rtcuseutc on \
        --graphicscontroller vmsvga --vram 16 --audio-driver none \
        --nic1 hostonly --hostonlyadapter1 "$IFACE" --nictype1 virtio \
        --macaddress1 "$MAC_NOCOLONS" \
        --nic2 nat --nictype2 virtio >/dev/null

      VM_DIR=$(VBoxManage showvminfo "$NAME" --machinereadable \
                | awk -F= '/^CfgFile=/{gsub(/"/, "", $2); print $2}' \
                | xargs dirname)

#      DISK="$VM_DIR/$NAME.vdi"
#     VBoxManage createmedium disk --filename "$DISK" --size "$DISK_MB" --format VDI >/dev/null
#     VBoxManage storagectl   "$NAME" --name "SATA" --add sata --controller IntelAhci --portcount 2 >/dev/null
#     VBoxManage storageattach "$NAME" --storagectl "SATA" --port 0 --device 0 --type hdd      --medium "$DISK" >/dev/null
#     VBoxManage storageattach "$NAME" --storagectl "SATA" --port 1 --device 0 --type dvddrive --medium "$ISO"  >/dev/null

      DISK="$VM_DIR/$NAME.vdi"
      VBoxManage createmedium disk --filename "$DISK" --size "$DISK_MB" --format VDI >/dev/null
      
      # 1. Add SATA Controller ONLY for the hard disk
      VBoxManage storagectl   "$NAME" --name "SATA" --add sata --controller IntelAhci --portcount 1 >/dev/null
      VBoxManage storageattach "$NAME" --storagectl "SATA" --port 0 --device 0 --type hdd --medium "$DISK" >/dev/null
      
      # 2. Add IDE Controller ONLY for the Talos Installation ISO
      VBoxManage storagectl   "$NAME" --name "IDE" --add ide >/dev/null
      VBoxManage storageattach "$NAME" --storagectl "IDE" --port 0 --device 0 --type dvddrive --medium "$ISO" >/dev/null


      VBoxManage startvm "$NAME" --type headless >/dev/null
      echo "[vm] started $NAME (mac=${each.value.mac}, ip=${each.value.ip})"
    BASH
  }

  provisioner "local-exec" {
    when        = destroy
    on_failure  = continue
    interpreter = ["/bin/bash", "-c"]
    command     = <<-BASH
      NAME="${self.triggers.name}"
      VBoxManage controlvm "$NAME" poweroff >/dev/null 2>&1 || true
      sleep 1
      VBoxManage unregistervm "$NAME" --delete >/dev/null 2>&1 || true
      echo "[vm] destroyed $NAME"
    BASH
  }
}

# Gate: wait for the Talos API (50000/tcp) on every node before any
# talos_machine_configuration_apply runs. Talos boots into maintenance
# mode from the ISO and brings up the API before any config is applied,
# so this is the right signal that the node is reachable. Without this,
# the Talos provider can race the boot and fail with "connection refused".
resource "null_resource" "wait_for_talos_api" {
  for_each   = { for n in var.nodes : n.name => n }
  depends_on = [null_resource.vm]

  triggers = { ip = each.value.ip }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-BASH
      set -e
      IP="${each.value.ip}"
      echo "[wait] $IP:50000 ..."
      for i in $(seq 1 180); do
        if timeout 2 bash -c "echo > /dev/tcp/$IP/50000" >/dev/null 2>&1; then
          echo "[wait] $IP up after $i tries"
          exit 0
        fi
        sleep 5
      done
      echo "[wait] timed out waiting for $IP:50000" >&2
      exit 1
    BASH
  }
}
