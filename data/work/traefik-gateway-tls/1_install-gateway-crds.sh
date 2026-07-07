#!/bin/bash

# fail & bail
set -eou pipefail

function fetch-api-crds {
 latest=$(curl -s "https://api.github.com/repos/kubernetes-sigs/gateway-api/releases/latest" | jq -r .tag_name)

 # Redirecting logs to stderr (>&2) so they don't break the return variable
 echo -e "Latest Gateway-API CRDS: \e[92m$latest\e[0m" >&2

 fileName="standard-install_gateway-api-crds-${latest}.yaml"
 URL="https://github.com/kubernetes-sigs/gateway-api/releases/download/${latest}/standard-install.yaml"

 echo "Downloading..." >&2

 if wget -q "${URL}" -O "${fileName}"; then
  echo -e "\e[90m Download Complete!\e[0m" >&2
  echo -e " File: \e[92m${fileName}\e[0m" >&2
 else
  echo -e "\e[91mDownload Failed!\e[0m" >&2
  exit 1
 fi

 echo "${fileName}"
}

CRD_MANIFEST_YAML=$(fetch-api-crds)

if kubectl apply -f "${CRD_MANIFEST_YAML}" >/dev/null 2>&1; then
 echo "Gateway API CRDs Installed"
fi
