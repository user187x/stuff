{{- define "devbox.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "devbox.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "devbox.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "devbox.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{ include "devbox.selectorLabels" . }}
{{- end }}

{{- define "devbox.selectorLabels" -}}
app.kubernetes.io/name: {{ include "devbox.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
