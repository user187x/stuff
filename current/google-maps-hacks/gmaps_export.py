#!/usr/bin/env python3
"""
gmaps_export.py
Export every saved place from every Google Maps list for the logged-in user.

Why this exists: Google Takeout's "Saved" archive is incomplete. Custom lists
and the places inside them are routinely missing, truncated, or shipped as
bare URLs with no metadata. This script talks directly to the same internal
endpoints the Maps web UI uses, so you get whatever the UI can see.

---------------------------------------------------------------------------
Setup
---------------------------------------------------------------------------
 1. Log into Google in a browser.
 2. Export cookies for google.com to a Netscape-format file (cookies.txt).
    Any "cookies.txt" browser extension works. Save it next to this script.
 3. (Optional) If Google has rotated the `pb=` shape, open Maps -> one of
    your lists -> DevTools Network tab -> filter "preview" -> reload the
    list -> right-click the `entitylist` request -> Copy URL. Paste it into
    URL_LIST_FEATURES below, replacing the list ID with {LIST_ID} and the
    offset/limit with {OFFSET}/{LIMIT}. Same idea for URL_LIST_LISTS if list
    discovery fails. The auth/parsing pipeline stays the same.
 4. python3 gmaps_export.py
    or:  COOKIE_FILE=/path/to/cookies.txt python3 gmaps_export.py

Output (in ./gmaps_export/):
   lists.json                 - parsed manifest of every list
   lists.raw.json             - verbatim API response (kept for re-parsing)
   list_<id>.raw.json         - verbatim per-list pages (concatenated)
   list_<id>_places.json      - parsed places per list
   all_places.tsv             - flattened across every list
---------------------------------------------------------------------------
Dependencies: Python 3.8+, stdlib only. No pip install required.
"""

from __future__ import annotations

import csv
import gzip
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from http.cookiejar import MozillaCookieJar
from pathlib import Path
from typing import Any, Iterable

# === Config (env-overridable) ================================================
COOKIE_FILE = os.getenv('COOKIE_FILE', './cookies.txt')
OUTPUT_DIR = Path(os.getenv('OUTPUT_DIR', './gmaps_export'))
AUTHUSER = os.getenv('AUTHUSER', '0')  # 0 = primary Google account
HL = os.getenv('HL', 'en')
GL = os.getenv('GL', 'us')
PAGE_SIZE = int(os.getenv('PAGE_SIZE', '100'))
SLEEP_BETWEEN = float(os.getenv('SLEEP_BETWEEN', '0.4'))
MAX_RETRIES = int(os.getenv('MAX_RETRIES', '4'))
ORIGIN = 'https://www.google.com'
UA = os.getenv(
 'UA',
 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 '
 '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
)

# URL templates. Both endpoints return the )]}'-prefixed nested-array format.
# The `pb` parameter is Google's protobuf-text encoding; the only bits that
# matter for us are the list ID and pagination, which we substitute below.
URL_LIST_LISTS = os.getenv(
 'URL_LIST_LISTS',
 f'https://www.google.com/maps/preview/userdata/list_lists'
 f'?authuser={AUTHUSER}&hl={HL}&gl={GL}&pb=!1m1!1e1!2m1!1s!3m1!1e1',
)
URL_LIST_FEATURES = os.getenv(
 'URL_LIST_FEATURES',
 f'https://www.google.com/maps/preview/entitylist'
 f'?authuser={AUTHUSER}&hl={HL}&gl={GL}'
 f'&pb=!1m4!1s{{LIST_ID}}!4e1!7e81!9s{{OFFSET}}!2e{{LIMIT}}',
)

# Heuristics for the response walker.
LIST_ID_RE = re.compile(r'^[A-Za-z0-9_\-]{16,}$')
PLACE_ID_RE = re.compile(r'^ChIJ[A-Za-z0-9_\-]{10,}$')


# === Cookie + auth helpers ===================================================
def load_cookies(path: str) -> MozillaCookieJar:
 if not Path(path).exists():
  sys.exit(f'Cookie file not found: {path}')
 cj = MozillaCookieJar(path)
 cj.load(ignore_discard=True, ignore_expires=True)
 return cj


def get_cookie(cj: MozillaCookieJar, name: str) -> str | None:
 # Prefer cookies on .google.com; fall back to anything matching the name.
 candidates = [c for c in cj if c.name == name]
 if not candidates:
  return None
 for c in candidates:
  if c.domain.endswith('google.com'):
   return c.value
 return candidates[0].value


