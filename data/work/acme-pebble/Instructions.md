chmod +x setup-pebble-acme.sh

# Defaults: namespace=pebble, issuer=pebble-issuer, solver=nginx
./setup-pebble-acme.sh

# Custom ingress class (e.g. traefik), different namespace
./setup-pebble-acme.sh --namespace acme-test --solver traefik

# Skip solver config (add your own solver to the issuer later)
./setup-pebble-acme.sh --solver none

One thing to note: PEBBLE_VA_ALWAYS_VALID=1 makes Pebble skip actually verifying HTTP-01 challenges, which is ideal for local/CI use. If you want real solver validation (e.g. end-to-end testing your ingress), remove that env var from the Helm values block.
