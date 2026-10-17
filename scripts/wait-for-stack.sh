#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://127.0.0.1:4200}"
attempts="${READY_ATTEMPTS:-60}"
payload='{"email":"admin@trendify.dev","password":"Trendify123!"}'

for ((attempt=1; attempt<=attempts; attempt++)); do
  status="$(curl --silent --output /tmp/trendify-ready.json --write-out '%{http_code}' \
    --connect-timeout 2 --max-time 5 \
    --header 'Content-Type: application/json' \
    --data "$payload" "$base_url/api/auth/login" || true)"
  if [[ "$status" == "200" ]] && grep -q 'accessToken' /tmp/trendify-ready.json; then
    echo "Trendify is ready after ${attempt} attempt(s)."
    exit 0
  fi
  echo "Waiting for Trendify through Nginx (attempt ${attempt}/${attempts}, HTTP ${status:-unreachable})..."
  sleep 2
done

echo "Trendify did not become ready at ${base_url}." >&2
docker compose ps >&2 || true
docker compose logs --no-color --tail=200 backend frontend postgres dynamodb-local ml-service >&2 || true
exit 1
