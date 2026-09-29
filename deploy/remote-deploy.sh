#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/uni-companion}"
cd "$APP_DIR"
git fetch origin main
git reset --hard origin/main
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build --remove-orphans
docker image prune -f
