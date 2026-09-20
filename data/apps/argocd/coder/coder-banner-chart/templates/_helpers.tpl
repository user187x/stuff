{{- define "coder-banner.namespace" -}}
{{ .Values.namespace | default "coder" }}
{{- end -}}

{{- define "coder-banner.labels" -}}
app.kubernetes.io/name: coder-banner
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: coder
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/* Stable banner id: re-shows for users who dismissed an older banner whenever the content changes. */}}
{{- define "coder-banner.id" -}}
{{- .Values.banner.id | default (printf "%s|%s|%s|%s|%s" .Values.banner.level .Values.banner.title .Values.banner.message .Values.banner.linkText .Values.banner.linkUrl | sha256sum | trunc 12) -}}
{{- end -}}
