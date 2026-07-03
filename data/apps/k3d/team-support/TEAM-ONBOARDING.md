# Routing your app through the platform Gateway

The cluster runs a shared Traefik Gateway (Kubernetes Gateway API). HTTPS,
certificates, and the entry point are platform-owned; **the only resource your
team writes is an `HTTPRoute` in your own namespace.** `whoami-demo.yaml` is a
working copy-paste example.

## The contract

Attach to the shared Gateway with a fixed `parentRef` — `name: traefik-gateway`,
`namespace: traefik` — and set exactly three things of your own:

1. **hostname** — any unused `<yourapp>.xxx.local`. It must sit under
   `*.xxx.local`, because the Gateway listener is bound to that wildcard and
   terminates TLS with the platform wildcard certificate. You never create TLS
   secrets, never see certs, and receive plain HTTP in your pod (check
   `X-Forwarded-Proto: https` if you care).
2. **backend Service name** — in the same namespace as your HTTPRoute.
3. **backend Service port** — the Service `port`, not the container port.

That's it. HTTP on port 80 is auto-redirected to HTTPS, so publish only
`https://` URLs.

## Verifying your route

```bash
kubectl -n <your-ns> get httproute <your-route> \
  -o jsonpath='{range .status.parents[*].conditions[*]}{.type}={.status} ({.reason}){"\n"}{end}'
```

You want `Accepted=True` and `ResolvedRefs=True`.
`ResolvedRefs=False (BackendNotFound)` means your Service name/port is wrong —
remember it's the Service port. Then:

```bash
curl --cacert pki/ca.crt https://<yourapp>.xxx.local/
```

On a local k3d workstation, add `127.0.0.1 <yourapp>.xxx.local` to `/etc/hosts`
first (or run `./tnl-web`, which discovers all HTTPRoute hostnames in the
cluster and patches hosts for you), and make sure the platform CA
(`pki/ca.crt`) is imported in your browser.

## What you must NOT do

Don't create Gateways, GatewayClasses, or listeners; don't reference the
platform TLS secret; don't ask for hostnames outside `*.xxx.local` (the
listener won't match them and the cert won't cover them). Cross-namespace
`backendRefs` require a `ReferenceGrant` in the target namespace — keep the
backend next to the route unless you have a real reason.

## Optional extras (all inside your own HTTPRoute)

Path matching (`rules[].matches[].path`), traffic splitting via weighted
`backendRefs`, header manipulation via `filters`, and multiple hostnames on
one route are all yours to use — see the HTTPRoute spec at
https://gateway-api.sigs.k8s.io. Raw TLS/TCP/UDP routing graduated to the
Standard channel in recent Gateway API releases but is only available once
the platform confirms the installed Traefik version reconciles those v1
resources — ask before depending on it.
