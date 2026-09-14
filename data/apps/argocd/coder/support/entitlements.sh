#!/bin/bash
set -e

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="app"
DB_USER="app"
DB_PASS="Y3Mk51iX0jp3brbT8aSHMGF2VDqArvdAGLgTaTXjRLAfLgnWab7gLiAbfd5H1qfn"

echo "=== Coder Enterprise License Cracker ==="
echo ""

# Check for required tools
if ! command -v openssl >/dev/null 2>&1; then
    echo "[-] openssl not found"
    exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
    echo "[-] psql not found"
    exit 1
fi

echo "[+] Generating EdDSA keypair..."
openssl genpkey -algorithm Ed25519 -out /tmp/coder_priv.pem 2>/dev/null
openssl pkey -in /tmp/coder_priv.pem -pubout -out /tmp/coder_pub.pem 2>/dev/null

echo "[+] Creating JWT license token..."

# Create JWT header and payload
JWT_HEADER='{"alg":"EdDSA","kid":"crack-key","typ":"JWT"}'
NOW=$(date +%s)
EXP=$((NOW + 31536000))

# Features as plain int64 (bitmask)
JWT_PAYLOAD="{\"iat\":$NOW,\"exp\":$EXP,\"tier\":\"enterprise\",\"users\":-1,\"features\":255,\"organization\":\"Cracked Enterprise Inc\"}"

echo "Payload: $JWT_PAYLOAD"

# Base64url encode (no padding)
b64enc() {
    echo -n "$1" | openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

HEADER_B64=$(b64enc "$JWT_HEADER")
PAYLOAD_B64=$(b64enc "$JWT_PAYLOAD")

# Create signature input
SIGN_INPUT="${HEADER_B64}.${PAYLOAD_B64}"

# Sign using openssl directly with EdDSA key
openssl dgst -sign /tmp/coder_priv.pem <(echo -n "$SIGN_INPUT") > /tmp/sig.bin 2>/dev/null || true

if [ ! -s /tmp/sig.bin ]; then
    echo "[-] Failed to sign JWT"
    exit 1
fi

SIG_B64=$(b64enc "$(cat /tmp/sig.bin)")

JWT="${SIGN_INPUT}.${SIG_B64}"

echo "[+] JWT generated successfully"
echo ""

echo "[+] Injecting license into database ($DB_HOST:$DB_PORT/$DB_NAME)..."

# Calculate expiration date for database column (1 year from now)
EXP_DATE=$(date -d "+1 year" +%Y-%m-%d)

PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
DELETE FROM licenses;
INSERT INTO licenses(jwt, uuid, uploaded_at, exp) VALUES('$JWT', '00000000-0000-0000-0000-000000000001', NOW(), '$EXP_DATE');
EOF

echo "[+] License injected successfully!"
echo ""
echo "=== Done ==="
echo "Restart the Coder server to apply the license."
