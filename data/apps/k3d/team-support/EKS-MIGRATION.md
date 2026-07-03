# EKS migration — replacing /etc/hosts with Route 53

Goal: keep the routing contract (shared Gateway + per-team HTTPRoutes)
byte-for-byte identical, and replace the local-only plumbing (hosts file,
self-signed CA, k3d port mapping) with real AWS equivalents. Teams should
notice exactly one change: the domain suffix.

Pick a delegated DNS zone first, e.g. `apps.example.com` (referred to as
`${DOMAIN_SUFFIX}` below). `.local` cannot be used — it is reserved for mDNS
and will never resolve through Route 53. Use a **public hosted zone** for
internet-facing apps or a **private hosted zone associated with your VPCs**
for internal-only.

---

## 1. Load balancer (replaces k3d's `-p 443:443@loadbalancer`)

Prerequisite: AWS Load Balancer Controller installed in the cluster.
Add to `traefik-values.yaml`:

```yaml
service:
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "external"
    service.beta.kubernetes.io/aws-load-balancer-nlb-target-type: "ip"
    # "internet-facing" for public apps; "internal" for VPC-only:
    service.beta.kubernetes.io/aws-load-balancer-scheme: "internal"
```

Nothing else changes: the NLB forwards 443 → Service port 443 → container
8443, so the Gateway listener stays on **8443** (Traefik's entrypoint port),
exactly as in k3d.

---

## 2. DNS — choose one

### Option A (recommended): a single wildcard ALIAS record

Because the Gateway listener only admits hostnames under
`*.${DOMAIN_SUFFIX}`, one record covers every present and future team app:

```bash
NLB=$(kubectl -n traefik get svc traefik \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# In the hosted zone for ${DOMAIN_SUFFIX}:
#   *.${DOMAIN_SUFFIX}  ALIAS/A  →  ${NLB}
```

(Console, Terraform, or `aws route53 change-resource-record-sets` — an ALIAS
A record to the NLB's zone is preferred over a CNAME.)

Per-app DNS work forever after: **none**.

### Option B: ExternalDNS with the Gateway API source

The in-cluster equivalent of what `tnl-web` did on laptops: it watches
HTTPRoutes and manages one Route 53 record per hostname automatically.
Choose this if you want per-route records, multiple zones, or DNS as an
audit trail. Requires an IRSA role allowing
`route53:ChangeResourceRecordSets` / `ListHostedZones` / `ListResourceRecordSets`.

```yaml
# external-dns helm values
provider: aws
sources:
  - gateway-httproute        # watch HTTPRoute hostnames
domainFilters:
  - apps.example.com
policy: sync                 # create AND delete records with routes
txtOwnerId: platform-cluster # unique per cluster
serviceAccount:
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<ACCOUNT>:role/external-dns
```

---

## 3. TLS — cert-manager replaces gen-certs

The self-signed CA required every laptop to import `pki/ca.crt`. Replace it
with a publicly trusted wildcard from Let's Encrypt via DNS-01 (DNS-01 is
required for wildcards, and works for internal-only apps too since no inbound
HTTP is needed). Requires an IRSA role for cert-manager with Route 53 change
permissions on the zone.

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-dns
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: platform-team@example.com
    privateKeySecretRef:
      name: letsencrypt-dns-account
    solvers:
      - dns01:
          route53:
            region: us-east-1
            # hostedZoneID optional; auto-discovered via IRSA permissions
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: wildcard-apps
  namespace: traefik            # must match the Gateway's namespace
spec:
  # SAME secret name the Gateway already references — this is what makes
  # the migration a no-op for the Gateway spec:
  secretName: wildcard-tls-cert
  issuerRef:
    name: letsencrypt-dns
    kind: ClusterIssuer
  dnsNames:
    - "*.apps.example.com"
    - "apps.example.com"
```

Then in `inst-app`: drop the `gen-certs` call and the
`kubectl create secret tls` step; cert-manager owns the secret (including
renewals). The Gateway's `certificateRefs` is unchanged.

Note: ACM certificates cannot generally be exported into cluster Secrets, so
for TLS terminated *inside* the cluster (your model), cert-manager is the
standard approach. Terminating at the NLB with ACM instead is possible but
changes the Gateway from HTTPS/Terminate to a different trust model — don't
mix the two without deciding deliberately.

---

## 4. What to delete from the local tooling

- `tnl-web` hosts-patching and tunnel: obsolete (DNS is real; NLB is the door).
- `gen-certs`, `pki/`, browser CA import: obsolete (public trust).
- `inst-app` /etc/hosts section and the `--resolve/--cacert` bits of the
  verification: verification becomes a plain
  `curl https://podinfo.${DOMAIN_SUFFIX}/`.
- `init-k3d` entirely (cluster provisioning moves to your EKS IaC); the
  Calico decision is likewise superseded by the VPC CNI unless you have a
  reason to run Calico on EKS.

## 5. Rollout order

1. Hosted zone exists and is delegated → 2. AWS LB Controller →
3. Traefik with the Service annotations (NLB appears) →
4. Wildcard record or ExternalDNS → 5. cert-manager issuer + Certificate
(wait for `Ready=True` on the Certificate) → 6. Gateway + routes as today →
7. `curl https://podinfo.${DOMAIN_SUFFIX}/` from any machine with network
access. No hosts file, no CA import, nothing per-laptop.
