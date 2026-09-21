# Sourced by the banner scripts. Sets CHART to the chart to install: $CHART if given, else the chart directory
# next to the scripts (git checkout), else the newest coder-banner-*.tgz next to them (air-gap bundle).
if [[ -z "${CHART:-}" ]]; then
 if [[ -d ./coder-banner-chart ]]; then
  CHART=./coder-banner-chart
 else
  CHART=$(ls -1 ./coder-banner-*.tgz 2>/dev/null | sort -V | tail -1 || true)
 fi
fi
[[ -n "${CHART}" ]] || { echo "No chart found: expected ./coder-banner-chart or ./coder-banner-*.tgz (or set CHART=...)" >&2; exit 1; }
