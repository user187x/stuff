from flask import Flask, request, Response
import requests

app = Flask(__name__)


@app.route("/verify", methods=["GET"])
def verify():
    # 1. Get the DN from the incoming request headers
    # (Traefik passes all headers to this service automatically)
    user_dn = request.headers.get("X-User-DN")

    if not user_dn:
        return Response("Missing DN Header", status=401)

    # 2. Construct the dynamic URL and call the external service
    external_url = f"http://checktherealguy.com/dn/{user_dn}"

    try:
        resp = requests.get(external_url)

        # 3. If external service says NO (non-200), we deny access
        if resp.status_code != 200:
            return Response("Access Denied by External Check", status=403)

        # 4. If YES, we return 200 and extracting the enrichment data
        # We assume the external service returns JSON or headers we want to pass along
        enrichment_data = resp.headers.get("X-User-Role", "guest")

        # 5. Send 200 OK back to Traefik with the NEW headers
        return Response(status=200, headers={"X-Enriched-Role": enrichment_data})

    except Exception as e:
        return Response(str(e), status=500)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
