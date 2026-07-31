#!/bin/bash

# Generate a dummy cert for testing:
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 365 -nodes -subj "/C=US/ST=MD/L=Baltimore/O=Platform Team/OU=DevOps/CN=Jane Doe/emailAddress=jane.doe@example.com"

# Send it to the Go app, simulating the pre-auth shim:
curl -H "X-Forwarded-Tls-Client-Cert: $(cat cert.pem | jq -sRr @uri)" http://localhost:8080/whoami
