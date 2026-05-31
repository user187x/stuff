# lib/postgres.sh — deploy Postgres from manifests/postgres.yaml and build
# the DB connection-URL secret that Coder reads.

apply_postgres() {
 step "PostgreSQL"
 kubectl apply -n "$NAMESPACE" -f "$MANIFESTS_DIR/postgres.yaml" >/dev/null

 log "Waiting for Postgres rollout…"
 kubectl rollout status deployment/postgres -n "$NAMESPACE" --timeout=300s
 ok "Postgres ready"

 local pg_user pg_pass pg_db
 pg_user=$(kubectl get secret coder-pg-credentials -n "$NAMESPACE" -o go-template='{{.data.username | base64decode}}')
 pg_pass=$(kubectl get secret coder-pg-credentials -n "$NAMESPACE" -o go-template='{{.data.password | base64decode}}')
 pg_db=$(kubectl get secret coder-pg-credentials -n "$NAMESPACE" -o go-template='{{.data.database | base64decode}}')

 kubectl create secret generic coder-db-url -n "$NAMESPACE" \
  --from-literal=url="postgres://${pg_user}:${pg_pass}@postgres.${NAMESPACE}.svc.cluster.local:5432/${pg_db}?sslmode=disable" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
 ok "DB URL secret: coder-db-url"
}
