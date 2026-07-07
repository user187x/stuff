#!/bin/bash

# fail & bail
set -eou pipefail

kubectl create namespace traefik --dry-run=client -o yaml | kubectl apply -f -

# Name of the secret holding the wildcard certs
WILDCARD_CERT_SECRET="traefik-wildcard-cert"

# Add the the tls wildcard secret to the cluster
kubectl create secret tls "${WILDCARD_CERT_SECRET}" \
 --cert=support/certs/server-wildcard.crt \
 --key=support/certs/server-wildcard.key \
 --namespace traefik || true

kubectl create configmap traefik-ca-bundle \
 --from-file=ca.crt=support/certs/ca.crt \
 --namespace traefik || true

# Install the helm repo
helm repo add traefik https://traefik.github.io/charts || true
helm repo update

helm upgrade --install traefik traefik/traefik \
 --namespace traefik \
 --create-namespace \
 --values values.yaml
