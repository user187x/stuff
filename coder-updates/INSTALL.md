# Coder banner: air-gapped install, use and removal

An editable announcement banner for the Coder dashboard, plus an admin page and a "Banner" shortcut in Coder's
user menu. It is built only from the objects your cluster already uses for Coder: the `traefik-gateway`
Gateway, Gateway API `HTTPRoute`s and Traefik `Middleware`s. With `--airgap` nothing is downloaded at run time.

## Easiest way: let the guide do it

```bash
./banner-guide
```

It asks what you want to do (bundle, deploy, check, remove), looks at your cluster to suggest answers (press Enter to
accept them), explains every step, shows each command before it runs, and only changes something after you say yes.
`./banner-guide --dry-run` walks through everything without changing anything. It is included in the bundle, so the same
command works on the air-gapped side. Everything below is what it does for you, for anyone who prefers to do it by hand.

**You need:** `kubectl`, `helm` (3.14+ or 4), `jq` on the machine you work from, cluster-admin rights, an
internal container registry, and (for the Argo CD route) an internal Helm chart repository.

## What gets installed, and what is never touched

| The chart adds (namespace of Coder)                                   | The chart never modifies                         |
| --------------------------------------------------------------------- | ------------------------------------------------ |
| `HTTPRoute/coder-banner` (beside Coder's own route, same host)        | Coder's Deployment, Service, database, secrets   |
| 6 Traefik `Middleware`s (`coder-banner*`)                             | Coder's own `HTTPRoute`                          |
| `Deployment` + `Service` `coder-banner` (small Python web server, ports 80 and 8081 for the live channel) | The `traefik-gateway` Gateway |
| 5 ConfigMaps (code, defaults, the text-effects library, the emoji list, published state), 1 ServiceAccount, 1 Role + RoleBinding (one ConfigMap) | Any other namespace |

One thing lives **outside** the chart because Traefik reads it at start-up: the `rewrite-body` plugin has to
be enabled in Traefik's static configuration (`support/traefik/enable-plugin`). It is reversible
(`disable-plugin`).

If a prerequisite is missing (plugin not loaded, image not pullable) Coder keeps working: Traefik drops only the
banner's routers and requests fall through to Coder's own route (tested with a missing plugin and a missing
Middleware).

**A Ready Traefik is not proof that the plugin works.** Traefik refuses a plugin name that is defined twice (e.g. the
download entry `experimental.plugins.rewrite-body` in its Helm values **and** the local one this bundle adds) and then
switches **all** its plugins off while still reporting Ready: Coder keeps answering, but no banner appears. So the
scripts read Traefik's own start-up log (`Plugins loaded` / `Plugins are disabled`): `enable-plugin --airgap` drops a
leftover download entry from its render (it says so, and the preview shows it), rolls back if Traefik still disabled
its plugins, and `preflight-banner` and `banner-guide --mode check` report it.

**Enabling or disabling the plugin restarts Traefik.** The new pod becomes Ready before the old one stops, but with a
single Traefik replica expect every site behind it (Coder, Argo CD, ...) to be unreachable for a few seconds while
traffic moves over. Do it in a quiet window. Installing or removing the chart itself never restarts Traefik.

## Part 1 - on a connected machine: build the bundle

```bash
./make-airgap-bundle --with-traefik-chart=<Traefik chart version on the target cluster>   # e.g. 41.4.0
```

The version is what `helm list -n traefik` shows on the **air-gapped** cluster (`traefik-41.4.0` -> `41.4.0`). Without
`=VERSION` the script uses the cluster you build on, which is only right if the two match; the Traefik step renders
from this archive, so a different version would produce a Deployment that differs from the one running there.

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
The banner appears **instantly** in every open Coder tab, including tabs nobody is touching (see below). The status
pill shows how many tabs are connected right now, and after publishing you are told how many it was sent to.
**Show again to everyone** makes the banner reappear even for people who dismissed it. What an admin publishes
survives upgrades and restarts; **use the chart defaults** resets it. `./banner-set --off` / `--message ...` change
the chart defaults from the command line.

## Emoji picker

On the admin page, the **😀 button to the left of "Bold lead-in"** opens an emoji picker, so a message like
`⚠️ Impending restart! ⚠️` needs no copy-and-paste.

* **Quick picks** for announcements (⚠️ 🚨 ✅ ❌ ℹ️ 🔧 ⏰ 🔒 🔥 🎉 🚀 ...), **recently used** emoji, all categories, and a search
  box that understands names and keywords ("siren", "alert", "rocket").
* It inserts **at the cursor** (replacing any selected text) with a space on each side where one is needed, into the
  **Message** or the **Bold lead-in**: the one you used last, changeable with the *Insert into* choice. Shift-click adds
  several without closing; Esc closes; arrow keys and Enter work. It refuses, and says why, if the text would go over
  the field's limit.
* Emoji are ordinary text, so they work in the banner, in the lead-in, and with every text effect.
* Air-gapped: **nothing is downloaded from outside.** The whole emoji list is one script, `files/emoji/emoji-data.js`
  (1,580 emoji, 86 KB), served by the banner service to admins only and loaded the first time the picker is opened. There is no
  picker library and no images: the picker is `files/emoji-picker.js`, and the emoji are drawn by the operating system's
  emoji font (Windows, macOS, iOS and Android have one; on Linux install `fonts-noto-color-emoji` or equivalent, or they
  show as empty boxes).
* Only emoji up to Unicode Emoji 14.0 are offered (newer ones draw as empty boxes on older systems), and no skin-tone
  variants or country flags.

The list is generated from the MIT-licensed `emojibase-data` package (names and keywords from Unicode CLDR, Unicode
License); see `files/emoji/LICENSE-emojibase.md`. To regenerate it (needs internet, once, on a build machine):
`./support/build-emoji-data` (`--max-version 15` to include newer emoji, `--from DIR` for an unpacked package).
No picker library was used because the available ones inject inline `<style>` elements that the admin page's strict
Content-Security-Policy blocks, or need their data as a separate download, or are too large for one ConfigMap under Argo CD.

## Text effects

The message can be animated: pick an effect from the **Text effect** drop-down on the admin page and tick **Repeat
continuously** if it should keep going (otherwise it plays once each time the banner appears). The preview on the
admin page plays it as you choose; **Replay** plays it again. It goes live with **Publish**, like everything else.

| Effect | What it does |
| --- | --- |
| None | Plain text (the default) |
| Typewriter | Letters appear one by one |
| Fade in | Words fade in one after another |
| Rise | Letters slide up into place |
| Wave | A wave of movement runs through the letters |
| Bounce | Letters drop in and bounce |
| Flip | Letters flip into view |
| Shake | The whole message shakes (keep it for urgent notices) |
| Pulse | The message gently pulses |
| Rainbow | Colours sweep through the letters |

* Chart default instead of the admin page: `banner.effect` / `banner.repeat` in values, or
  `./banner-set --effect wave --repeat`. An unknown name fails the install with the list of valid ones.
* Changing only the effect does **not** bring the banner back for people who dismissed it (only new text does).
* People whose device asks for **reduced motion** always see plain text (their browser setting is respected), and screen
  readers read the sentence once, normally.
* If the effects library cannot load, the banner is simply shown as plain text. The library is only downloaded by
  browsers when a banner actually has an effect, and is cached for a year.
* Air-gapped: the library is **inside the chart** (`files/vendor/`), served by the banner service itself: no CDN, no
  download, and it is part of the bundle like everything else.

**Third-party code:** [Anime.js](https://animejs.com) 4.5.0 by Julian Garnier, MIT licence (`files/vendor/LICENSE-animejs.md`,
also served in the `coder-banner-vendor` ConfigMap). It is the unmodified `dist/bundles/anime.umd.min.js` from the
`animejs@4.5.0` npm package (sha256 `8d5b3a58a1f64023a04a4cedeef135a2263b18da04b35177ac13438a0bad033b`). To update it,
replace that file and its licence, and change the `?v=` in `LIB_URL` at the top of `files/banner.js` so browsers fetch the
new copy.

## How the live push works
Every open Coder tab holds a WebSocket to the banner service (`wss://<coder host>/__banner/live`, served by an
asyncio hub in the same pod, reached through its own `HTTPRoute` rule on port 8081).

* The tab is sent the current banner the moment it connects, and every change the instant it is published (measured:
  ~25 ms from clicking Publish to the banner being on screen in an idle dashboard tab, through Traefik).
* A heartbeat every 20 s lets a tab notice a dead connection (laptop sleep, network change) and reconnect on its own with
  exponential back-off and jitter; on reconnect it is caught up automatically. Traefik's default timeouts do not cut it
  (tested idle for 200 s).
* If a tab cannot use WebSockets it falls back to polling `banner.json` (every `refreshSeconds`); while the socket is up
  it only polls every 5 minutes as a safety net.
* Changes made outside the admin page (`kubectl edit cm coder-banner-state`, a second replica) are pushed within
  ~15 s.
* Capacity: no thread per tab. 300 open tabs cost ~3 MB; the default cap is 5000 (`live.maxConnections`); further tabs
  get a 503 and fall back to polling. (Figures measured with Coder 2.36.0, Traefik 3.7.12, one replica.)
* Tabs that were already open before this feature was installed keep the old polling script until they are reloaded once.
* Turn it off with `--set live.enabled=false` (tabs then poll, as before).
* The channel carries only the public banner content; it needs no login (the admin page still does).

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
  `targetRevision` (Argo CD). Published banners are kept. Coming from 0.2.x (before text effects): banners published
  earlier simply have no effect, and tabs that are already open need one reload to learn about effects.
* **Coder:** the menu shortcut edits Coder's minified JavaScript, so run `./verify-menu` after every Coder upgrade.
  If Coder's code changed, the shortcut simply stops applying (stock menu) and everything else keeps working; adjust
  `templates/_menu.tpl`.
* **Traefik itself** (its own Helm release; not something the banner does): the plugin is applied straight to
  Traefik's Deployment, not through Helm, so keep two things in mind.
  * Helm 4 applies server-side, so `helm upgrade traefik ...` conflicts with the field manager used here and the
    release is marked `failed`. Run it with `--force-conflicts`, then run `./support/traefik/enable-plugin --airgap`
    again (an upgrade takes the plugin arg out). Keep `experimental.plugins.rewrite-body` out of the release's own
    values: the plugin is added by `enable-plugin`, never by the values.
  * If the Traefik release is ever deleted and reinstalled (`helm uninstall` removes the Deployment, Service,
    GatewayClass and Gateway; the CRDs, TLS Secrets and the plugin ConfigMap stay): reinstall from the bundled
    `traefik-<version>.tgz` with the release's values, make sure the Gateway's listener names are the ones your routes
    bind to (Argo CD and Harbor routes use `sectionName: https`), then run `enable-plugin --airgap` and finish with
    `./banner-guide --mode check`.

## Troubleshooting
| Symptom                                   | Check                                                                                          |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------- |
| No banner, HTML has no `/__banner`        | `./preflight-banner`; Traefik logs `Plugins loaded. plugins=["rewrite-body"]`?                 |
| Traefik log: `Plugins are disabled ... must be unique` | The plugin is defined twice (download + local). `./support/traefik/enable-plugin --airgap` removes the download entry; `./banner-guide --mode check` confirms. |
| Traefik log: `middleware ... does not exist` | Normal for a few seconds while resources appear; persistent = Middleware missing/namespace.  |
| A route stopped working after Traefik was reinstalled | `kubectl get httproute -A` and the Gateway's listener names: a route with `sectionName: https` needs a listener called `https`. |
| `helm history traefik` shows `failed` after an upgrade | Helm 4 conflicts with the plugin's field manager: `helm upgrade ... --force-conflicts`, then `./support/traefik/enable-plugin --airgap`. |
| Pod `ImagePullBackOff`                    | `image.registry` / `imagePullSecrets`; image pushed?                                            |
| Admin page: "Not allowed"                 | Only Coder roles in `admin.roles` (default `owner`).                                           |
| Admin page: "Could not verify your session" | The banner pod cannot reach `http://<coder service>.<ns>.svc`; check the Service name/port.   |
| Menu still says Codernauts                | Hard refresh; then `./verify-menu` (Coder version changed?).                                    |
| Banner only appears after a reload / ~1 min | The tab predates the live script (reload once), or `live.enabled=false`, or the socket is blocked: the admin pill shows "0 open tabs" while a tab is open. Check `kubectl -n coder logs deploy/coder-banner` for `live-listening`, and that the `/__banner/live` rule exists on `HTTPRoute/coder-banner`. |
