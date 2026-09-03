#!/usr/bin/env bash
###############################################################################
# coder-banner-airgap.sh — zero-to-hero installer for a Traefik middleware
# stack that:
#
#   1. Rewrites the Coder v2 dashboard response body to inject a top
#      broadcast banner (messages / alerts for all users).
#   2. Strips the "Codernauts" easter-egg game from the dashboard
#      (JS string rewrite + DOM MutationObserver belt-and-suspenders).
#   3. Exposes ONE entry-point middleware — a Traefik `chain` — that your
#      HTTPRoute references. The chain calls every other middleware.
#
# Because Traefik normally downloads plugins from the internet at startup,
# this script vendors the response-body-rewrite plugin AS A LOCAL PLUGIN
# (--experimental.localPlugins) so the cluster never needs egress.
#
# ─────────────────────────────────────────────────────────────────────────
# USAGE
#
#   On an INTERNET-CONNECTED machine:
#       ./coder-banner-airgap.sh bundle
#       # -> produces coder-banner-airgap-bundle.tar.gz
#       # sneakernet the tarball + this script into the air-gapped network
#
#   Inside the AIR-GAPPED cluster (kubectl + helm access):
#       ./coder-banner-airgap.sh install
#
#   Change the broadcast message any time (hot-reloads, no restart):
#       ./coder-banner-airgap.sh set-banner "Maintenance window 22:00 UTC"
#       ./coder-banner-airgap.sh set-banner ""     # hides the banner
#
# ─────────────────────────────────────────────────────────────────────────
# TUNABLES (env-override any of these)
###############################################################################
set -euo pipefail

TRAEFIK_NAMESPACE="${TRAEFIK_NAMESPACE:-traefik}"  # ns of your traefik-gateway release
TRAEFIK_RELEASE="${TRAEFIK_RELEASE:-traefik}"      # helm release name
CODER_NAMESPACE="${CODER_NAMESPACE:-coder}"        # ns where Middlewares live (same ns your HTTPRoute/Coder uses)
TRAEFIK_CHART_VERSION="${TRAEFIK_CHART_VERSION:-}" # PIN to the version you already run! (helm list -n $TRAEFIK_NAMESPACE)
TRAEFIK_IMAGE="${TRAEFIK_IMAGE:-traefik:v3.1}"     # used only for the plugin-extract initContainer (alpine sh+tar)
BANNER_MESSAGE="${BANNER_MESSAGE:-Welcome — announcements will appear here.}"
BANNER_BG="${BANNER_BG:-#b45309}"                       # banner background color
PLUGIN_REPO="https://github.com/packruler/rewrite-body" # response-body rewrite plugin (maintained fork of traefik/plugin-rewritebody)
PLUGIN_REF="${PLUGIN_REF:-v1.2.0}"                      # pin the plugin tag you validated
BUNDLE_IMAGES="${BUNDLE_IMAGES:-false}"                 # true -> also docker-save the traefik image into the bundle

BUNDLE="coder-banner-airgap-bundle.tar.gz"
WORKDIR="$(pwd)/.coder-banner-work"
MANIFESTS="${WORKDIR}/manifests"

log() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
die() {
 printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2
 exit 1
}

###############################################################################
# PHASE 1 — bundle (run ONLINE)
###############################################################################
bundle() {
 command -v git >/dev/null || die "git is required to bundle"
 command -v helm >/dev/null || die "helm is required to bundle"

 rm -rf "$WORKDIR"
 mkdir -p "$WORKDIR/plugins" "$WORKDIR/charts" "$WORKDIR/images"

 log "Cloning rewrite-body plugin @ ${PLUGIN_REF}"
 git clone --depth 1 --branch "$PLUGIN_REF" "$PLUGIN_REPO" "$WORKDIR/plugins/rewrite-body"
 rm -rf "$WORKDIR/plugins/rewrite-body/.git" "$WORKDIR/plugins/rewrite-body/.github"

 log "Tarring plugin source (goes into a ConfigMap on the air-gapped side)"
 tar -czf "$WORKDIR/plugins/rewrite-body.tar.gz" -C "$WORKDIR/plugins" rewrite-body
 rm -rf "$WORKDIR/plugins/rewrite-body"

 log "Pulling traefik helm chart ${TRAEFIK_CHART_VERSION:-(latest — PIN THIS to your installed version!)}"
 helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
 helm repo update traefik >/dev/null
 if [ -n "$TRAEFIK_CHART_VERSION" ]; then
  helm pull traefik/traefik --version "$TRAEFIK_CHART_VERSION" -d "$WORKDIR/charts"
 else
  helm pull traefik/traefik -d "$WORKDIR/charts"
 fi

 if [ "$BUNDLE_IMAGES" = "true" ]; then
  command -v docker >/dev/null || die "docker required when BUNDLE_IMAGES=true"
  log "Saving ${TRAEFIK_IMAGE}"
  docker pull "$TRAEFIK_IMAGE"
  docker save "$TRAEFIK_IMAGE" -o "$WORKDIR/images/traefik.tar"
 fi

 log "Writing bundle ${BUNDLE}"
 tar -czf "$BUNDLE" -C "$WORKDIR" .
 log "Done. Move ${BUNDLE} + this script into the air-gapped environment, then run: $0 install"
}

