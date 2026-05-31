# coder-umbrella (CloudNativePG variant)

An umbrella Helm chart that bundles the **Coder** control plane and a
**CloudNativePG** Postgres cluster — no Bitnami dependency.

```
coder-umbrella/
├── Chart.yaml                      # CNPG operator + Coder as deps
├── values.yaml                     # config for both
├── .helmignore
├── templates/
│   ├── _helpers.tpl
│   └── postgres-cluster.yaml       # the CNPG Cluster CR
└── argocd/
    └── application.yaml            # ArgoCD Application
```

## Why CloudNativePG?

- **Active CNCF project.** Modern, well-maintained, no licensing surprises.
- **Auto-managed credentials.** When you create a `Cluster` named `coder-pg`,
  the operator creates a Secret `coder-pg-app` with keys `username`, `password`,
  `host`, `port`, `dbname`, `uri`, `jdbc-uri`. Coder consumes the `uri` key
  directly via `secretKeyRef` — no URL-builder template needed.
- **Built-in HA, backups, point-in-time recovery, monitoring.** Just config.
- **The Coder docs explicitly call out using a Postgres operator** as a
  supported pattern.

## How the pieces fit

```
ArgoCD Application
  └── Helm release: coder-umbrella
        ├── Sub-chart: cloudnative-pg          (operator + CRDs)
        ├── Template:  postgres-cluster.yaml   (Cluster CR → DB pods + Secret)
        └── Sub-chart: coder                   (envFrom secretKeyRef coder-pg-app:uri)
```

ArgoCD applies in this order on first sync:
1. CRDs from the CNPG sub-chart (`Cluster`, `Backup`, `ScheduledBackup`, ...).
2. The CNPG operator Deployment.
3. The `Cluster` CR from this umbrella's templates (sync-wave `1`).
4. The CNPG operator reconciles the `Cluster`, creates the `coder-pg-app`
   Secret + StatefulSet + Service.
5. The Coder Deployment from the Coder sub-chart starts and reads its DB URL
   from the Secret.

`ServerSideApply=true` in the ArgoCD Application avoids the "no matches for
kind Cluster" race on the very first sync.

## Local install

```bash
helm dependency update ./coder-umbrella

helm install coder ./coder-umbrella \
  --namespace coder \
  --create-namespace \
  --values ./coder-umbrella/values.yaml
```

## Install via ArgoCD

```bash
# Edit argocd/application.yaml — set spec.source.repoURL and path.
kubectl apply -f coder-umbrella/argocd/application.yaml
```

## If the CNPG operator is already installed cluster-wide

Set `cloudnative-pg.enabled: false` in values.yaml. The umbrella will then
only manage the `Cluster` CR + Coder, reusing the existing operator.

## Production checklist

- [ ] `CODER_ACCESS_URL` set to the externally reachable URL.
- [ ] `postgres.instances: 3` for HA (1 primary + 2 streaming replicas).
- [ ] `postgres.backup.barmanObjectStore` configured to S3/GCS/Azure Blob.
- [ ] `postgres.storage.storageClass` pinned to a fast SSD class.
- [ ] TLS terminated at an Ingress / cloud LB OR `coder.coder.tls.secretNames`
      set.
- [ ] CNPG monitoring (`postgres.monitoring.enabled: true`) wired into your
      Prometheus stack.

## Other Postgres options if you don't want CNPG

| Approach | When to pick it |
|---|---|
| **Zalando postgres-operator** | You want an operator and prefer Zalando's model. The Coder docs link to it. Same umbrella pattern: install the operator as a sub-chart, then create a `postgresql` CR in `templates/`. |
| **Crunchy Data PGO** | Enterprise feature set, supported commercially. |
| **External managed DB (RDS / Cloud SQL / Azure)** | Set `cloudnative-pg.enabled: false` and `postgres.enabled: false`. Pre-create a Secret named `coder-db-url` (key `url`) with your connection string. Update Coder's `env` to point at it. |
| **Vanilla community chart** (`groundhog2k/postgres`, etc.) | You want a single StatefulSet with no operator. Trade-off: you give up automated backups / HA / failover. |

## References

- Coder install: https://coder.com/docs/install/kubernetes
- Coder chart: https://github.com/coder/coder/tree/main/helm/coder
- CloudNativePG charts: https://github.com/cloudnative-pg/charts
- CNPG docs: https://cloudnative-pg.io/documentation/current/
