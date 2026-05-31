# Traefik Auth-Shim (Javalin + Helm)

A minimal Java/Javalin micro-service that backs a Traefik **ForwardAuth**
middleware in Kubernetes, packaged as a Helm chart.

```
client ──HTTPS──▶ Traefik ──ForwardAuth GET /auth──▶ auth-shim
                     │                                  │
                     │ ◀── 2xx (allow) / 4xx (deny) ────┘
                     ▼
                  upstream service
```

## Layout

```
auth-shim-project/
├── auth-shim/                       # Java micro-service
│   ├── pom.xml                      # Maven build (Javalin 6.x, shaded jar)
│   ├── Dockerfile                   # Multi-stage build
│   └── src/main/java/com/example/authshim/
│       ├── Application.java         # Javalin bootstrap
│       └── controller/AuthController.java
└── helm/
    ├── auth-shim/                   # Helm chart for the shim service
    │   ├── Chart.yaml
    │   ├── values.yaml
    │   └── templates/
    │       ├── deployment.yaml
    │       ├── service.yaml
    │       └── middleware.yaml      # Traefik Middleware CR (ForwardAuth)
    ├── traefik-values.yaml          # Traefik chart values
    ├── tlsstore.yaml                # Default TLSStore
    └── example-ingressroute.yaml    # How to attach the middleware
```

## Endpoints exposed by the shim

| Method  | Path     | Purpose                              |
|---------|----------|--------------------------------------|
| GET/POST| `/auth`  | ForwardAuth probe target for Traefik |
| GET     | `/health`| Liveness probe                       |
| GET     | `/ready` | Readiness probe                      |

## ForwardAuth contract

Traefik issues a `GET` to `/auth`. The shim returns:

| Status | Result                                                              |
|--------|---------------------------------------------------------------------|
| `2xx`  | Original request is allowed; response headers listed in the Middleware's `authResponseHeaders` are copied back onto the original request before it reaches the upstream. |
| non-2xx| Original request is short-circuited; the shim's status + body are returned to the client. |

The pass-through default in `AuthController.handleAuth` returns `200 OK`
and emits `X-Auth-Shim: pass-through` and a correlation ID. Replace the
clearly-marked **BUSINESS LOGIC LAYER** block in `AuthController.java`
with your real auth/authz checks.

## Quick start

```bash
# Build the image
cd auth-shim
docker build -t your-registry/auth-shim:1.0.0 .
docker push your-registry/auth-shim:1.0.0

docker login harbor.local
docker tag auth-shim:1.0.0 harbor.local/library/auth-shim:1.0.0
docker push harbor.local/library/auth-shim:1.0.0
cd ..

# Update helm/auth-shim/values.yaml: image.repository → your-registry/auth-shim

# Place server.crt, server.key, and ca.crt in the current directory, then:
./install.sh
```

## Naming note

Kubernetes resource names must follow **DNS-1123** (lowercase letters,
digits, hyphens). Underscores are not allowed, so the secret requested
as `traefik_ingress_tls` is created as **`traefik-ingress-tls`**.
