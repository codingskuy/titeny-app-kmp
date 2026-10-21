# CHANGELOG — titeny-app-kmp

Format: satu seksi per rilis, terbaru di atas. Wajib diperbarui pada setiap commit `feat:` / `fix:` / `docs:`.

## [Belum dirilis]

### Diubah
- Naikkan toolchain ke baseline FSD/30: Kotlin 2.2.20, Compose Multiplatform 1.10.0 (prasyarat Navigation3 1.1.x), Ktor 3.1.3, coroutines 1.10.2, AGP 8.7.2, Gradle 8.14.3.

### Ditambahkan
- TTN-KMP-003: klien REST analis (login+OTP, refresh), layar Antrean Validasi (polling 5 menit + Segarkan) dan Detail Flag (setuju/koreksi_nilai/tolak, alasan 10-140), `POST /api/v1/analis/validasi` idempoten dengan `aksi_id`.
- TTN-KMP-002: graf Navigation3 desktop analis 7 layar (Masuk, AntreanValidasi, DetailFlag, TahanPrediksi, BandingModel, KelolaRekomendasi, Ekspor), deep link `titeny://analis/flag/<eventId>`, back stack maks 20, pintasan Ctrl+R/Ctrl+E/Esc, jendela min 1120x720.
- Gradle wrapper 8.10.2 (`gradlew`), plugin Compose compiler, `packageVersion` 1.0.0 (untuk CI TTN-OPS-002 dan rilis MSI TTN-OPS-005); sumber desktop dipindah ke `src/desktopMain`.
- Fondasi AI-dev v1.0.0: `AGENTS.md`, `CLAUDE.md`, `docs/decisions/ADR-0001-fondasi-teknis.md`, `versions/v1.0.0/docs/40-TECHSTACK.md` dan `60-BLAST-RADIUS.md` (versi lengkap, menggantikan ringkasan awal).
