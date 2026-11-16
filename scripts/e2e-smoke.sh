#!/usr/bin/env bash
# Start Postgres + identity + learning (fake AI), then run Playwright smoke tests.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

IDENTITY_PID=""
LEARNING_PID=""
PG_STARTED_BY_US=0

cleanup() {
  if [[ -n "${IDENTITY_PID}" ]] && kill -0 "${IDENTITY_PID}" 2>/dev/null; then
    kill "${IDENTITY_PID}" 2>/dev/null || true
    wait "${IDENTITY_PID}" 2>/dev/null || true
  fi
  if [[ -n "${LEARNING_PID}" ]] && kill -0 "${LEARNING_PID}" 2>/dev/null; then
    kill "${LEARNING_PID}" 2>/dev/null || true
    wait "${LEARNING_PID}" 2>/dev/null || true
  fi
  if [[ "${PG_STARTED_BY_US}" -eq 1 ]]; then
    docker compose stop postgres >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local name="$2"
  local attempts="${3:-60}"
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "${name} is up"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for ${name} at ${url}" >&2
  return 1
}

echo "==> Postgres"
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose)." >&2
  exit 1
fi
if ! docker compose ps --status running 2>/dev/null | grep -Eq 'uni-companion-postgres|postgres'; then
  docker compose up -d postgres
  PG_STARTED_BY_US=1
fi
ready=0
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U unicompanion -d postgres >/dev/null 2>&1; then
    echo "Postgres is ready"
    ready=1
    break
  fi
  sleep 2
done
if [[ "${ready}" -ne 1 ]]; then
  echo "Postgres did not become ready" >&2
  exit 1
fi

echo "==> Build services"
mvn -B -pl services/identity-service,services/learning-service -am package -DskipTests

IDENTITY_JAR="$(ls -1 "$ROOT"/services/identity-service/target/identity-service-*.jar | head -n1)"
LEARNING_JAR="$(ls -1 "$ROOT"/services/learning-service/target/learning-service-*.jar | head -n1)"

echo "==> Start identity-service"
java -jar "$IDENTITY_JAR" \
  --spring.profiles.active=local \
  >"$ROOT"/target-e2e-identity.log 2>&1 &
IDENTITY_PID=$!

echo "==> Start learning-service (fake AI)"
SPRING_PROFILES_ACTIVE=local,fake java -jar "$LEARNING_JAR" \
  --spring.profiles.active=local,fake \
  >"$ROOT"/target-e2e-learning.log 2>&1 &
LEARNING_PID=$!

wait_http "http://127.0.0.1:8081/actuator/health" "identity-service"
wait_http "http://127.0.0.1:8082/actuator/health" "learning-service"

echo "==> Playwright smoke"
cd "$ROOT/apps/web"
if [[ ! -d node_modules ]]; then
  npm ci
fi
npx playwright install --with-deps chromium
CI="${CI:-}" npm run test:e2e

echo "==> E2E smoke passed"
