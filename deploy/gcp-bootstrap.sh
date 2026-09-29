#!/usr/bin/env bash
set -euo pipefail

# One-time setup on the Google Cloud Ubuntu VM.
# Usage (after cloning the repo on the VM):
#   sudo bash /opt/uni-companion/deploy/gcp-bootstrap.sh

REPO_URL="${REPO_URL:-https://github.com/blackwhissh/uni-companion.git}"
APP_DIR="${APP_DIR:-/opt/uni-companion}"
DEPLOY_USER="${SUDO_USER:-${USER:-ubuntu}}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root (sudo)." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git openssl ufw

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi

usermod -aG docker "$DEPLOY_USER" || true
systemctl enable --now docker

if ufw status | grep -q "Status: active"; then
  ufw allow OpenSSH
  ufw allow 80/tcp
  ufw allow 443/tcp
fi

mkdir -p "$APP_DIR" "$APP_DIR/secrets"
if [[ ! -d "$APP_DIR/.git" ]]; then
  git clone "$REPO_URL" "$APP_DIR"
else
  git -C "$APP_DIR" fetch origin
  git -C "$APP_DIR" reset --hard origin/main
fi

if [[ ! -f "$APP_DIR/.env.prod" ]]; then
  cp "$APP_DIR/deploy/.env.prod.example" "$APP_DIR/.env.prod"
  JWT="$(openssl rand -base64 48 | tr -d '\n')"
  DBPASS="$(openssl rand -base64 24 | tr -d '\n')"
  ADMINPASS="$(openssl rand -base64 18 | tr -d '\n')"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${JWT}|" "$APP_DIR/.env.prod"
  sed -i "s|^DATABASE_PASSWORD=.*|DATABASE_PASSWORD=${DBPASS}|" "$APP_DIR/.env.prod"
  sed -i "s|^SEED_ADMIN_PASSWORD=.*|SEED_ADMIN_PASSWORD=${ADMINPASS}|" "$APP_DIR/.env.prod"
  PUBLIC_IP="$(curl -fsS --max-time 5 https://ifconfig.me || true)"
  if [[ -n "$PUBLIC_IP" ]]; then
    sed -i "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=http://${PUBLIC_IP}|" "$APP_DIR/.env.prod"
  fi
  echo "Wrote $APP_DIR/.env.prod with generated secrets."
  echo "Admin password is in SEED_ADMIN_PASSWORD in that file."
fi

chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"
chmod +x "$APP_DIR/deploy/remote-deploy.sh" "$APP_DIR/deploy/gcp-bootstrap.sh"

echo "Building and starting the stack (first Java image build can take 10–20 minutes)..."
cd "$APP_DIR"
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

echo
echo "Uni Companion is starting."
echo "Open: http://$(curl -fsS --max-time 5 https://ifconfig.me || echo YOUR_PUBLIC_IP)"
echo "Confirm VPC firewall allows TCP 22, 80, and 443 (http-server / https-server tags)."
echo "Logins: admin / value of SEED_ADMIN_PASSWORD in $APP_DIR/.env.prod"
echo "Also seeded: admin/admin and student/student unless you changed identity seed data."
