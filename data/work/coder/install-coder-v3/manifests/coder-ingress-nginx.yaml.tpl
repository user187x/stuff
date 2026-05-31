apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${SAFE_NAME}-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "0"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/backend-protocol: "HTTP"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  ingressClassName: nginx
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
