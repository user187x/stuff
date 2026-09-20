# Coder banner: air-gapped install, use and removal

An editable announcement banner for the Coder dashboard, plus an admin page and a "Banner" shortcut in Coder's
user menu. It is built only from the objects your cluster already uses for Coder: the `traefik-gateway`
Gateway, Gateway API `HTTPRoute`s and Traefik `Middleware`s. With `--airgap` nothing is downloaded at run time.

**You need:** `kubectl`, `helm` (3.14+ or 4), `jq` on the machine you work from, cluster-admin rights, an
internal container registry, and (for the Argo CD route) an internal Helm chart repository.

## What gets installed, and what is never touched

| The chart adds (namespace of Coder)                                   | The chart never modifies                         |
| --------------------------------------------------------------------- | ------------------------------------------------ |
| `HTTPRoute/coder-banner` (beside Coder's own route, same host)        | Coder's Deployment, Service, database, secrets   |
| 6 Traefik `Middleware`s (`coder-banner*`)                             | Coder's own `HTTPRoute`                          |
| `Deployment` + `Service` `coder-banner` (small Python web server)     | The `traefik-gateway` Gateway                    |
| 3 ConfigMaps, 1 ServiceAccount, 1 Role + RoleBinding (one ConfigMap)  | Any other namespace                              |

One thing lives **outside** the chart because Traefik reads it at start-up: the `rewrite-body` plugin has to
be enabled in Traefik's static configuration (`support/traefik/enable-plugin`). It is reversible
(`disable-plugin`).

If a prerequisite is missing (plugin not loaded, image not pullable) Coder keeps working: Traefik drops only the
banner's routers and requests fall through to Coder's own route (tested with a missing plugin and a missing
Middleware).

**Enabling or disabling the plugin restarts Traefik.** The new pod becomes Ready before the old one stops, but with a
single Traefik replica expect every site behind it (Coder, Argo CD, ...) to be unreachable for a few seconds while
traffic moves over. Do it in a quiet window. Installing or removing the chart itself never restarts Traefik.

## Part 1 - on a connected machine: build the bundle

```bash
./make-airgap-bundle --with-traefik-chart
```

Produces `dist/coder-banner-airgap-<version>.tar.gz` (+ `.sha256`): the chart archive, the container image
(`images/images.tar`), the vendored plugin source (Apache-2.0, upstream `traefik/plugin-rewritebody` v0.3.1), all
scripts, this document and the Argo CD Application. Carry it across (USB / data diode / transfer host).

## Part 2 - on the air-gapped side

### 1. Unpack and verify
```bash
sha256sum -c coder-banner-airgap-*.tar.gz.sha256
tar xzf coder-banner-airgap-*.tar.gz && cd coder-banner-airgap-*/
sha256sum -c SHA256SUMS --quiet && echo "bundle intact"
```

### 2. Put the image in your registry
```bash
docker load -i images/images.tar                       # prints the loaded image name, e.g. python:3.13-alpine
docker tag  python:3.13-alpine registry.internal:5000/library/python:3.13-alpine
docker push registry.internal:5000/library/python:3.13-alpine
# skopeo alternative:  skopeo copy docker-archive:images/images.tar:python:3.13-alpine docker://registry.internal:5000/library/python:3.13-alpine
```
Any image that provides `python3` (3.8+) works; set it with `image.registry/repository/tag` (and
`imagePullSecrets` if your registry needs credentials).

### 3. Check the cluster (read-only)
```bash
CODER_HOST=coder.example.com BANNER_IMAGE=registry.internal:5000/library/python:3.13-alpine ./preflight-banner
```
Fix every `FAIL`. Defaults assume namespace `coder`, Service `coder`, Gateway `traefik/traefik-gateway`; override with
the environment variables listed at the top of the script.

### 4. Choose how to install

**A. Helm CLI** (does the Traefik step too):
```bash
TRAEFIK_CHART=./traefik-<version>.tgz ./install-banner-chart --airgap \
  --set coder.host=coder.example.com \
  --set image.registry=registry.internal:5000
```
Omit `TRAEFIK_CHART` if your Traefik chart is in a repo Helm can reach. Add `--skip-traefik` if the plugin is
already enabled or Traefik is managed elsewhere (then apply `support/traefik/rewrite-body-plugin-airgap.yaml`
to your Traefik release yourself: it is a plain values file for the Traefik chart).

**B. Argo CD** (after the chart is in your internal repository):
```bash
# 1. Enable the Traefik plugin (Argo CD cannot: it is Traefik's static config).
TRAEFIK_CHART=./traefik-<version>.tgz ./support/traefik/enable-plugin --airgap
# 2. Upload the chart.
helm push coder-banner-*.tgz oci://registry.internal/charts              # OCI registry (Harbor, ...)
curl --data-binary @coder-banner-*.tgz https://charts.internal/api/charts  # ChartMuseum
#    static repo: copy the .tgz and run  helm repo index . --merge index.yaml --url https://charts.internal
# 3. Edit repoURL, coder.host and image.registry, then apply.
$EDITOR argocd/coder-banner-application.yaml
kubectl apply -f argocd/coder-banner-application.yaml
```
Argo CD must trust your repository: register it and, if it uses your own CA, add that CA to
`argocd-tls-certs-cm`. The Application has no `CreateNamespace` on purpose: the namespace belongs to Coder.

### 5. Verify
```bash
./verify-menu            # "Banner" is in Coder's user menu
```
Open `https://<coder host>/__banner/admin` as a Coder **owner**. Browsers that already cached Coder's app need one
hard refresh (Ctrl+Shift+R) to see the menu change.

## Using it
Admins open the admin page (or use **user menu -> Banner**): write a message, pick a style, **Publish**.
**Show again to everyone** makes the banner reappear even for people who dismissed it (open tabs pick it up within
~60 s; lower it under *Advanced* down to 15 s). What an admin publishes survives upgrades and restarts; **use the chart
defaults** resets it. `./banner-set --off` / `--message ...` change the chart defaults from the command line.

## Removing it (returns Coder to its original state)

```bash
./uninstall-banner-chart                 # Helm install: removes the release, then verifies
kubectl -n argocd delete application coder-banner && ./uninstall-banner-chart --verify-only   # Argo CD install
./uninstall-banner-chart --remove-plugin # additionally reverts Traefik's plugin setting
```
The script then proves the result: no `coder-banner` object remains, Coder's Deployment/Service/route are present
and available, and Coder's page and JavaScript are byte-for-byte stock (the user menu says "Codernauts" again).
Only browsers that cached the rewritten app keep the "Banner" entry until a hard refresh.
Do the Traefik revert **after** removing the chart (`disable-plugin` refuses while a plugin Middleware exists); it
restarts Traefik (see above) and returns its Deployment to exactly what its Helm release describes. Removal takes
Traefik about 5 seconds to notice, so the script waits for it before checking.

## Upgrading
* **The chart:** upload the new archive, then `./install-banner-chart --skip-traefik` (Helm) or raise
  `targetRevision` (Argo CD). Published banners are kept.
* **Coder:** the menu shortcut edits Coder's minified JavaScript, so run `./verify-menu` after every Coder upgrade.
  If Coder's code changed, the shortcut simply stops applying (stock menu) and everything else keeps working; adjust
  `templates/_menu.tpl`.

## Troubleshooting
| Symptom                                   | Check                                                                                          |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------- |
| No banner, HTML has no `/__banner`        | `./preflight-banner`; Traefik logs `Plugins loaded. plugins=["rewrite-body"]`?                 |
| Traefik log: `middleware ... does not exist` | Normal for a few seconds while resources appear; persistent = Middleware missing/namespace.  |
| Pod `ImagePullBackOff`                    | `image.registry` / `imagePullSecrets`; image pushed?                                            |
| Admin page: "Not allowed"                 | Only Coder roles in `admin.roles` (default `owner`).                                           |
| Admin page: "Could not verify your session" | The banner pod cannot reach `http://<coder service>.<ns>.svc`; check the Service name/port.   |
| Menu still says Codernauts                | Hard refresh; then `./verify-menu` (Coder version changed?).                                    |
