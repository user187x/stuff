# install-coder

A modular installer for self-hosted Coder on Minikube. This is a refactor of the
original single-file script: the orchestrator stays small while every step lives
in its own module under `lib/`, and every Kubernetes resource lives as a real
file under `manifests/`.

## Layout

```
install-coder                       # thin orchestrator — sources libs and dispatches
filebrowser                         # air-gap binary — placed alongside the script
lib/
  common.sh                         # defaults, logging, arg parsing, ERR trap
  preflight.sh                      # required-tool + driver checks
  minikube.sh                       # cluster bring-up
  certs.sh                          # Root CA + server cert
  secrets.sh                        # namespace + TLS/CA/PG secrets
  postgres.sh                       # apply manifests/postgres.yaml + DB-URL secret
  coder.sh                          # render Helm values + helm upgrade --install
  ingress.sh                        # detect/install controller + apply ingress template
  hosts.sh                          # /etc/hosts entry
  health.sh                         # /healthz probe
  ca-trust.sh                       # OS + browser CA trust
  cli.sh                            # download Coder CLI from its own server
  admin.sh                          # create first-user via CLI
  filebrowser.sh                    # air-gap filebrowser binary host (kubectl cp)
  summary.sh                        # final printout
  status.sh                         # --status implementation
  uninstall.sh                      # --uninstall implementation
manifests/
  server-ext.cnf.tpl                # OpenSSL ext config (was inline heredoc)
  postgres.yaml                     # PG PVC + Deployment + Service
  coder-values.yaml.tpl             # Helm values (rendered to $VALUES_FILE)
  coder-ingress-nginx.yaml.tpl      # Ingress when reusing ingress-nginx
  coder-ingress-traefik.yaml.tpl    # Ingress when installing Traefik
  filebrowser-host.yaml.tpl         # in-cluster HTTP host for filebrowser binary
```

## Variable paths

Every external resource is reached via a variable with a sensible default.
Override any of these to point at a different location:

| Variable             | Default                       | Purpose                                       |
| -------------------- | ----------------------------- | --------------------------------------------- |
| `LIB_DIR`            | `<script dir>/lib`            | shell library modules                         |
| `MANIFESTS_DIR`      | `<script dir>/manifests`      | Kubernetes manifests / templates              |
| `FILEBROWSER_BINARY` | `<script dir>/filebrowser`    | the filebrowser binary for air-gap workspaces |
| `CERT_DIR`           | `$PWD/certs`                  | where Root CA + server certs live             |
| `VALUES_FILE`        | `$PWD/coder-values.yaml`      | rendered Helm values                          |

Templates (`*.tpl`) are rendered by `envsubst` with a fixed allowlist of
variables (`$DOMAIN`, `$NAMESPACE`, `$SAFE_NAME`, `$TLS_SECRET`, `$CA_SECRET`,
`$FILEBROWSER_HOST_IMAGE`). Other `$...` references inside a template are
left untouched, so Helm syntax or shell snippets in init scripts won't get
clobbered.

## Air-gap notes

The original script downloaded the filebrowser binary from the internet at
workspace-creation time. That doesn't work in an air-gapped cluster, so the
installer ships its own copy and hosts it inside the cluster.

**Step 1 — Place the binary.** Drop the `filebrowser` binary in the same
directory as `install-coder` (or pass `--filebrowser-binary /some/path`).

**Step 2 — Pre-load the host image.** The host pod runs `busybox:1.36` by
default. In a fully air-gapped cluster, either pre-load it:

```bash
minikube image load busybox:1.36 -p minikube
```

…or override `FILEBROWSER_HOST_IMAGE` to a name your private registry
already has.

**Step 3 — Run the installer.** It applies `manifests/filebrowser-host.yaml.tpl`
and `kubectl cp`s the binary into `/www/filebrowser` inside the pod. Re-runs
are no-ops when the in-pod size matches the local size.

**Step 4 — Reference it from your workspace template.** Replace whatever
filebrowser module/install-script you had with a `wget` against cluster DNS:

```bash
wget -qO /tmp/filebrowser \
    http://filebrowser-host.coder.svc.cluster.local/filebrowser
chmod +x /tmp/filebrowser
/tmp/filebrowser --noauth --address 0.0.0.0 --port 13339 --root /home/coder
```

Plain HTTP is fine here because traffic never leaves the cluster.

**Other air-gap concerns.** This refactor doesn't change how `helm` and the
container images are fetched (still `helm.coder.com`, `traefik.github.io`,
`postgres:16-alpine`, etc.). For a fully offline install you'll also need to:

- Mirror the Coder + Traefik Helm charts to a local repo (or use
  `helm install <release> /path/to/chart-x.y.z.tgz`).
- Pre-load `postgres:16-alpine` and the Coder image into the cluster's image
  cache, the same way you handled `busybox`.

## Extending

- **Add a step:** drop a new file in `lib/`, source it from `install-coder`,
  and call its function in the dispatch block. Each module should expose one
  top-level function.
- **Tweak a resource:** edit the YAML directly under `manifests/`. No shell
  changes needed unless you're introducing a new template variable (in which
  case add it to the allowlist in `lib/common.sh`'s `render_template`).
- **Run a single step:** source `lib/common.sh` plus the step you want, call
  `parse_args "$@"`, then call the function. Useful for debugging.
