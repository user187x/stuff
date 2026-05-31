# terraform-virtualbox

Terraform that spins up local VirtualBox VMs from an ISO image. Image source
is **pluggable**: today there's a `local` source (path on disk) and a
**stubbed-out** `remote` source (downloads via curl) that you can replace with
anything — S3, GCS, OCI, an internal artifact store, etc.

## Why this design

There is no first-party Terraform provider for VirtualBox, and the community
ones either rely on Vagrant boxes or are sparsely maintained. To keep this
robust and ISO-native, the `vm` module drives `VBoxManage` directly via
`null_resource` + `local-exec`, with a paired destroy provisioner so
`terraform destroy` actually unregisters and deletes the VM and its disks.

## Layout

```
.
├── main.tf                       # wires modules together
├── variables.tf                  # top-level inputs
├── outputs.tf
├── versions.tf
├── terraform.tfvars.example
└── modules/
    ├── vm/                       # creates one VirtualBox VM
    ├── image_local/              # source: ISO already on disk
    └── image_remote/             # source: ISO fetched from a URL (STUB)
```

Both image modules expose the same output: `iso_path`. The root module picks
one based on `var.image_source`, so swapping is a one-line change.

## Prerequisites

- VirtualBox 6.1+ installed; `VBoxManage` on `PATH`
- Terraform 1.3+
- `bash`, `curl`, `sha256sum`, `awk` (standard on macOS/Linux; on Windows use WSL)

The root module runs a preflight check on `VBoxManage` at plan time and aborts
with a clear message if it isn't found.

## Quick start (local ISO)

```bash
cp terraform.tfvars.example terraform.tfvars
# edit local_iso_path to point at your .iso
terraform init
terraform apply
```

## Quick start (remote ISO)

```hcl
# terraform.tfvars
image_source        = "remote"
remote_iso_url      = "https://releases.ubuntu.com/24.04/ubuntu-24.04-live-server-amd64.iso"
remote_iso_checksum = "sha256:<the published hash>"
```

The remote module caches under `./.iso_cache/` and skips redownloads when the
checksum matches.

## Swapping the remote fetcher

`modules/image_remote/main.tf` is intentionally a stub. The fetch logic lives
in a single `null_resource.fetch` block, between the
`# ---- BEGIN: replaceable fetch implementation ----` and `# ---- END ... ----`
markers. Replace those lines with whatever you want — `aws s3 cp`,
`gsutil cp`, `oras pull`, an internal CLI — as long as the file ends up at
`local.target_path`. Nothing else needs to change.

If you want a third source (say, `image_s3`), copy `modules/image_remote/`,
implement it, and register it in the root `main.tf` alongside the existing
`module "image_remote"`. Add a new value to `var.image_source`'s validation
and update `local.resolved_iso_path`.

## What gets created per VM

- A registered VirtualBox VM with the chosen ostype
- A SATA controller with a fresh VDI disk attached
- An IDE controller with the ISO attached as DVD drive
- Boot order: DVD first, disk second
- One NIC in NAT, bridged, or host-only mode
- Headless start by default

## What gets destroyed on `terraform destroy`

- VM is powered off (best-effort, ignored if already off)
- VM is unregistered with `--delete`, which also removes the VDI

The remote ISO cache is **not** deleted by default — uncomment the destroy
provisioner in `modules/image_remote/main.tf` if you want strict teardown.

## Inputs (root)

| Name | Default | Notes |
|---|---|---|
| `image_source` | `"local"` | `"local"` or `"remote"` |
| `local_iso_path` | `""` | required when `image_source = "local"` |
| `remote_iso_url` | `""` | required when `image_source = "remote"` |
| `remote_iso_checksum` | `""` | `sha256:<hex>` — strongly recommended |
| `image_cache_dir` | `"./.iso_cache"` | where remote ISOs are cached |
| `vm_count` | `1` | 1–32 |
| `vm_name_prefix` | `"tf-vbox"` | final name = `<prefix>-NN` |
| `vm_ostype` | `"Ubuntu_64"` | run `VBoxManage list ostypes` for choices |
| `vm_cpus` | `2` | |
| `vm_memory_mb` | `2048` | |
| `vm_disk_mb` | `20480` | |
| `vm_vram_mb` | `16` | |
| `vm_network_mode` | `"nat"` | `nat` / `bridged` / `hostonly` |
| `vm_bridge_adapter` | `""` | required for `bridged` |
| `vm_hostonly_adapter` | `""` | required for `hostonly` |
| `vm_headless` | `true` | |
| `vm_base_folder` | `""` | override VirtualBox machine folder |

## Outputs

- `vbox_version` — detected VirtualBox version
- `iso_path` — absolute path of the ISO that was attached
- `vms` — list of `{ name, uuid }` per VM

## Caveats

- VirtualBox is not concurrency-safe across multiple Terraform runs against
  the same set of VM names. Don't `apply` two stacks that share names.
- Bridged networking on macOS may require granting VirtualBox permission in
  System Settings → Privacy & Security.
- If a previous run left a VM behind, the create step refuses to clobber it.
  Clean up by hand:
  ```bash
  VBoxManage controlvm <name> poweroff || true
  VBoxManage unregistervm <name> --delete
  ```
