#!/bin/bash

kubectl apply -f httproute-coder.yaml
kubectl apply -f httproute-headlamp.yaml
kubectl apply -f httproute-grafana.yaml
