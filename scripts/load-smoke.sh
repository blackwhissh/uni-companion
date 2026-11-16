#!/usr/bin/env bash
# Lightweight concurrent HTTP smoke against a running stack (NFR-021 baseline helper).
# Does not start services — run dev.ps1 / compose first.
#
# Usage:
#   ./scripts/load-smoke.sh
#   CONCURRENCY=20 REQUESTS=100 ./scripts/load-smoke.sh
set -euo pipefail

IDENTITY_URL="${IDENTITY_URL:-http://127.0.0.1:8081}"
LEARNING_URL="${LEARNING_URL:-http://127.0.0.1:8082}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@uni-companion.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin-pass-1}"
CONCURRENCY="${CONCURRENCY:-10}"
REQUESTS="${REQUESTS:-50}"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}
need curl
need python3

echo "==> Preflight health"
curl -fsS "${IDENTITY_URL}/actuator/health/liveness" >/dev/null
curl -fsS "${LEARNING_URL}/actuator/health/liveness" >/dev/null
echo "identity + learning liveness OK"

echo "==> Login once for token"
LOGIN_JSON="$(curl -fsS -X POST "${IDENTITY_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")"
TOKEN="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<<"${LOGIN_JSON}")"
if [[ -z "${TOKEN}" ]]; then
  echo "Login did not return a token" >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

worker() {
  local i="$1"
  local start end ms code
  start="$(date +%s%3N)"
  code="$(curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    "${LEARNING_URL}/api/courses" || echo 000)"
  end="$(date +%s%3N)"
  ms=$((end - start))
  printf '%s %s\n' "${code}" "${ms}" >"${WORKDIR}/${i}.txt"
}

export -f worker
export TOKEN LEARNING_URL WORKDIR

echo "==> ${REQUESTS} GETs /api/courses with concurrency ${CONCURRENCY}"
seq 1 "${REQUESTS}" | xargs -P "${CONCURRENCY}" -I{} bash -c 'worker "$@"' _ {}

python3 - <<'PY' "${WORKDIR}" "${REQUESTS}"
import pathlib, sys
workdir = pathlib.Path(sys.argv[1])
expected = int(sys.argv[2])
rows = []
for path in sorted(workdir.glob("*.txt")):
    code, ms = path.read_text().strip().split()
    rows.append((int(code), int(ms)))
if len(rows) != expected:
    print(f"Expected {expected} samples, got {len(rows)}", file=sys.stderr)
    sys.exit(1)
ok = [ms for code, ms in rows if code == 200]
fail = [code for code, ms in rows if code != 200]
ok.sort()
def pct(p):
    if not ok:
        return None
    idx = min(len(ok) - 1, max(0, int(round((p / 100) * (len(ok) - 1)))))
    return ok[idx]
avg = sum(ok) / len(ok) if ok else 0
print(f"ok={len(ok)}/{expected} fail={len(fail)}")
if fail:
    print(f"non-200 codes: {sorted(set(fail))}", file=sys.stderr)
print(f"latency_ms avg={avg:.1f} p50={pct(50)} p95={pct(95)} p99={pct(99)} max={ok[-1] if ok else 'n/a'}")
sys.exit(0 if len(ok) == expected else 1)
PY

echo "==> Load smoke finished"
