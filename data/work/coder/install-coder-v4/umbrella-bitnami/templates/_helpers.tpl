{{/*
Common labels for resources owned by the umbrella chart.
*/}}
{{- define "coder-umbrella.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/*
Build the Postgres connection URL.

When postgresql.enabled is true, derive the host from Bitnami's standard
service name pattern: "<release>-postgresql" in the release namespace.

Otherwise, require a user-supplied dbUrlSecret.url (external DB).
*/}}
{{- define "coder-umbrella.dbUrl" -}}
{{- if .Values.postgresql.enabled -}}
  {{- $user := .Values.postgresql.auth.username | required "postgresql.auth.username is required" -}}
  {{- $pass := .Values.postgresql.auth.password | required "postgresql.auth.password is required" -}}
  {{- $db   := .Values.postgresql.auth.database | required "postgresql.auth.database is required" -}}
  {{- $host := printf "%s-postgresql.%s.svc.cluster.local" .Release.Name .Release.Namespace -}}
postgres://{{ $user }}:{{ $pass }}@{{ $host }}:5432/{{ $db }}?sslmode=disable
{{- else -}}
  {{- required "dbUrlSecret.url is required when postgresql.enabled is false" .Values.dbUrlSecret.url -}}
{{- end -}}
{{- end -}}
