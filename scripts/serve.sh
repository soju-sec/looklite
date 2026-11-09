#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-8765}"
echo "LookLite preview → http://127.0.0.1:${PORT}/web/"
exec python3 -m http.server "$PORT" --bind 127.0.0.1