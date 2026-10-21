#!/usr/bin/env bash
# Uji berbasis properti (Schemathesis) terhadap openapi.yaml titeny-backend-service.
# Env:
#   BASE_URL  alamat API (default http://localhost:8080 = `cargo run` / mock lokal)
#   SPEC      path/URL openapi.yaml; bila kosong diunduh dari repo backend via `gh api`
#   TOKEN     JWT bearer (opsional; JANGAN di-commit)
#   MAX_EXAMPLES  jumlah contoh per operasi (default 25)
# Telemetri dinonaktifkan; tidak ada data dikirim ke layanan eksternal.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_EXAMPLES="${MAX_EXAMPLES:-25}"
export SCHEMATHESIS_TELEMETRY=off E2E_TELEMETRY_DISABLED=1

command -v schemathesis >/dev/null || { echo "schemathesis belum terpasang: pip install schemathesis" >&2; exit 2; }

SPEC_FILE="${SPEC:-}"
if [[ -z "$SPEC_FILE" ]]; then
  SPEC_FILE="$(mktemp -t titeny-openapi.XXXXXX.yaml)"
  trap 'rm -f "$SPEC_FILE"' EXIT
  gh api repos/Coding-Skuy/titeny-backend-service/contents/docs/api/openapi.yaml \
    -H "Accept: application/vnd.github.raw" > "$SPEC_FILE"
fi

args=(run "$SPEC_FILE" --url "$BASE_URL" --max-examples "$MAX_EXAMPLES" --checks all)
[[ -n "${TOKEN:-}" ]] && args+=(-H "Authorization: Bearer ${TOKEN}")

exec schemathesis "${args[@]}"
