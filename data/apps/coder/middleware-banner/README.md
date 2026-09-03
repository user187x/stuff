ere's the zero-to-hero script (syntax-checked, generated YAML validated). How it hangs together:

The middleware stack (5 total, but your HTTPRoute references only one):

coder-accept-gzip — forces Accept-Encoding: gzip upstream so the body-rewrite plugin can always decode Coder's responses (it can't decompress br/zstd).
coder-csp-relax — strips Coder's CSP header so the injected inline style/script actually render.
coder-banner — the response body writer. It's the rewrite-body Traefik plugin (packruler's maintained fork of the official rewritebody plugin) run as a local plugin, rewriting text/html: injects banner CSS before </head> and the banner <div> right after <body>, plus a small MutationObserver that removes any "Codernauts" menu entry the SPA renders after load.
coder-strip-codernauts — second body-writer instance on application/javascript that strips the label from the JS bundle itself, as belt-and-suspenders.
coder-dashboard-chain — the single chained caller. It's a Traefik chain referencing 1–4 in order; your HTTPRoute attaches only this one via a Gateway API ExtensionRef filter (an example HTTPRoute is rendered for you).

Air-gap flow:

./coder-banner-airgap.sh bundle (online) — clones the plugin source at a pinned tag, pulls the Traefik Helm chart, optionally docker-saves the Traefik image, tars it all up.
./coder-banner-airgap.sh install (inside the gap) — loads the plugin source into a ConfigMap, then helm upgrade --reuse-values on your existing traefik-gateway release: an initContainer extracts the plugin into /plugins-local and --experimental.localPlugins registers it with zero network calls. Then applies all five Middlewares.
./coder-banner-airgap.sh set-banner "Patching at 22:00 UTC" — re-renders and re-applies the CRD; Traefik hot-reloads, no pod restart. Empty message auto-hides the banner via CSS.

Three things to check on your side: pin TRAEFIK_CHART_VERSION to what helm list -n traefik shows (so --reuse-values doesn't drift your release), verify PLUGIN_REF against the latest rewrite-body tag before bundling, and after a Coder upgrade sanity-check the "Codernauts" regex still matches the minified bundle string. The chain order matters — gzip/CSP fixes must run before the body writers, which the script already encodes.
