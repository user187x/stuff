# Shared by enable-plugin / disable-plugin (sourced, not executed).
#
# Why not `helm upgrade`? The live Gateway "traefik-gateway" can have listeners (http:8080 / https:8443,
# applied by hand) that differ from what the Traefik chart renders (web:8000 / websecure:8443). Helm merges
# listeners by name, so the upgrade yields four listeners and the API server rejects it - and the failed
# rollback leaves the release marked "failed". ArgoCD's HTTPRoute can be bound to sectionName "https", so
# the live listener names must not change. These scripts therefore apply ONLY the Deployment (and, for the
# air-gapped plugin, its ConfigMap) that the chart renders, and never touch the Gateway.
# (Helm 4 applies server-side, so a later `helm upgrade` of the Traefik release conflicts with the field manager
# used here and is marked "failed": run it with --force-conflicts and then ./enable-plugin again.)
#
# Environment:
#   TRAEFIK_NAMESPACE      namespace of the Traefik release          (default: traefik)
#   TRAEFIK_RELEASE        Helm release name                         (default: traefik)
#   TRAEFIK_DEPLOYMENT     name of Traefik's Deployment              (default: the release name)
#   TRAEFIK_CHART          chart reference or a local .tgz / directory (default: traefik/traefik)
#   TRAEFIK_CHART_VERSION  chart version                             (default: the installed release's)
NS="${TRAEFIK_NAMESPACE:-traefik}"
RELEASE="${TRAEFIK_RELEASE:-traefik}"
DEPLOY="${TRAEFIK_DEPLOYMENT:-${RELEASE}}"
CHART="${TRAEFIK_CHART:-traefik/traefik}"
PLUGIN_NAME="rewrite-body"   # the key under experimental.plugins / experimental.localPlugins (see the overlays)
WORK=$(mktemp -d)
trap 'rm -rf "${WORK}"' EXIT
. ./plugin-health.sh   # plugin_health: what Traefik itself says about its plugins (callers cd here first)