def sapisid_hash(cj: MozillaCookieJar) -> str:
 """
 Build the SAPISIDHASH Authorization header.
 Formula (this is exactly what the Maps frontend computes):
     ts   = current unix seconds
     hash = sha1( ts + " " + SAPISID + " " + ORIGIN )
     header value = "SAPISIDHASH ts_hash"
 Google accepts SAPISID (1P) or __Secure-3PAPISID (3P).
 """
 sapisid = (
  get_cookie(cj, 'SAPISID')
  or get_cookie(cj, '__Secure-3PAPISID')
  or get_cookie(cj, '__Secure-1PAPISID')
 )
 if not sapisid:
  sys.exit(
   'Could not find SAPISID / __Secure-3PAPISID / __Secure-1PAPISID '
   'cookie. Are you logged in and is the cookie file current?'
  )
 ts = str(int(time.time()))
 h = hashlib.sha1(f'{ts} {sapisid} {ORIGIN}'.encode()).hexdigest()
 return f'SAPISIDHASH {ts}_{h}'


def gmaps_get(cj: MozillaCookieJar, url: str) -> str:
 """Authenticated GET with retries. Returns the response body as text."""
 opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
 last_err: Exception | None = None
 for attempt in range(1, MAX_RETRIES + 1):
  req = urllib.request.Request(
   url,
   headers={
    # Auth header must be regenerated each attempt (timestamp-based).
    'Authorization': sapisid_hash(cj),
    'x-goog-authuser': AUTHUSER,
    'Origin': ORIGIN,
    'Referer': f'{ORIGIN}/maps',
    'User-Agent': UA,
    'Accept': '*/*',
    'Accept-Language': 'en-US,en;q=0.9',
    'Accept-Encoding': 'gzip, deflate',
   },
  )
  try:
   with opener.open(req, timeout=30) as resp:
    raw = resp.read()
    if resp.headers.get('Content-Encoding') == 'gzip':
     raw = gzip.decompress(raw)
    return raw.decode('utf-8', errors='replace')
  except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as e:
   last_err = e
   if attempt >= MAX_RETRIES:
    break
   time.sleep(attempt * 2)
 raise RuntimeError(f'Request failed after {MAX_RETRIES} tries: {url} ({last_err})')


def strip_xssi(text: str) -> str:
 """Drop the )]}' XSSI prefix Google prepends to JSON responses."""
 text = text.lstrip()
 if text.startswith(")]}'"):
  text = text[4:]
 return text.lstrip()


# === Walking Google's anonymous-array responses ==============================
# Both endpoints return deeply nested arrays with no field names. Rather than
# indexing fixed paths (which break the moment Google reshuffles), we walk the
# whole tree and pattern-match. This is the layer most likely to need tweaking
# when the schema changes.


def _iter_floats(node: Any) -> Iterable[float]:
 if isinstance(node, list):
  for v in node:
   yield from _iter_floats(v)
 elif isinstance(node, dict):
  for v in node.values():
   yield from _iter_floats(v)
 elif isinstance(node, float):
  yield node


def find_lists(root: Any) -> list[dict]:
 """Find every {id, name} pair that looks like a saved list."""
 out: list[dict] = []
 seen: set[str] = set()

 def rec(node: Any) -> None:
  if isinstance(node, list):
   strs = [x for x in node if isinstance(x, str)]
   ids = [x for x in strs if LIST_ID_RE.match(x) and len(x) >= 22]
   names = [x for x in strs if not LIST_ID_RE.match(x) and 1 <= len(x) <= 200]
   if ids and names:
    lid = ids[0]
    if lid not in seen:
     seen.add(lid)
     name = next(
      (n for n in names if not n.startswith(('http', '/', '@')) and not n.isdigit()),
      names[0],
     )
     out.append({'id': lid, 'name': name})
   for v in node:
    rec(v)
  elif isinstance(node, dict):
   for v in node.values():
    rec(v)

 rec(root)
 return out