###############################################################################
# Manifest rendering (shared by install + set-banner)
###############################################################################
render_manifests() {
 mkdir -p "$MANIFESTS"

 # sed-safe banner message
 local msg
 msg=$(printf '%s' "$BANNER_MESSAGE" | sed -e 's/[&/\]/\\&/g' -e "s/'/\&#39;/g" -e 's/</\&lt;/g')
 local bg
 bg=$(printf '%s' "$BANNER_BG" | sed -e 's/[&/\]/\\&/g')

 # ── Middlewares ──────────────────────────────────────────────────────────
 cat >"$MANIFESTS/middlewares.yaml" <<'EOF'
# ============================================================================
# Middleware 1: force gzip-or-identity upstream so the rewrite plugin can
# always decode the body (avoids br/zstd responses it cannot decompress).
# ============================================================================
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: coder-accept-gzip
  namespace: __CODER_NS__
spec:
  headers:
    customRequestHeaders:
      Accept-Encoding: "gzip"
---
# ============================================================================
# Middleware 2: relax CSP so the injected inline <style>/<script> render.
# Remove this from the chain if your Coder CSP already allows inline assets.
# ============================================================================
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: coder-csp-relax
  namespace: __CODER_NS__
spec:
  headers:
    customResponseHeaders:
      Content-Security-Policy: ""
---
# ============================================================================
# Middleware 3: BANNER — response-body writer on text/html.
#   * injects banner CSS before </head>
#   * injects the banner <div> + a MutationObserver right after <body>
#     (the observer also nukes any "Codernauts" menu entry the SPA renders)
# ============================================================================
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: coder-banner
  namespace: __CODER_NS__
spec:
  plugin:
    rewritebody:
      lastModified: true
      monitor:
        types:
          - text/html
      rewrites:
        - regex: '</head>'
          replacement: '<style>#coder-broadcast-banner{display:block;width:100%;padding:10px 16px;box-sizing:border-box;background:__BANNER_BG__;color:#fff;font:600 14px/1.4 system-ui,sans-serif;text-align:center;position:relative;z-index:99999}#coder-broadcast-banner:empty{display:none}</style></head>'
        - regex: '<body([^>]*)>'
          replacement: '<body${1}><div id="coder-broadcast-banner" role="alert">__BANNER_MSG__</div><script>(function(){function sweep(){document.querySelectorAll("a,button,[role=menuitem],li").forEach(function(el){if(/codernauts/i.test(el.textContent||"")&&el.children.length<4){(el.closest("[role=menuitem]")||el).remove();}});}new MutationObserver(sweep).observe(document.documentElement,{childList:true,subtree:true});sweep();})();</script>'
---
# ============================================================================
# Middleware 4: strip "Codernauts" from the JS bundle itself so the menu
# item never registers. Adjust the regex to match your Coder build if the
# label string differs.
# ============================================================================
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: coder-strip-codernauts
  namespace: __CODER_NS__
spec:
  plugin:
    rewritebody:
      lastModified: true
      monitor:
        types:
          - application/javascript
          - text/javascript
      rewrites:
        - regex: 'Codernauts'
          replacement: ''
---
# ============================================================================
# Middleware 5 — THE CHAIN. This is the ONLY middleware your HTTPRoute
# references; it calls every middleware above, in order.
# ============================================================================
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: coder-dashboard-chain
  namespace: __CODER_NS__
spec:
  chain:
    middlewares:
      - name: coder-accept-gzip
      - name: coder-csp-relax
      - name: coder-banner
      - name: coder-strip-codernauts
EOF
 sed -i \
  -e "s/__CODER_NS__/${CODER_NAMESPACE}/g" \
  -e "s/__BANNER_MSG__/${msg}/g" \
  -e "s/__BANNER_BG__/${bg}/g" \
  "$MANIFESTS/middlewares.yaml"

 # ── Example HTTPRoute attachment (Gateway API + Traefik ExtensionRef) ────
 cat >"$MANIFESTS/httproute-example.yaml" <<EOF
# EXAMPLE ONLY — merge the 'filters' block into your existing Coder HTTPRoute.
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: coder
  namespace: ${CODER_NAMESPACE}
spec:
  parentRefs:
    - name: traefik-gateway            # <- your existing Gateway
      namespace: ${TRAEFIK_NAMESPACE}
  hostnames:
    - coder.example.internal           # <- your Coder host
  rules:
    - matches:
        - path: { type: PathPrefix, value: / }
      filters:
        - type: ExtensionRef
          extensionRef:
            group: traefik.io
            kind: Middleware
            name: coder-dashboard-chain   # <- the single chained caller
      backendRefs:
        - name: coder
          port: 80
EOF

 # ── Helm values: local-plugin wiring ─────────────────────────────────────
 cat >"$MANIFESTS/values-local-plugins.yaml" <<EOF
# Merged into your existing traefik-gateway release with --reuse-values.
additionalArguments:
  - "--experimental.localPlugins.rewritebody.moduleName=github.com/packruler/rewrite-body"

additionalVolumeMounts:
  - name: plugins-local
    mountPath: /plugins-local

deployment:
  additionalVolumes:
    - name: plugins-local
      emptyDir: {}
    - name: plugin-bundle
      configMap:
        name: traefik-local-plugins
  initContainers:
    - name: install-local-plugins
      image: ${TRAEFIK_IMAGE}
      command:
        - sh
        - -c
        - >
          mkdir -p /plugins-local/src/github.com/packruler &&
          tar -xzf /bundle/rewrite-body.tar.gz -C /plugins-local/src/github.com/packruler &&
          ls -R /plugins-local/src
      volumeMounts:
        - name: plugins-local
          mountPath: /plugins-local
        - name: plugin-bundle
          mountPath: /bundle
EOF
}

