#!/usr/bin/env python3

import asyncio
import uuid
import sys
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse

app = FastAPI()

# Global state
pending_requests = {}
stats = {'received': 0, 'approved': 0, 'denied': 0}
app_state = {'auto_accept': False}


@app.get('/auth')
@app.get('/verify')
async def handle_auth(request: Request):
 stats['received'] += 1

 if app_state['auto_accept']:
  stats['approved'] += 1
  return JSONResponse({'status': 'approved'}, status_code=200)

 req_id = str(uuid.uuid4())
 event = asyncio.Event()
 headers = dict(request.headers)
 uri = headers.get('x-forwarded-uri', 'Unknown')
 ip = headers.get('x-forwarded-for', 'Unknown')

 # Log headers to pod stdout for debugging
 print(f'[{req_id}] Incoming headers: {headers}', file=sys.stdout, flush=True)

 # Target the correct Traefik header
 dn_raw = headers.get(
  'x-forwarded-tls-client-cert-info', headers.get('x-forwarded-dn', 'Unknown')
 )

 # Format the raw output (e.g., Subject="CN=admin,O=IT") for the UI
 dn = dn_raw
 if dn_raw != 'Unknown' and 'Subject="' in dn_raw:
  try:
   dn = dn_raw.split('Subject="')[1].split('"')[0]
  except IndexError:
   pass

 pending_requests[req_id] = {
  'event': event,
  'decision': None,
  'uri': uri,
  'ip': ip,
  'dn': dn,
  'id': req_id,
 }

 try:
  await asyncio.wait_for(event.wait(), timeout=60.0)
 except asyncio.TimeoutError:
  pending_requests.pop(req_id, None)
  stats['denied'] += 1
  raise HTTPException(status_code=403, detail='Timeout waiting for admin approval')

 decision = pending_requests.pop(req_id, {}).get('decision')
 if decision == 'approve':
  stats['approved'] += 1
  return JSONResponse({'status': 'approved'}, status_code=200)
 else:
  stats['denied'] += 1
  raise HTTPException(status_code=403, detail='Denied by admin UI')


@app.get('/ui', response_class=HTMLResponse)
async def ui():
 return """
    <html>
    <head>
        <title>Auth Shim UI</title>
        <style>
            body {
                font-family: monospace;
                padding: 20px;
                padding-bottom: 70px; /* Leave space for footer */
                background: #1e1e1e;
                color: #d4d4d4;
            }
            .header-container {
                display: flex;
                align-items: center;
                gap: 20px;
                margin-bottom: 20px;
            }
            .requests-container {
                max-height: 1100px; /* Fits approximately 10 request blocks before scrolling */
                overflow-y: auto;
                border: 1px solid #333;
                padding: 10px;
                background: #181818;
            }
            .req { border: 1px solid #333; padding: 15px; margin-bottom: 10px; background: #252526; }
            button { padding: 8px 15px; margin-right: 10px; cursor: pointer; border: none; font-weight: bold; }
            .approve { background: #4CAF50; color: white; }
            .deny { background: #f44336; color: white; }

            /* Toggle Switch CSS */
            .switch { position: relative; display: inline-block; width: 44px; height: 22px; }
            .switch input { opacity: 0; width: 0; height: 0; }
            .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #555; transition: .4s; border-radius: 22px; }
            .slider:before { position: absolute; content: ""; height: 14px; width: 14px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; }
            input:checked + .slider { background-color: #4CAF50; }
            input:checked + .slider:before { transform: translateX(22px); }
            .toggle-label { font-size: 1.2em; font-weight: bold; }

            /* Footer CSS */
            .footer {
                position: fixed;
                bottom: 0;
                left: 0;
                width: 100%;
                background: #111;
                border-top: 2px solid #333;
                padding: 15px 0;
                text-align: center;
                font-size: 1.2em;
                font-weight: bold;
                z-index: 1000;
            }
            .stat-val { color: #4CAF50; margin-right: 15px; }
            .stat-val.denied { color: #f44336; }
            .stat-val.received { color: #2196F3; }
        </style>
    </head>
    <body>
        <div class="header-container">
            <h2>Pending Traefik Requests</h2>
            <label class="switch">
                <input type="checkbox" id="autoAcceptToggle" onchange="toggleAutoAccept()">
                <span class="slider"></span>
            </label>
            <span class="toggle-label">Auto-Accept New Requests</span>
        </div>

        <div class="requests-container" id="requests"></div>

        <div class="footer">
            Total Received: <span class="stat-val received" id="stat-received">0</span>
            Total Approved: <span class="stat-val" id="stat-approved">0</span>
            Total Denied: <span class="stat-val denied" id="stat-denied">0</span>
        </div>

        <script>
            async function fetchState() {
                let res = await fetch('/api/state');
                let data = await res.json();

                // Update UI Stats
                document.getElementById('stat-received').innerText = data.stats.received;
                document.getElementById('stat-approved').innerText = data.stats.approved;
                document.getElementById('stat-denied').innerText = data.stats.denied;

                // Sync toggle state without triggering onchange
                let toggle = document.getElementById('autoAcceptToggle');
                if (toggle.checked !== data.auto_accept) {
                    toggle.checked = data.auto_accept;
                }

                // Render requests
                let div = document.getElementById('requests');
                div.innerHTML = '';
                for (let id in data.requests) {
                    let req = data.requests[id];
                    div.innerHTML += `<div class="req">
                        <strong>IP:</strong> ${req.ip} <br>
                        <strong>URI:</strong> ${req.uri} <br>
                        <strong>DN:</strong> ${req.dn} <br><br>
                        <button class="approve" onclick="decide('${id}', 'approve')">Approve</button>
                        <button class="deny" onclick="decide('${id}', 'deny')">Deny</button>
                    </div>`;
                }
            }

            async function toggleAutoAccept() {
                let toggleState = document.getElementById('autoAcceptToggle').checked;
                await fetch('/api/auto_accept', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ auto_accept: toggleState })
                });
                fetchState();
            }

            async function decide(id, action) {
                await fetch(`/api/decide/${id}/${action}`, {method: 'POST'});
                fetchState();
            }

            setInterval(fetchState, 2000);
            fetchState();
        </script>
    </body>
    </html>
    """


@app.get('/api/state')
async def get_state():
 """Consolidated endpoint to return stats, auto-accept state, and active requests."""
 reqs = {
  k: {'id': v['id'], 'uri': v['uri'], 'ip': v['ip'], 'dn': v['dn']}
  for k, v in pending_requests.items()
 }
 return {'requests': reqs, 'stats': stats, 'auto_accept': app_state['auto_accept']}


@app.post('/api/auto_accept')
async def toggle_auto_accept(request: Request):
 """Toggles the auto-accept capability."""
 data = await request.json()
 app_state['auto_accept'] = data.get('auto_accept', False)

 # If turned on, automatically approve all currently pending requests
 if app_state['auto_accept']:
  for req_id, req_data in list(pending_requests.items()):
   if req_data['decision'] is None:
    req_data['decision'] = 'approve'
    req_data['event'].set()

 return {'status': 'ok', 'auto_accept': app_state['auto_accept']}


@app.post('/api/decide/{req_id}/{action}')
async def decide_request(req_id: str, action: str):
 """Processes manual decisions."""
 if req_id in pending_requests and action in ['approve', 'deny']:
  pending_requests[req_id]['decision'] = action
  pending_requests[req_id]['event'].set()
  return {'status': 'ok'}
 raise HTTPException(status_code=404, detail='Request not found or already processed')