# Traefik refuses two plugins with the same name ("the plugin's name must be unique") and then DISABLES ALL
# plugins: it still becomes Ready, but every router that uses a plugin Middleware is dropped, so the banner
# silently stops. The air-gapped overlay defines the plugin as a LOCAL plugin, so a leftover download entry of
# the same name in the release's own values (e.g. from an older values.yaml) must go from the render. Only that
# one entry is removed; other plugins stay. Prints a note when it removed something. Plain awk, no yq needed.
strip_download_plugin() { # edits ${WORK}/values.yaml in place (the layout `helm get values -o yaml` prints)
 # The chart cannot render a bare "plugins:" (null), so when the last entry goes it leaves "plugins: {}".
 awk -v name="${PLUGIN_NAME}" '
  function leave() { if (in_plug && pending != "") print "  plugins: {}"; in_plug = 0; pending = "" }
  /^[^ #]/             { leave(); in_exp = ($0 ~ /^experimental:/); skip = 0 }
  in_exp && /^  [^ #]/ { leave(); skip = 0; if ($0 ~ /^  plugins:/) { in_plug = 1; pending = $0; next } }
  in_plug && /^    [^ #]/ { skip = ($0 ~ ("^    " name ":")); if (skip) next; if (pending != "") { print pending; pending = "" } }
  skip && /^     /     { next }
  { print }
  END { leave() }' "${WORK}/values.yaml" > "${WORK}/values.stripped.yaml"
 if ! cmp -s "${WORK}/values.yaml" "${WORK}/values.stripped.yaml"; then
  echo "NOTE: the release's own values enable the DOWNLOAD plugin '${PLUGIN_NAME}' (experimental.plugins). Traefik" >&2
  echo "      disables all plugins when a plugin name is used twice, so it is left out of this render." >&2
 fi
 mv "${WORK}/values.stripped.yaml" "${WORK}/values.yaml"
}

# Render the chart with the release's own values (+ optional overlay) and keep only the objects we manage:
# the Deployment, and the plugin ConfigMap that exists only when the overlay defines a local plugin.
# Needs only helm and kubectl (no jq / PyYAML), so it runs on a bare air-gapped jump host.
render() { # $1 = optional values overlay
 local version="${TRAEFIK_CHART_VERSION:-$(helm list -n "${NS}" --filter "^${RELEASE}\$" -o yaml | sed -n 's/^ *chart: traefik-//p' | head -1)}"
 local args=(template "${RELEASE}" "${CHART}" -n "${NS}" -f "${WORK}/values.yaml")
 [[ -n "${1:-}" ]] && args+=(-f "$1")
 [[ -f "${CHART}" || -d "${CHART}" ]] || args+=(--version "${version}")
 helm get values "${RELEASE}" -n "${NS}" -o yaml > "${WORK}/values.yaml"
 [[ "${1:-}" == *airgap* ]] && strip_download_plugin
 helm "${args[@]}" --show-only templates/deployment.yaml > "${WORK}/rendered.yaml"
 grep -q '^kind: Deployment' "${WORK}/rendered.yaml" || { echo "could not render the Traefik Deployment from ${CHART}" >&2; return 1; }
 helm "${args[@]}" --show-only templates/local-plugins-cm.yaml >> "${WORK}/rendered.yaml" 2>/dev/null || true
}

show_changes() {
 local raw changes
 raw=$(kubectl diff --server-side --force-conflicts --field-manager=traefik-plugin -f "${WORK}/rendered.yaml" || true)  # exit 1 = differences
 # Show the Deployment's changes line by line; for a ConfigMap (the plugin's source code) just say what it is.
 changes=$(awk '
  function flush() { if (cm && cnt > 0) { sub(/^[^.]*\./, "", obj); print "+ ConfigMap " obj " (" cnt " lines: the plugin source code)" } cm = 0; cnt = 0 }
  /^diff / { flush(); n = split($NF, p, "/"); obj = p[n]; cm = (obj ~ /ConfigMap/); sub(/^.*ConfigMap\./, "", obj); next }
  /^[-+] / { if ($0 ~ /generation:|resourceVersion:|managedFields|time:|manager:|operation:|fieldsType|fieldsV1|f:/) next; if (cm) { cnt++; next } print; next }
  END { flush() }' <<<"${raw}")
 echo "Changes to deployment/${DEPLOY} (+ plugin ConfigMap):"
 if [[ -n "${changes}" ]]; then echo "${changes}"; else echo "  (none)"; fi
}

# maxUnavailable=0: the old pod keeps serving until the new one is Ready, so a broken change never
# takes Traefik down - it is reverted instead.
deploy_revision() { kubectl -n "${NS}" get deployment "${DEPLOY}" -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}' 2>/dev/null || true; }

apply_and_wait() {
 REVISION_BEFORE=$(deploy_revision)   # so verify_plugins_loaded only ever undoes a rollout THIS run caused
 kubectl apply --server-side --force-conflicts --field-manager=traefik-plugin -f "${WORK}/rendered.yaml"
 if ! kubectl -n "${NS}" rollout status deployment/${DEPLOY} --timeout=180s; then
  echo "Traefik did not become ready - rolling back." >&2
  kubectl -n "${NS}" logs deployment/${DEPLOY} --tail=20 >&2 || true
  kubectl -n "${NS}" rollout undo deployment/${DEPLOY}
  return 1
 fi
}

# After enabling: roll back if Traefik came up but disabled its plugins (a rollout only proves it is Ready; see
# plugin-health.sh). Only a rollout this run started is undone: if nothing changed there is nothing to undo, and
# `rollout undo` would wrongly step Traefik back to an older revision.
verify_plugins_loaded() {
 local out state
 out=$(plugin_health "${NS}" "${DEPLOY}"); state=${out%%$'\n'*}
 case "${state}" in
  loaded) echo "Traefik reports: plugins loaded." ;;
  disabled)
   echo "Traefik is up but DISABLED its plugins: $(sed -n 2p <<<"${out}")" >&2
   if [[ "$(deploy_revision)" != "${REVISION_BEFORE:-}" ]]; then
    echo "Rolling back so the banner routes are not left half-working." >&2
    kubectl -n "${NS}" rollout undo deployment/${DEPLOY} >&2
   else
    echo "This run changed nothing, so nothing is rolled back: see the log line above and: kubectl -n ${NS} logs deploy/${DEPLOY}" >&2
   fi
   return 1 ;;
  *) echo "WARNING: could not confirm from Traefik's log that the plugin loaded - check: kubectl -n ${NS} logs deploy/${DEPLOY} | grep -i plugin" >&2 ;;
 esac
}
