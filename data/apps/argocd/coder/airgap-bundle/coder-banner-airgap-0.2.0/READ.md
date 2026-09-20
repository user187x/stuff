The banner chart can now be installed in an air-gapped cluster, removed cleanly, and installed through Argo CD

Air-gapped install

- Two external dependencies: the chart's only external dependencies were the Python image and the Traefik plugin, which Traefik downloads from GitHub at startup.
- Image: the image is now split into registry, repository, tag and optional digest, plus imagePullSecrets.
- Plugin: the plugin source is vendored in support/traefik/plugin-src (Apache-2.0, byte-identical to upstream v0.3.1) and delivered to Traefik as a ConfigMap.
- Bundle: ./make-airgap-bundle builds one checksummed tarball with the chart, the image archive, the plugin source, the scripts, the docs and the Argo CD Application.
- Preflight: ./preflight-banner runs read-only checks before install.
- Offline proof: Traefik loaded the plugin on a Docker network with no route out, and returned your 2.4 MB bundle byte-identical to my earlier reference output. I also ran the local plugin on the live cluster, and nothing was downloaded.

Removal

./uninstall-banner-chart removes the release and then checks the result. I ran it live and compared with the state before the banner existed:

- Coder's own objects: Coder's Deployment, Service and HTTPRoute were unchanged.
- Login page: identical to the first copy I fetched before any banner work.
- JavaScript bundle: byte-identical to the original.
- Traefik: disable-plugin returned it to its original Helm release manifest, with an empty diff. Removing through Argo CD was equally clean.

The chart only ever adds objects and never edits Coder's, which is why this works. If the plugin or a Middleware is missing, Traefik drops only the banner's routes and Coder still answers. I tested that with a missing plugin and a nonexistent Middleware.

Argo CD Application

The file is coder-banner-argocd.yaml. I ran it unchanged, except for the repo URL and registry, from an in-cluster chart repository.

- Sync waves: it now applies the pod, then the Middlewares, then the route. That removes the brief Traefik "middleware does not exist" errors.
- State ConfigMap: a banner published by an admin survived a hard refresh, with no drift.
- ignoreDifferences: my ignoreDifferences rule turned out to be unnecessary on your Argo CD v3.5.2. I corrected the file's comment and kept the rule as insurance.
- Namespace: there is deliberately no CreateNamespace, so deleting the Application cannot touch the coder namespace.

Things to know

- Traefik restarts: enabling or disabling the plugin restarts Traefik, and with a single replica everything behind it is unreachable for a few seconds. I saw exactly that. Installing or removing the chart does not restart Traefik.
- Hard refresh after removal: browsers that cached the rewritten Coder app keep the "Banner" menu entry until a hard refresh.
- Helm CLI install: the Helm CLI install still logs a few seconds of harmless Traefik errors while it settles. Argo CD's waves avoid them.
- Bugs I caught and fixed:
  - A relative TRAEFIK_CHART path broke.
  - The disable-plugin preview hid removals.
  - The preflight image check gave false warnings.
  - The removal check reported failures during the normal few-second convergence.
  - The repo's *.css ignore rule would have dropped app.css from any commit.
  - Bundle output is now git-ignored.

Not tested

- A real disconnected network. I used an internal Docker network and an in-cluster repo instead.
- Your actual internal chart repository (ChartMuseum, Harbor or OCI push), Argo CD trusting your CA, docker push to a real registry, or Podman.
- uninstall-banner-chart --remove-plugin end to end. I ran disable-plugin directly.
- Other Coder or Traefik versions. I tested Coder 2.36.0 and Traefik 3.7.12.
