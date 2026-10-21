# ADR-0004 — Uji kontrak konsumen dengan Pact JVM (desktop)

- **Status**: Diterima
- **Tanggal**: 2026-10-22

## Konteks

Klien REST analis (`ApiKlien`, Ktor) mengonsumsi `docs/api/openapi.yaml` milik `titeny-backend-service`. Kontrak berubah lewat PR backend; klien perlu tanda dini bila bentuk permintaan/respons yang dipakai berubah. Tiket: TTN-KMP-TEST-PACT (#7).

Nomor ADR: ADR-0002 (governance, PR #10) dan ADR-0003 (versi toolchain, usulan terpisah) sudah dipesan; ADR ini memakai nomor berikutnya agar tidak ganda.

## Keputusan

1. **Pact JVM 4.6.21** (`au.com.dius.pact.consumer:junit`) hanya di source set `desktopTest` (target `jvm("desktop")`). Pact JVM berjalan di JVM biasa, sehingga layak untuk Compose Desktop; tidak ada klien Pact untuk target KMP non-JVM, jadi `commonTest` tidak dipakai.
2. Uji memakai `ApiKlien` asli dengan engine Ktor CIO melawan mock server Pact (HTTP sungguhan), bukan MockEngine, supaya header, query, dan serialisasi benar-benar diperiksa.
3. Berkas pact (V3) ditulis ke `pacts/titeny-analis-desktop-titeny-backend-service.json` dan di-commit sebagai artefak kontrak. Dihapus dan dibuat ulang tiap `desktopTest` agar tak ada interaksi basi.
4. Isi interaksi diturunkan dari openapi.yaml (login, OTP, antrean, validasi sukses dan 422, tahan, harga). Pencocokan memakai matcher tipe/regex, bukan nilai literal, kecuali enum dan kunci idempotensi.
5. Verifikasi provider (menjalankan pact terhadap backend) adalah tiket di repo backend; pact ini adalah masukannya.

## Fallback bila Pact tidak dipakai

Bila kelak klien dipindah ke target non-JVM (iOS, JS) atau Pact JVM tak lagi dipelihara, kontrak dijaga oleh dua lapis yang sudah ada: Schemathesis terhadap openapi.yaml (`contract/schemathesis.sh`, sisi provider) dan uji `ApiKlienTest` dengan MockEngine (sisi konsumen).

## Konsekuensi

- Dependensi uji bertambah (Pact + transitifnya); tidak masuk ke artefak aplikasi (hanya `desktopTest`).
- Perubahan kontrak di backend yang merusak klien terdeteksi di PR backend setelah verifikasi provider tersambung (belum ada; lihat butir 5).
- Pact tidak membuktikan kecocokan dengan openapi.yaml secara otomatis; kecocokan dijaga manual dan oleh Schemathesis.
