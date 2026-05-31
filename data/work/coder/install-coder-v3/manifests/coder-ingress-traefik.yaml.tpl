apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${SAFE_NAME}-ingress
  annotations:
    traefik.ingress.kubernetes.io/router.tls: "true"
    traefik.ingress.kubernetes.io/router.entrypoints: websecure
spec:
  ingressClassName: traefik
  tls:
    - hosts: ["${DOMAIN}", "*.${DOMAIN}"]
      secretName: ${TLS_SECRET}
  rules:
    - host: ${DOMAIN}
      http:
        paths:
          - { path: /, pathType: Prefix, backend: { service: { name: coder, port: { number: 80 } } } }
    - host: "*.${DOMAIN}"
      http:
        paths:
          - { path: /, pathType: Prefix, backend: { service: { name: coder, port: { number: 80 } } } }
