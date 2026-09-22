# Briefing for whoever (human or AI agent) deploys this on the air-gapped cluster

Read this before touching anything. It is written for an assistant agent picking this up cold, but a human
should read it too. `INSTALL.md` in this same folder is the full reference (troubleshooting table included);
this file is the "don't get wrapped around the axle" version.

## Intent

This folder installs the **Coder banner**: an editable announcement banner injected into the Coder dashboard,
built entirely from objects the cluster already uses for Coder (Traefik `Gateway`, `HTTPRoute`, `Middleware`).
It was originally packed as `coder-banner-airgap-<version>.tar.gz` for exactly this transfer. Some of the
inner archives (`coder-banner-<version>.tgz`, `traefik-<version>.tgz`) were **untarred into plain directories**
(`./coder-banner/`, `./traefik/`) before this copy was made. The scripts (`banner-guide` and friends) were
just patched to work either way — packed `.tgz` or unpacked directory — so this should behave identically to a
freshly-unpacked bundle. That patch was verified by dry-run on the packing side only (see Caveats).

**What should happen:** run `./banner-guide`, choose **DEPLOY** (it's the pre-selected default here), answer
its questions — it looks at the cluster and suggests answers in `[brackets]`, press Enter to accept — and it
installs. Nothing on the cluster changes until you type `y` at a confirmation. Every command it runs is echoed
before it runs, and logged to `banner-guide.log`.

## Blast radius — what this is allowed to touch

| Allowed | Never |
|---|---|
| New objects in Coder's own namespace: `Deployment`/`Service` `coder-banner`, an `HTTPRoute`, 6 Traefik `Middleware`s, a few `ConfigMap`s, one `ServiceAccount`+`Role` | Coder's own `Deployment`, `Service`, `HTTPRoute`, database, secrets |
| Traefik's `Deployment` — **one field added** (`rewrite-body` plugin), applied directly, not via `helm upgrade` (see `support/traefik/plugin-lib.sh` for why) | The `traefik-gateway` `Gateway`, any other namespace, any other Traefik release object |

If a step looks like it's about to touch anything outside the left column, **stop and ask a human** — that's
not what this is supposed to do, and something is wrong.

## The one genuinely risky step

Step 6 of DEPLOY ("switch on Traefik's plugin") **restarts Traefik**. With a single Traefik replica, everything
behind it — Coder, Argo CD, anything else — is briefly unreachable while the new pod takes over. The script:
- shows a diff and asks for confirmation before doing it,
- waits for the new pod to be Ready, and rolls back automatically if Traefik comes up with its plugins
  *disabled* (this happens silently if a plugin name is defined twice — Traefik disables ALL plugins but still
  reports `Ready`, so a green rollout is not proof it worked; the script checks Traefik's own log for
  `Plugins loaded` vs `Plugins are disabled`, not just pod readiness).

Do this in a quiet window. Do **not** skip the confirmation or rerun it repeatedly hoping it "just works" —
if it reports a problem, read the actual message (it names the log line), don't guess.

## Caveats — things I could not verify from the packing side

- **No `SHA256SUMS` file is present** in this copy (it covered the outer bundle's packed files, which were
  then unpacked). There is no automated integrity check on these files. If anything about the deploy looks
  wrong, the first thing to suspect is a bad transfer, not the logic.
- **The chart/script changes were verified by dry-run only**, on a machine with no `kubectl`/`helm` installed.
  `./banner-guide --mode deploy --dry-run` was confirmed to correctly find the chart, find the Traefik chart,
  and pick sane defaults — but `helm upgrade`, `support/traefik/enable-plugin`, `preflight-banner`, and
  `verify-menu` were **not** exercised against a real cluster. The first real run here is the real test.
- `banner-guide.log` in this folder may already contain entries from dry-run testing on the packing side
  (timestamps from an unrelated machine). That's noise, not a sign something already happened here — don't
  treat its presence as evidence of a prior install attempt on *this* cluster.
- The Traefik chart version bundled here must match what's actually running on this cluster
  (`helm list -n traefik` → the `CHART` column). The script checks and warns on a mismatch, but it *lets you
  continue anyway* if you say yes — don't say yes to that without knowing why.

## How to stay ahead of surprises

1. **Always `./banner-guide --dry-run` first**, even though you've read this. It shows every command it would
   run with nothing applied — cheap insurance.
2. **Run `./preflight-banner` and read every `FAIL`/`WARN` line before installing**, not just the exit code.
   The DEPLOY flow runs this for you at step 7, but if you're doing anything by hand, run it yourself first.
3. **Trust the suggested defaults, but verify the ones that matter**: `CODER_HOST`, the Gateway name/namespace,
   and the Traefik namespace/deployment. Wrong guesses here mostly fail loudly (preflight catches most), but
   double-check before confirming if anything about the cluster is non-standard.
4. **After installing, run `./banner-guide --mode check`** (or let step 9 do it) — don't assume success from
   "the command didn't error." A `Synced/Healthy` Argo CD app or a `0/1` rollout can both look done and not be.
5. **If something fails, fix the root cause and re-run — don't bypass.** Every script here is idempotent
   (`helm upgrade --install`, safe re-runs of `preflight-banner`/`enable-plugin`), so re-running after a fix is
   always safe. Passing flags to skip checks (`--skip-traefik` without actually having enabled it elsewhere,
   forcing past a preflight `FAIL`) just moves the failure somewhere less visible.
6. **Everything here is reversible**: `./banner-guide --mode remove` deletes exactly what this added and
   proves Coder is back to stock (byte-for-byte, checked automatically). If you're ever unsure whether a change
   is safe, remember there's a tested way back — that should lower the bar for asking a human before a risky
   step, not raise it.
7. **If you're an AI agent reading this**: don't answer `y` to a `banner-guide` confirmation on behalf of a
   human you haven't actually heard from, and don't set `BANNER_GUIDE_ASSUME_DEFAULTS=1` unless a human
   explicitly asked for a fully unattended run. The whole design of this tool is "ask before changing
   anything" — preserve that even when you're the one driving it.

## Escalate, don't improvise, if:

- `preflight-banner` reports a `FAIL` you don't understand,
- the Traefik plugin step reports it rolled back,
- `verify-menu` fails after a Coder upgrade (its regex-based rewrite is tied to Coder's exact minified JS),
- anything suggests Coder's own Deployment/Service/database might be affected.

`INSTALL.md`'s troubleshooting table covers the known failure modes; if it's not in there, stop and ask rather
than guessing.
