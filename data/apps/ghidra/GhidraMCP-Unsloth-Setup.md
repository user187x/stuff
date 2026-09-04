# GhidraMCP + Unsloth Desktop — Setup Guide

This guide connects the **GhidraMCP** plugin to **Unsloth Desktop** so a local model can decompile
and analyze binaries in Ghidra via MCP tools.

Tested on Linux with Python 3.14 and Unsloth Desktop. macOS is the same; Windows notes are called out
where they differ.

> **Why these specific steps:** Unsloth Desktop's custom-MCP client speaks **streamable-HTTP** (it POSTs
> to the server's base URL). The stock GhidraMCP bridge only ships `stdio` and legacy `sse` transports, so
> we add streamable-HTTP with a tiny patch and point Unsloth at the `/mcp` endpoint.

---

## Prerequisites

- **Ghidra** installed — https://ghidra-sre.org
- **Python 3** with `venv` support
  - Debian/Ubuntu: `sudo apt install python3-full python3-venv`
- **Unsloth Desktop** installed — https://unsloth.ai/download

---

## 1. Install the GhidraMCP plugin

1. Download the latest release zip from https://github.com/LaurieWired/GhidraMCP/releases
   (e.g. `GhidraMCP-release-1-4.zip`) and extract it.
2. Launch Ghidra.
3. `File` → `Install Extensions` → click **`+`** → select the release zip → **OK**.
4. **Restart Ghidra.**
5. Confirm the plugin is enabled: `File` → `Configure` → `Developer` → check **GhidraMCPPlugin**.
6. The plugin runs an HTTP server on **port 8080** by default
   (changeable via `Edit` → `Tool Options` → `GhidraMCP HTTP Server`).
   This is Ghidra's internal server — **not** the endpoint Unsloth connects to.

---

## 2. Set up the Python bridge (in a virtualenv)

A venv avoids the `externally-managed-environment` (PEP 668) error on modern Linux and keeps the
correct `mcp` version isolated.

```bash
# From the extracted release folder (adjust the name to your version)
cd ~/Desktop/GhidraMCP-release-1-4

python3 -m venv venv
source venv/bin/activate

# Pin mcp<2 — the bridge uses FastMCP, which was removed/renamed in mcp 2.x
pip install "mcp<2" requests
```

> **Windows (PowerShell):** activate with `venv\Scripts\Activate.ps1` instead of `source venv/bin/activate`.

**Every new terminal** needs `source venv/bin/activate` before running the bridge — or call the venv's
Python directly: `venv/bin/python bridge_mcp_ghidra.py ...`.

---

## 3. Patch the bridge for streamable-HTTP

Run these three `sed` commands from inside the release folder to add the `streamable-http` transport:

```bash
sed -i 's/choices=\["stdio", "sse"\]/choices=["stdio", "sse", "streamable-http"]/' bridge_mcp_ghidra.py
sed -i 's/if args.transport == "sse":/if args.transport in ("sse", "streamable-http"):/' bridge_mcp_ghidra.py
sed -i 's/mcp.run(transport="sse")/mcp.run(transport=args.transport)/' bridge_mcp_ghidra.py
```

<details>
<summary>Prefer to edit by hand? (three changes in <code>bridge_mcp_ghidra.py</code>)</summary>

```diff
- parser.add_argument("--transport", type=str, default="stdio", choices=["stdio", "sse"],
+ parser.add_argument("--transport", type=str, default="stdio", choices=["stdio", "sse", "streamable-http"],

- if args.transport == "sse":
+ if args.transport in ("sse", "streamable-http"):

-             mcp.run(transport="sse")
+             mcp.run(transport=args.transport)
```
</details>

> The startup log will still print `.../sse` — that line is cosmetic. The real streamable-HTTP endpoint
> is **`/mcp`**.

---

## 4. Start the bridge

```bash
source venv/bin/activate   # if not already active
python bridge_mcp_ghidra.py \
  --transport streamable-http \
  --mcp-host 127.0.0.1 \
  --mcp-port 8081 \
  --ghidra-server http://127.0.0.1:8080/
```

Leave this terminal running. It bridges Unsloth (`:8081/mcp`) to Ghidra's plugin (`:8080`).

**Startup order matters:** Ghidra (with plugin + a program loaded) → bridge → connect in Unsloth.

---

## 5. Connect in Unsloth Desktop

1. Click **MCP** in the chat toolbar → **Add custom MCP**.
2. Fill in:
   - **Display name:** `GhidraMCP`
   - **URL:** `http://127.0.0.1:8081/mcp`
   - **Authentication:** none (leave OAuth off, no headers)
3. Click **Test connection** → once it succeeds, **Add server**.
4. Make sure both the **GhidraMCP** toggle and the master **Use MCP Servers** toggle are ON.
5. Click **Refresh** if the tools don't appear immediately.

---

## 6. Use it

1. Open a binary in Ghidra (the plugin operates on the **currently loaded** program).
2. Pick a local model in Unsloth.
3. Ask it to e.g. *"list the functions"*, *"decompile function X"*, or *"show the imports"*.

---

## Optional: one-command launch script

Save as `run-ghidra-bridge.sh` in the release folder, then `chmod +x run-ghidra-bridge.sh`:

```bash
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
source venv/bin/activate

if ! curl -s -o /dev/null http://127.0.0.1:8080/; then
  echo "⚠️  Ghidra HTTP server not reachable on 8080 — open Ghidra with the plugin enabled and a program loaded."
  exit 1
fi

exec python bridge_mcp_ghidra.py \
  --transport streamable-http --mcp-host 127.0.0.1 --mcp-port 8081 \
  --ghidra-server http://127.0.0.1:8080/
```

Run with `./run-ghidra-bridge.sh`.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `error: externally-managed-environment` | PEP 668 blocks system-wide pip | Use the venv (Step 2). Don't `pip install` globally. |
| `ModuleNotFoundError: No module named 'mcp'` after `pipx install mcp` | pipx isolates the app; the script can't import it | Install into the venv: `pip install "mcp<2" requests` |
| `No module named 'mcp.server.fastmcp'` | mcp 2.x renamed FastMCP | Pin the SDK: `pip install "mcp<2"` |
| Bridge log shows `"POST / HTTP/1.1" 404` | Wrong transport/URL (client hitting root) | Use `--transport streamable-http` and URL `http://127.0.0.1:8081/mcp` |
| Unsloth **Test connection** fails | Bridge not running, or Ghidra not open | Start bridge; ensure Ghidra + plugin running with a program loaded; check port matches |
| Tools don't appear after connecting | Not refreshed / toggles off | Click **Refresh**; enable the server toggle **and** the master toggle |

---

## Security note

Only connect MCP servers you trust, and be cautious combining MCP tools with web search — content from an
analyzed binary or a web result could attempt a prompt injection that triggers unwanted tool calls. Keep
human confirmation on for anything sensitive, especially when reverse-engineering untrusted samples.

---

## Quick reference: transport ↔ URL

| Bridge command | Protocol | Unsloth URL |
|---|---|---|
| `--transport streamable-http` (patched) | streamable HTTP | `http://127.0.0.1:8081/mcp` |
| `--transport sse` (unpatched) | legacy SSE | `http://127.0.0.1:8081/sse` |
