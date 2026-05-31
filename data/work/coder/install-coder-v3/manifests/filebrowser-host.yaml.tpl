# filebrowser-host — serves the filebrowser binary on cluster-internal HTTP
# so workspaces can `wget` it without touching the public internet.
#
# The PVC keeps the binary across pod restarts; the install script uses
# `kubectl cp` to push the binary into /www on first run (or whenever the
# local file's size differs from the in-pod copy).
#
# Image: ${FILEBROWSER_HOST_IMAGE} (default busybox:1.36) — must be
# pre-loaded in the cluster's image cache for fully air-gapped installs:
#     minikube image load busybox:1.36
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: filebrowser-host-pvc
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 100Mi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: filebrowser-host
  labels: { app: filebrowser-host }
spec:
  replicas: 1
  strategy: { type: Recreate }
  selector:
    matchLabels: { app: filebrowser-host }
  template:
    metadata:
      labels: { app: filebrowser-host }
    spec:
      containers:
        - name: httpd
          image: ${FILEBROWSER_HOST_IMAGE}
          command: ["sh", "-c", "mkdir -p /www && exec busybox httpd -f -v -p 8080 -h /www"]
          ports:
            - { containerPort: 8080, name: http }
          readinessProbe:
            tcpSocket: { port: 8080 }
            initialDelaySeconds: 2
            periodSeconds: 5
          resources:
            requests: { cpu: 10m,  memory: 16Mi }
            limits:   { cpu: 100m, memory: 64Mi }
          volumeMounts:
            - { name: data, mountPath: /www }
      volumes:
        - name: data
          persistentVolumeClaim: { claimName: filebrowser-host-pvc }
---
apiVersion: v1
kind: Service
metadata:
  name: filebrowser-host
  labels: { app: filebrowser-host }
spec:
  selector: { app: filebrowser-host }
  ports:
    - { port: 80, targetPort: 8080, name: http }
