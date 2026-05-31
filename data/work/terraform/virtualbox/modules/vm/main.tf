###############################################################################
# vm
#
# Creates a single VirtualBox VM from an ISO via VBoxManage. Designed to be
# idempotent (safe to re-apply) and to clean up fully on destroy.
#
# Lifecycle on apply:
#   1. Create VM (--register)
#   2. Configure CPU / RAM / VRAM / boot order / network
#   3. Create a fresh VDI disk
#   4. Add SATA controller + attach disk
#   5. Add IDE controller + attach ISO as DVD
#   6. Start VM (headless or GUI)
#
# Lifecycle on destroy:
#   1. Power off (best-effort; ignored if already off)
#   2. Unregister + delete (removes the VM and its disk files)
#
# Why null_resource + local-exec rather than a "real" provider?
# There is no first-party Terraform VirtualBox provider, and the community
# providers either depend on Vagrant boxes or are sparsely maintained. Driving
# VBoxManage directly is the most reliable way to support raw ISOs and gives
# us full control over the lifecycle.
###############################################################################

locals {
  # Build the network args once; injected into the modifyvm call.
  network_args = (
    var.network_mode == "nat" ? "--nic1 nat" :
    var.network_mode == "bridged" ? "--nic1 bridged --bridgeadapter1 \"${var.bridge_adapter}\"" :
    "--nic1 hostonly --hostonlyadapter1 \"${var.hostonly_adapter}\""
  )

  start_type = var.headless ? "headless" : "gui"

  base_folder_arg = var.base_folder == "" ? "" : "--basefolder \"${var.base_folder}\""
}

# Sanity-check network configuration before doing anything.
resource "terraform_data" "validate_network" {
  input = {
    mode             = var.network_mode
    bridge_adapter   = var.bridge_adapter
    hostonly_adapter = var.hostonly_adapter
  }

  lifecycle {
    precondition {
      condition     = var.network_mode != "bridged" || length(var.bridge_adapter) > 0
      error_message = "bridge_adapter must be set when network_mode = \"bridged\"."
    }
    precondition {
      condition     = var.network_mode != "hostonly" || length(var.hostonly_adapter) > 0
      error_message = "hostonly_adapter must be set when network_mode = \"hostonly\"."
    }
  }
}

###############################################################################
# Create the VM. The destroy-time provisioner tears it down cleanly.
###############################################################################

