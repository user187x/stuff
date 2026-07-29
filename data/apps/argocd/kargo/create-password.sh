#!/bin/bash


pass=$(openssl rand -base64 48 | tr -d "=+/" | head -c 32)
echo "Password: $pass"
echo "Password Hash: $(htpasswd -bnBC 10 "" $pass | tr -d ':\n')"
echo "Signing Key: $(openssl rand -base64 48 | tr -d "=+/" | head -c 32)"



