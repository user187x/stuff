# Sourced by the banner scripts. Sets CHART to the chart to install: $CHART if given, else the chart directory
# next to the scripts - a git checkout (./coder-banner-chart) or an unpacked chart archive (./coder-banner,
# the name `helm package`/`tar` gives its top-level folder, from Chart.yaml's `name:`) - else the newest
# coder-banner-*.tgz next to them (a bundle where that archive was kept packed).
if [[ -z "${CHART:-}" ]]; then
 if [[ -d ./coder-banner-chart ]]; then
  CHART=./coder-banner-chart
 elif [[ -f ./coder-banner/Chart.yaml ]]; then
  CHART=./coder-banner
 else
  CHART=$(ls -1 ./coder-banner-*.tgz 2>/dev/null | sort -V | tail -1 || true)
 fi
fi
[[ -n "${CHART}" ]] || { echo "No chart found: expected ./coder-banner-chart, ./coder-banner (unpacked) or ./coder-banner-*.tgz (or set CHART=...)" >&2; exit 1; }