def find_places(root: Any) -> list[dict]:
 """Find every place entry by hunting for ChIJ-style place IDs."""
 out: list[dict] = []
 seen: set[str] = set()

 def rec(node: Any) -> None:
  if isinstance(node, list):
   strs = [x for x in node if isinstance(x, str)]
   pids = [x for x in strs if PLACE_ID_RE.match(x)]
   if pids:
    pid = pids[0]
    if pid not in seen:
     seen.add(pid)
     coords = [v for v in _iter_floats(node) if -180 <= v <= 180]
     lat = lng = None
     for i in range(len(coords) - 1):
      a, b = coords[i], coords[i + 1]
      if -90 <= a <= 90 and -180 <= b <= 180:
       lat, lng = a, b
       break
     addr = next(
      (s for s in strs if ',' in s and 5 < len(s) < 250 and not s.startswith('http')),
      '',
     )
     short = [
      s
      for s in strs
      if 1 <= len(s) <= 80
      and not s.startswith(('http', '/', '@', '0x'))
      and not LIST_ID_RE.match(s)
      and s != pid
     ]
     name = next((s for s in short if s != addr), '')
     url = next((s for s in strs if s.startswith('http')), '')
     out.append(
      {
       'name': name,
       'address': addr,
       'lat': lat,
       'lng': lng,
       'place_id': pid,
       'url': url,
      }
     )
   for v in node:
    rec(v)
  elif isinstance(node, dict):
   for v in node.values():
    rec(v)

 rec(root)
 return out


# === Main ====================================================================
def safe_filename(s: str) -> str:
 return re.sub(r'[^A-Za-z0-9._-]', '_', s)


def main() -> int:
 cj = load_cookies(COOKIE_FILE)
 OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

 print('>> Fetching list manifest...')
 raw = strip_xssi(gmaps_get(cj, URL_LIST_LISTS))
 (OUTPUT_DIR / 'lists.raw.json').write_text(raw, encoding='utf-8')

 try:
  manifest_root = json.loads(raw)
 except json.JSONDecodeError as e:
  sys.exit(
   f'Failed to parse manifest JSON: {e}\n'
   f'Response saved to {OUTPUT_DIR / "lists.raw.json"}'
  )

 lists = find_lists(manifest_root)
 (OUTPUT_DIR / 'lists.json').write_text(
  json.dumps(lists, indent=2, ensure_ascii=False), encoding='utf-8'
 )
 print(f'>> Found {len(lists)} list(s).')

 if not lists:
  print(
   'No lists detected. Inspect lists.raw.json -- if the response '
   'looks valid, Google may have changed the response shape; tweak '
   'find_lists().',
   file=sys.stderr,
  )
  return 1

 agg_path = OUTPUT_DIR / 'all_places.tsv'
 with agg_path.open('w', encoding='utf-8', newline='') as agg_fh:
  agg = csv.writer(agg_fh, delimiter='\t', lineterminator='\n')
  agg.writerow(
   [
    'list_name',
    'list_id',
    'name',
    'address',
    'lat',
    'lng',
    'place_id',
    'url',
   ]
  )

  for entry in lists:
   lid, lname = entry['id'], entry['name']
   safe = safe_filename(lid)
   print(f'>> List {lname!r} ({lid})')

   raw_path = OUTPUT_DIR / f'list_{safe}.raw.json'
   raw_path.write_text('', encoding='utf-8')

   offset = 0
   total = 0
   collected: list[dict] = []
   while True:
    url = (
     URL_LIST_FEATURES.replace('{LIST_ID}', lid)
     .replace('{OFFSET}', str(offset))
     .replace('{LIMIT}', str(PAGE_SIZE))
    )
    try:
     page_text = strip_xssi(gmaps_get(cj, url))
    except Exception as e:
     print(f'   !! request failed: {e}', file=sys.stderr)
     break

    with raw_path.open('a', encoding='utf-8') as f:
     f.write(page_text + '\n')

    try:
     page_root = json.loads(page_text)
    except json.JSONDecodeError:
     print('   !! could not parse page JSON', file=sys.stderr)
     break

    places = find_places(page_root)
    if not places:
     break

    for p in places:
     agg.writerow(
      [
       lname,
       lid,
       p.get('name', ''),
       p.get('address', ''),
       '' if p.get('lat') is None else p['lat'],
       '' if p.get('lng') is None else p['lng'],
       p.get('place_id', ''),
       p.get('url', ''),
      ]
     )

    collected.extend(places)
    total += len(places)

    if len(places) < PAGE_SIZE:
     break
    offset += PAGE_SIZE
    time.sleep(SLEEP_BETWEEN)

   (OUTPUT_DIR / f'list_{safe}_places.json').write_text(
    json.dumps(collected, indent=2, ensure_ascii=False),
    encoding='utf-8',
   )
   print(f'   -> {total} place(s)')

 print()
 print(f'Done. Aggregate output: {agg_path}')
 print(f'Per-list raw responses: {OUTPUT_DIR}/list_*.raw.json')
 print(
  "If a list has 0 places but you know it shouldn't, capture the "
  'request from DevTools and override URL_LIST_FEATURES via env var.'
 )
 return 0


if __name__ == '__main__':
 sys.exit(main())