resource "null_resource" "vm" {
  depends_on = [terraform_data.validate_network]

  # Re-create the VM if any of these change. Anything not in triggers will
  # NOT cause a recreate — adjust to taste.
  triggers = {
    name           = var.name
    ostype         = var.ostype
    cpus           = var.cpus
    memory_mb      = var.memory_mb
    disk_mb        = var.disk_mb
    vram_mb        = var.vram_mb
    iso_path       = var.iso_path
    network_mode   = var.network_mode
    bridge_adapter = var.bridge_adapter
    hostonly_adptr = var.hostonly_adapter
    headless       = var.headless
    base_folder    = var.base_folder
    vbox_version   = var.vbox_version
  }

  # ---------------------------------------------------------------------------
  # CREATE
  # ---------------------------------------------------------------------------
  provisioner "local-exec" {
    interpreter = ["bash", "-c"]
    command     = <<-EOT
      set -euo pipefail

      NAME='${var.name}'
      OSTYPE='${var.ostype}'
      CPUS='${var.cpus}'
      MEM='${var.memory_mb}'
      VRAM='${var.vram_mb}'
      DISK_MB='${var.disk_mb}'
      ISO='${var.iso_path}'

      echo ">>> Creating VM: $NAME"

      # If a VM with this name somehow already exists, refuse loudly. Leaving
      # the user to clean up by hand is safer than silently clobbering state.
      if VBoxManage showvminfo "$NAME" >/dev/null 2>&1; then
        echo "ERROR: VM '$NAME' already exists in VirtualBox." >&2
        echo "       Remove it first with:" >&2
        echo "         VBoxManage controlvm '$NAME' poweroff || true" >&2
        echo "         VBoxManage unregistervm '$NAME' --delete" >&2
        exit 1
      fi

      # 1. Register the VM.
      VBoxManage createvm \
        --name "$NAME" \
        --ostype "$OSTYPE" \
        ${local.base_folder_arg} \
        --register

      # 2. Resolve the actual machine folder so we know where to put the VDI.
      MACHINE_FOLDER="$(VBoxManage showvminfo "$NAME" --machinereadable \
                        | awk -F= '/^CfgFile=/ {gsub(/"/, "", $2); print $2}' \
                        | xargs dirname)"
      DISK_PATH="$MACHINE_FOLDER/$NAME.vdi"

      # 3. Core hardware config.
      VBoxManage modifyvm "$NAME" \
        --cpus "$CPUS" \
        --memory "$MEM" \
        --vram "$VRAM" \
        --boot1 dvd --boot2 disk --boot3 none --boot4 none \
        --rtcuseutc on \
        --acpi on --ioapic on \
        ${local.network_args}

      # 4. Create primary disk.
      VBoxManage createmedium disk \
        --filename "$DISK_PATH" \
        --size "$DISK_MB" \
        --format VDI

      # 5. SATA controller + attach disk.
      VBoxManage storagectl "$NAME" \
        --name "SATA" \
        --add sata --controller IntelAhci --portcount 2

      VBoxManage storageattach "$NAME" \
        --storagectl "SATA" \
        --port 0 --device 0 \
        --type hdd --medium "$DISK_PATH"

      # 6. IDE controller + attach the ISO as a DVD.
      VBoxManage storagectl "$NAME" \
        --name "IDE" \
        --add ide --controller PIIX4

      VBoxManage storageattach "$NAME" \
        --storagectl "IDE" \
        --port 0 --device 0 \
        --type dvddrive --medium "$ISO"

      # 7. Start it.
      VBoxManage startvm "$NAME" --type ${local.start_type}

      # Print UUID so the post-create data lookup has something to read.
      VBoxManage showvminfo "$NAME" --machinereadable \
        | awk -F= '/^UUID=/ {gsub(/"/, "", $2); print $2; exit}'
    EOT
  }

  # ---------------------------------------------------------------------------
  # DESTROY
  #
  # `self.triggers.name` is the only thing referenced — destroy provisioners
  # cannot reference variables directly, only `self`.
  # ---------------------------------------------------------------------------
  provisioner "local-exec" {
    when        = destroy
    interpreter = ["bash", "-c"]
    command     = <<-EOT
      set -uo pipefail

      NAME='${self.triggers.name}'
      echo "<<< Destroying VM: $NAME"

      if ! VBoxManage showvminfo "$NAME" >/dev/null 2>&1; then
        echo "VM '$NAME' is not registered; nothing to do."
        exit 0
      fi

      # Best-effort poweroff — fine if it's already off.
      VBoxManage controlvm "$NAME" poweroff >/dev/null 2>&1 || true

      # Wait briefly for the VM to actually be stopped before unregistering.
      for i in 1 2 3 4 5; do
        STATE="$(VBoxManage showvminfo "$NAME" --machinereadable 2>/dev/null \
                 | awk -F= '/^VMState=/ {gsub(/"/, "", $2); print $2}')"
        if [ "$STATE" != "running" ] && [ "$STATE" != "stopping" ]; then
          break
        fi
        sleep 1
      done

      # --delete also deletes the disk files attached to the VM.
      VBoxManage unregistervm "$NAME" --delete
      echo "VM '$NAME' destroyed."
    EOT
  }
}

###############################################################################
# Read the VM's UUID after creation so we can expose it as an output.
###############################################################################

data "external" "vm_info" {
  depends_on = [null_resource.vm]

  program = ["bash", "-c", <<-EOT
    set -euo pipefail
    NAME='${var.name}'
    UUID="$(VBoxManage showvminfo "$NAME" --machinereadable \
             | awk -F= '/^UUID=/ {gsub(/"/, "", $2); print $2; exit}')"
    printf '{"uuid":"%s","name":"%s"}\n' "$UUID" "$NAME"
  EOT
  ]
}
