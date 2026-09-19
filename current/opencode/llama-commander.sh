#!/bin/bash

set -euo pipefail

MODEL_FILE=/home/xxx/Models/unsloth/Qwen3.8-27B-GGUF/Qwen3.8-27B-UD-Q4_K_S.gguf
BASE_URL=http://127.0.0.1:8080
SERVER_URL=$BASE_URL/v1
PID_FILE="${XDG_STATE_HOME:-$HOME/.local/state}/llama-run.pid"

write_config() {
 cat >~/.config/opencode/opencode.jsonc <<'EOF'
{
  "$schema": "https://opencode.ai/config.json",
  "model": "local/qwen38-27b",
  "provider": {
    "local": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Local Llama",
      "options": {
        "baseURL": "http://127.0.0.1:8080/v1",
        "apiKey": "no-key"
      },
      "models": {
        "qwen38-27b": {
          "name": "Qwen 3.8 27B",
          "limit": {
            "context": 32768,
            "output": 4096
          }
        }
      }
    }
  }
}
EOF
}

running_pid() {
 if [ -f "$PID_FILE" ]; then
  local pid
  pid="$(cat "$PID_FILE")"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
   printf '%s' "$pid"
   return 0
  fi
  rm -f "$PID_FILE"
 fi
 return 1
}

server_up() {
 curl -sf "$BASE_URL/health" >/dev/null 2>&1
}

start() {
 if ! [ -f "$MODEL_FILE" ]; then
  printf 'error: model file not found: %s\n' "$MODEL_FILE" >&2
  return 1
 fi

 if running_pid; then
  printf 'llama server already running (pid %s)\n' "$(running_pid)"
  return 0
 fi

 if server_up; then
  printf 'error: a server is already listening on %s\n' "$BASE_URL" >&2
  printf 'hint: run %s --stop first\n' "$0" >&2
  return 1
 fi

 write_config
 printf 'starting llama server...\n'
 llama server -m "$MODEL_FILE" -c 1000000 &
 echo $! >"$PID_FILE"

 for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if server_up; then
   printf 'llama server ready at %s\n' "$SERVER_URL"
   return 0
  fi
  sleep 1
 done

 printf 'error: server did not become healthy\n' >&2
 return 1
}

stop() {
 local pid
 if pid="$(running_pid)"; then
  kill "$pid"
  rm -f "$PID_FILE"
  printf 'stopped llama server (pid %s)\n' "$pid"
  return 0
 fi

 if server_up || pgrep -f "llama server -m $MODEL_FILE" >/dev/null 2>&1; then
  pkill -f "llama server -m $MODEL_FILE"
  rm -f "$PID_FILE"
  printf 'stopped orphaned llama server\n'
  return 0
 fi

 printf 'llama server not running\n'
 return 0
}

usage() {
 printf 'usage: %s {--start|--stop|--restart}\n' "$0" >&2
}

case "${1:-}" in
 --start)
  start
  ;;
 --stop)
  stop
  ;;
 --restart)
  stop
  start
  ;;
 *)
  usage
  exit 1
  ;;
esac
