#!/bin/bash
# Auto-generated Minikube startup script for cluster: xxx

# Address rootless docker requirement if the Docker driver is in use
if [[ "docker" == "docker" ]] && command -v docker &>/dev/null; then
 if [[ "$MINIKUBE_ROOTLESS" == "true" ]] || docker context ls | grep -q "rootless"; then
  echo -e "\n[\!] Enforcing rootless Docker context to resolve driver constraints..."
  docker context use rootless || true
 fi
fi

echo -e "\n\e[1mStarting Minikube cluster 'xxx'...\e[0m\n"

set -x
minikube start -p "xxx" \
 --nodes="2" \
 --memory="4g" \
 --cpus="2" \
 --disk-size="20g" \
 --driver="docker" \
 --container-runtime="docker" \
 \
 --gpus=all

set +x
