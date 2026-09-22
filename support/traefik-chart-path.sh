# Sourced by the banner scripts (cwd = the bundle root). Sets TRAEFIK_CHART, if not already given by the
# caller, to a local Traefik chart next to the scripts: an unpacked chart (./traefik - what you get from
# extracting a Helm chart archive, since Helm names the top-level folder after the chart) if present, else the
# newest ./traefik-*.tgz (a bundle where that archive was kept packed). Leaves TRAEFIK_CHART empty when neither
# exists, so callers (support/traefik/plugin-lib.sh) fall back to a configured Helm repository.
#
# Also sets TRAEFIK_CHART_VER to that local chart's version (from its Chart.yaml, or parsed from the .tgz name),
# or "" when there is no local chart to report a version for.
if [[ -z "${TRAEFIK_CHART:-}" ]]; then
 if [[ -f ./traefik/Chart.yaml ]]; then
  TRAEFIK_CHART=./traefik
 else
  TRAEFIK_CHART=$(ls -1 ./traefik-*.tgz 2>/dev/null | sort -V | tail -1 || true)
 fi
fi

TRAEFIK_CHART_VER=""
if [[ -f ./traefik/Chart.yaml ]]; then
 TRAEFIK_CHART_VER=$(awk '/^version:/{print $2; exit}' ./traefik/Chart.yaml)
elif [[ "${TRAEFIK_CHART:-}" == ./traefik-*.tgz ]]; then
 TRAEFIK_CHART_VER=$(sed -n 's/^\.\/traefik-\(.*\)\.tgz$/\1/p' <<<"${TRAEFIK_CHART}")
fi