###############################################################################
# PHASE 2 — install (run INSIDE the air gap)
###############################################################################
install() {
 command -v kubectl >/dev/null || die "kubectl required"
 command -v helm >/dev/null || die "helm required"
 [ -f "$BUNDLE" ] || die "bundle ${BUNDLE} not found next to this script — run '$0 bundle' online first"

 rm -rf "$WORKDIR"
 mkdir -p "$WORKDIR"
 tar -xzf "$BUNDLE" -C "$WORKDIR"

 if [ -f "$WORKDIR/images/traefik.tar" ]; then
  log "Image tar present at ${WORKDIR}/images/traefik.tar — load/push it to your internal registry if needed"
 fi

 render_manifests

 log "Publishing plugin source as ConfigMap traefik-local-plugins (ns ${TRAEFIK_NAMESPACE})"
 kubectl -n "$TRAEFIK_NAMESPACE" create configmap traefik-local-plugins \
  --from-file=rewrite-body.tar.gz="$WORKDIR/plugins/rewrite-body.tar.gz" \
  --dry-run=client -o yaml | kubectl apply -f -

 CHART_TGZ=$(ls "$WORKDIR"/charts/traefik-*.tgz | head -n1)
 log "Upgrading ${TRAEFIK_RELEASE} with local-plugin wiring (chart: $(basename "$CHART_TGZ"))"
 helm upgrade "$TRAEFIK_RELEASE" "$CHART_TGZ" \
  -n "$TRAEFIK_NAMESPACE" \
  --reuse-values \
  -f "$MANIFESTS/values-local-plugins.yaml" \
  --wait

 log "Applying middlewares (ns ${CODER_NAMESPACE})"
 kubectl get ns "$CODER_NAMESPACE" >/dev/null 2>&1 || kubectl create ns "$CODER_NAMESPACE"
 kubectl apply -f "$MANIFESTS/middlewares.yaml"

 log "Done."
 echo
 echo "  * Attach the chain to your route: see ${MANIFESTS}/httproute-example.yaml"
 echo "    (only 'coder-dashboard-chain' needs to be referenced — it calls the rest)"
 echo "  * Broadcast a message:  $0 set-banner \"Patching tonight at 22:00 UTC\""
 echo "  * Verify plugin loaded: kubectl -n ${TRAEFIK_NAMESPACE} logs deploy/${TRAEFIK_RELEASE} | grep -i plugin"
}

###############################################################################
# set-banner — update the broadcast message (Traefik hot-reloads the CRD)
###############################################################################
set_banner() {
 BANNER_MESSAGE="${1-}"
 render_manifests
 kubectl apply -f "$MANIFESTS/middlewares.yaml" >/dev/null
 if [ -z "$BANNER_MESSAGE" ]; then
  log "Banner cleared (empty banner auto-hides via CSS)."
 else
  log "Banner set: ${BANNER_MESSAGE}"
 fi
}

###############################################################################
case "${1-}" in
 bundle) bundle ;;
 install) install ;;
 set-banner)
  shift
  set_banner "${1-}"
  ;;
 render)
  render_manifests
  log "Manifests rendered to ${MANIFESTS}"
  ;;
 *)
  echo "Usage: $0 {bundle | install | set-banner \"message\" | render}"
  echo "  bundle      (online)   collect plugin source + helm chart into ${BUNDLE}"
  echo "  install     (air gap)  configmap + helm upgrade + apply middlewares"
  echo "  set-banner  (air gap)  update/clear the broadcast message"
  echo "  render                 just write the YAML to ${MANIFESTS} for review"
  exit 1
  ;;
esac
