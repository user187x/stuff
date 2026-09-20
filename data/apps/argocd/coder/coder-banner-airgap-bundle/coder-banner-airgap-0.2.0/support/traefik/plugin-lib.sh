# Shared by enable-plugin / disable-plugin (sourced, not executed).
#
# Why not `helm upgrade`? The live Gateway "traefik-gateway" can have listeners (http:8080 / https:8443,
# applied by hand) that differ from what the Traefik chart renders (web:8000 / websecure:8443). Helm merges
# listeners by name, so the upgrade yields four listeners and the API server rejects it - and the failed
# rollback leaves the release marked "failed". ArgoCD's HTTPRoute can be bound to sectionName "https", so
# the live listener names must not change. These scripts therefore apply ONLY the Deployment (and, for the
# air-gapped plugin, its ConfigMap) that the chart renders, and never touch the Gateway.
#
# Environment:
#   TRAEFIK_NAMESPACE      namespace of the Traefik release          (default: traefik)
#   TRAEFIK_RELEASE        Helm release name                         (default: traefik)
#   TRAEFIK_CHART          chart reference or a local .tgz / directory (default: traefik/traefik)
#   TRAEFIK_CHART_VERSION  chart version                             (default: the installed release's)
NS="${TRAEFIK_NAMESPACE:-traefik}"
RELEASE="${TRAEFIK_RELEASE:-traefik}"
CHART="${TRAEFIK_CHART:-traefik/traefik}"
WORK=$(mktemp -d)
trap 'rm -rf "${WORK}"' EXIT

# Render the chart with the release's own values (+ optional overlay) and keep only the objects we manage:
# the Deployment, and the plugin ConfigMap that exists only when the overlay defines a local plugin.
# Needs only helm and kubectl (no jq / PyYAML), so it runs on a bare air-gapped jump host.
render() { # $1 = optional values overlay
 local version="${TRAEFIK_CHART_VERSION:-$(helm list -n "${NS}" --filter "^${RELEASE}\$" -o yaml | sed -n 's/^ *chart: traefik-//p' | head -1)}"
 local args=(template "${RELEASE}" "${CHART}" -n "${NS}" -f "${WORK}/values.yaml")
 [[ -n "${1:-}" ]] && args+=(-f "$1")
 [[ -f "${CHART}" || -d "${CHART}" ]] || args+=(--version "${version}")
 helm get values "${RELEASE}" -n "${NS}" -o yaml > "${WORK}/values.yaml"
 helm "${args[@]}" --show-only templates/deployment.yaml > "${WORK}/rendered.yaml"
 grep -q '^kind: Deployment' "${WORK}/rendered.yaml" || { echo "could not render the Traefik Deployment from ${CHART}" >&2; return 1; }
 helm "${args[@]}" --show-only templates/local-plugins-cm.yaml >> "${WORK}/rendered.yaml" 2>/dev/null || true
}

show_changes() {
 local changes
 changes=$(kubectl diff --server-side --force-conflicts --field-manager=traefik-plugin -f "${WORK}/rendered.yaml" || true)  # exit 1 = differences
 changes=$(grep -E '^[-+] ' <<<"${changes}" | grep -vE 'generation:|resourceVersion:|managedFields|time:|manager:|operation:|fieldsType|fieldsV1|f:' || true)
 echo "Changes to deployment/traefik (+ plugin ConfigMap):"
 if [[ -n "${changes}" ]]; then echo "${changes}"; else echo "  (none)"; fi
}

# maxUnavailable=0: the old pod keeps serving until the new one is Ready, so a broken change never
# takes Traefik down - it is reverted instead.
apply_and_wait() {
 kubectl apply --server-side --force-conflicts --field-manager=traefik-plugin -f "${WORK}/rendered.yaml"
 if ! kubectl -n "${NS}" rollout status deployment/traefik --timeout=180s; then
  echo "Traefik did not become ready - rolling back." >&2
  kubectl -n "${NS}" logs deployment/traefik --tail=20 >&2 || true
  kubectl -n "${NS}" rollout undo deployment/traefik
  return 1
 fi
}
