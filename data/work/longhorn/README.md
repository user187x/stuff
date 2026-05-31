# Start Minikube on a VM-based driver with multiple nodes first
minikube delete
minikube start --driver=kvm2 --nodes=3   # or --driver=hyperkit / --driver=qemu

# Then the setup
./setup-longhorn-minikube.sh

# Tear down later (leaves Longhorn installed)
./setup-longhorn-minikube.sh down


fileserver-1 ─┐
fileserver-2 ─┼─→ share-manager pod (NFSv4) ─→ Longhorn block volume
fileserver-3 ─┘                                        ├─→ replica on node A
                                                       ├─→ replica on node B
                                                       └─→ replica on node C


The share-manager itself is still a single pod (so writes funnel through it — same throughput consideration as NFS), but the underlying data has 3 synchronous replicas across nodes. If a node dies, Longhorn's manager detects it and reschedules — the share-manager pod comes back somewhere else, with surviving replicas already containing the data. All writes go through one share manager pod — this is a single point of throughput. Network overhead applies for remote nodes accessing data via NFSv4. Best for: shared configuration, web content, log aggregation. Not suitable for write-heavy workloads requiring very low latency from multiple nodes.


