# Coder ApplicationSet — 4 environments

This drops into your existing `coder-umbrella` repo and replaces the single
`Application` with one `ApplicationSet` that produces:

| Environment | Cluster                | Branch source           | Namespace                          |
|-------------|------------------------|-------------------------|------------------------------------|
| **prod**    | prod cluster           | `main`                  | `coder`                            |
| **staging** | staging cluster        | `staging`               | `coder`                            |
| **dev**     | dev cluster            | `develop`               | `coder`                            |
| **preview** | dev cluster (shared)   | any branch `^preview.*` | `coder-preview-<branchNormalized>` |

## Repo layout

```
your-repo/
├── coder-umbrella/                      # the Helm chart (unchanged)
│   ├── Chart.yaml
│   ├── values.yaml                      # base values, applies to all envs
│   ├── templates/
│   └── ...
├── environments/
│   ├── prod/values.yaml                 # prod-specific overrides
│   ├── staging/values.yaml
│   ├── dev/values.yaml
│   └── preview/values.yaml              # base preview overrides (env injected per branch)
└── argocd/
    ├── applicationset.yaml              # ← the new manifest
    └── github-token-secret.example.yaml # SCM provider credentials
```

## How it works

The ApplicationSet has **two generators** sharing a single top-level template:

### Generator 1 — `list` (static envs)

Iterates a hardcoded list of `{env, cluster, targetRevision}` tuples. Each
element produces one `Application` named `coder-<env>` pointing at the
matching cluster, syncing the matching branch, and loading
`environments/<env>/values.yaml`.

### Generator 2 — `scmProvider` (preview envs)

Calls the GitHub API every 30 minutes (default) to enumerate branches in
your repo, filtered down to those matching `^preview.*`. Each matching
branch produces an `Application` named `coder-preview-<branchNormalized>`
deployed to the **dev cluster** in its own namespace `coder-preview-<branchNormalized>`,
with `CODER_ACCESS_URL` templated to `https://<branchNormalized>.preview.coder.example.com`.

When a preview branch is deleted, the corresponding Application is removed
on the next reconcile, and `prune: true` in the syncPolicy cleans up the
namespace + all resources.

## Multi-source apps

Each generated Application uses two sources:

1. The chart at `path: coder-umbrella` on the appropriate branch
2. A second source with `ref: values` so per-environment values files at
   `environments/<env>/values.yaml` can be referenced via `$values/...`

This is ArgoCD's recommended way to keep chart + values in the same repo
without smuggling `..` into `valueFiles`.

## Per-environment override pattern

`coder.coder.env` is a Helm array, and Helm **replaces** arrays rather than
merging them. So each per-env values file defines the **full** env list
(connection URL + OAuth toggle + ACCESS_URL + any extras). The repetition
is small (3-5 entries) and explicit.

For previews, `environments/preview/values.yaml` deliberately omits the env
list — the ApplicationSet's preview generator injects it via
`helm.valuesObject`, with the branch name templated into the URL. That's
how each preview gets its own hostname without committing per-branch files.

## Bootstrap

```bash
# 1. Register your clusters with ArgoCD (one-time per cluster):
argocd cluster add prod-context     --name prod
argocd cluster add staging-context  --name staging
argocd cluster add dev-context      --name dev

# 2. Create the GitHub token secret:
kubectl -n argocd create secret generic github-token \
  --from-literal=token=ghp_xxxxxxxxxxxxxxxxxxxx
kubectl -n argocd label secret github-token argocd.argoproj.io/secret-type=scm-creds

# 3. Apply the ApplicationSet:
kubectl apply -f argocd/applicationset.yaml

# 4. Watch it reconcile:
argocd appset get coder-stack
argocd app list -l 'argocd.argoproj.io/application-set-name=coder-stack'
```

You should see three apps appear immediately (prod / staging / dev), plus
one app for each preview branch on the next SCM poll (≤30 min, or restart
the applicationset-controller for an immediate poll).

## Common adjustments

**Different repo per env.** Change `repoURL` per generator template — the
list elements can carry `repoURL` as another field, and the template
references `{{ .repoURL }}`.

**Namespace-restricted clusters.** Replace `server: '{{ .cluster }}'` with
`name: '{{ .clusterName }}'` and reference clusters by their ArgoCD name
instead of API URL.

**Pull requests instead of branches for previews.** Swap `scmProvider` for
the `pullRequest` generator. You get `number`, `branch`, `head_sha`,
`labels` etc. as parameters. Useful if you only want previews for *open*
PRs (auto-cleaned on merge/close).

**Preserving preview namespaces on deletion.** Set
`spec.preservedFields.annotations` on the ApplicationSet, or remove
`prune: true` from the preview generator's sync policy. By default,
deleting a preview branch deletes everything — usually what you want.

**Throttling the SCM poll.** Add `requeueAfterSeconds: 300` to the
`scmProvider` block to poll every 5 min instead of 30.

## Caveats

- The `scmProvider.github` generator paginates over **all repositories** in
  the configured org. The `repositoryMatch` filter is applied client-side
  *after* pagination, so for large orgs this can be slow. If that's a
  concern, run a separate ApplicationSet per repo or migrate to the
  `pullRequest` generator (single-repo).
- ApplicationSet template substitution happens **before** Helm rendering.
  `valuesObject` strings are templated by Go (`{{ .branch }}` etc.); inside
  the chart, the Helm `{{ }}` runs separately. Don't mix the two contexts.
- Preview branches need wildcard DNS (`*.preview.coder.example.com`) and a
  wildcard TLS cert. Set those up via cert-manager + DNS01 challenge.
