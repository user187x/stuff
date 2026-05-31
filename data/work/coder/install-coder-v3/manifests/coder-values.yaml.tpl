coder:
  replicaCount: 1
  service:
    type: ClusterIP
  resources:
    requests: { cpu: 250m, memory: 512Mi }
    limits:   { cpu: "2",  memory: 2Gi }
  env:
    - name: CODER_PG_CONNECTION_URL
      valueFrom:
        secretKeyRef:
          name: coder-db-url
          key: url
    - name: CODER_ACCESS_URL
      value: "https://${DOMAIN}"
    - name: CODER_WILDCARD_ACCESS_URL
      value: "*.${DOMAIN}"
