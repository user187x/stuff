#!/bin/bash

echo "To build the chart into a tarball:"
echo "$ helm package ./pre-auth-shim"

echo "To install the chart:"
# Note: Update the version number to match your Chart.yaml
echo "$ helm install pre-auth-shim ./pre-auth-shim-0.1.0.tgz"

echo "To delete the chart:"
echo "$ helm uninstall pre-auth-shim"
