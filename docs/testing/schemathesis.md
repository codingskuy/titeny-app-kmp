# Uji properti Schemathesis

Menguji `docs/api/openapi.yaml` milik `titeny-backend-service` (hanya dibaca; kode backend tidak diubah).

## Pakai lokal
```bash
pip install schemathesis            # disarankan di venv
gh auth login                       # untuk mengunduh spec dari repo backend
BASE_URL=http://localhost:8080 contract/schemathesis.sh
# opsi: SPEC=/path/openapi.yaml  TOKEN=<jwt>  MAX_EXAMPLES=50
```
`BASE_URL` harus menunjuk ke server mock atau lokal (`cargo run` di repo backend), bukan produksi.

## CI
Workflow manual `.github/workflows/schemathesis.yml` (`workflow_dispatch`). Secret opsional: `BACKEND_READ_TOKEN` (baca repo backend), `TITENY_TEST_JWT`. Tidak ada rahasia di repo.

## Catatan
- Kegagalan (5xx, respons tak sesuai skema) dilaporkan sebagai issue di repo backend.
- Telemetri dimatikan (`SCHEMATHESIS_TELEMETRY=off`).
