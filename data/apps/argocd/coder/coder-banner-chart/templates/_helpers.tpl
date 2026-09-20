{{/* Everything lives next to Coder's Service, so default to the namespace the release is installed into. */}}
{{- define "coder-banner.namespace" -}}
{{ .Values.namespace | default .Release.Namespace }}
{{- end -}}

{{/* registry/repository:tag, or registry/repository@digest when a digest is pinned. */}}
{{- define "coder-banner.image" -}}
{{- $image := .Values.image -}}
{{- $ref := ternary (printf "%s/%s" $image.registry $image.repository) $image.repository (not (empty $image.registry)) -}}
{{- if $image.digest -}}{{ printf "%s@%s" $ref $image.digest }}{{- else -}}{{ printf "%s:%s" $ref $image.tag }}{{- end -}}
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
