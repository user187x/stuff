# coder-umbrella

A Helm umbrella chart that bundles the **Coder** control plane and **Bitnami
PostgreSQL** so the entire stack can be deployed as a single ArgoCD
`Application`.

```
coder-umbrella/
├── Chart.yaml                      # declares postgresql + coder as deps
├── values.yaml                     # config for both sub-charts
├── .helmignore
├── templates/
│   ├── _helpers.tpl                # builds the postgres URL
│   └── coder-db-secret.yaml        # creates `coder-db-url` Secret
└── argocd/
    └── application.yaml            # ArgoCD Application that deploys this chart
```

## How it works

Coder's chart expects a `Secret` named `coder-db-url` with a key `url`
containing the Postgres connection string. The umbrella chart:

1. Pulls in Bitnami PostgreSQL as a sub-chart (creates the DB in-cluster).
2. Pulls in the official Coder chart as a sub-chart.
3. Generates the `coder-db-url` Secret from values, pointing at the
   in-cluster Postgres service (`<release>-postgresql.<ns>.svc.cluster.local`).

That keeps the two sub-charts loosely coupled and lets you swap to a managed
DB by flipping `postgresql.enabled: false` and setting `dbUrlSecret.url`.

## Local install (without ArgoCD)

```bash
helm dependency update ./coder-umbrella

helm install coder ./coder-umbrella \
  --namespace coder \
  --create-namespace \
  --values ./coder-umbrella/values.yaml
```

## Install via ArgoCD

1. Commit the `coder-umbrella/` directory to your GitOps repo.
2. Edit `argocd/application.yaml` — set `spec.source.repoURL` and
   `spec.source.path` to match your repo layout.
3. Apply the Application:

```bash
kubectl apply -f coder-umbrella/argocd/application.yaml
```

ArgoCD will run `helm dependency build` automatically when it sees the
`Chart.yaml` with declared dependencies, so you do **not** need to commit
the `charts/` tarballs to Git.

### ArgoCD repository registration

ArgoCD must be able to reach both Helm repos. If your ArgoCD instance has
egress to the public internet, no action is needed. If it's air-gapped or
restricted, register the repos in ArgoCD (one-time, cluster-wide):

```bash
argocd repo add https://charts.bitnami.com/bitnami --type helm --name bitnami
argocd repo add https://helm.coder.com/v2          --type helm --name coder-v2
```

Or declaratively as `argoproj.io/v1alpha1` `Repository` secrets in the
`argocd` namespace.

## Production checklist

- **Set `CODER_ACCESS_URL`** to the externally reachable URL (uncomment in
  `values.yaml`). Without this, workspace agents will get stuck connecting.
- **Replace the Postgres password.** The default `coder/coder` is
  proof-of-concept only. For real environments either:
  - Use a managed DB (`postgresql.enabled: false`, set `dbUrlSecret.url`), or
  - Pre-create `coder-db-url` from a secret manager (Vault/ESO) and set
    `dbUrlSecret.create: false`.
- **Pin chart versions.** `Chart.yaml` already pins exact versions; bump them
  intentionally and re-run `helm dependency update`.
- **TLS.** Either provide a TLS Secret via `coder.coder.tls.secretNames`, or
  terminate TLS at an Ingress / cloud load balancer in front of Coder.
- **Backups.** The Bitnami chart does not back up your DB. Use a CronJob,
  Velero, or your cloud provider's snapshot tooling.

## References

- Coder install docs: https://coder.com/docs/install/kubernetes
- Coder Helm chart: https://github.com/coder/coder/tree/main/helm/coder
- Bitnami Postgres chart: https://github.com/bitnami/charts/tree/main/bitnami/postgresql
