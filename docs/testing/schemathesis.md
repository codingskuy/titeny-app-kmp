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

## Hasil uji nyata (2026-10-22)
Dijalankan terhadap backend nyata (`titeny-backend-service` cabang `feat/ttn-be-005-aksi-analis-audit`, binari `cargo build` debug, Postgres lokal DB kosong `titeny_schemathesis`, `BIND_ADDR=127.0.0.1:18090`, `RATE_LIMIT_PER_MENIT=100000`), JWT analis HS256 dibuat lokal dari `JWT_SECRET` uji. schemathesis 4.32.2, `--max-examples 25 --checks all`, seed `305430071438268106300272022080819672789`.

Ringkasan: 14/14 operasi diuji, 1052 kasus dibangkitkan, 14 kegagalan unik, 0 respons 5xx. Temuan (untuk backend, bukan diperbaiki di repo ini):
- ST202 (9): permintaan sah menurut skema ditolak 422 karena batasan tak tertulis di spec: `cursor=""` (antrean, panen, rekomendasi), `zona` wajib di `/demand`, `pasar_id` wajib di `/harga`, `X-Idempotency-Key` harus sama dengan `aksi_id` (tahan), `nilai_baru` hanya untuk `koreksi_nilai` (validasi), `username` kosong (login), `sesi_id` kosong (otp). Saran: tambahkan `minLength`/`required`/`oneOf` di openapi.yaml.
- ST103 (2): status tak terdokumentasi: 422 pada `GET /analis/antrean?limit=201` (spec hanya 200/401/403/429/500) dan 404 pada `POST /umpan-balik` (komoditas tak dikenal).
- ST104/ST109 (1): `POST /rekomendasi/{id}/status` dengan id tak ada mengembalikan 404 berbadan kosong, bukan JSON galat standar.
- ST401 (1): kemungkinan positif palsu; `GET /rekomendasi` tanpa token/PIN dicek manual dengan curl = 401 (benar). Pemicu: skema keamanan alternatif (`zonaPin` atau `bearerAuth`) + header `-H` tetap.
- Peringatan: `POST /event/masuk` hanya 403 karena token berperan `analis` (perlu token `layanan`); `GET /harga` dan `/panen` selalu 404 karena DB kosong (tanpa seed prediksi). Cakupan dua operasi itu belum bermakna.
