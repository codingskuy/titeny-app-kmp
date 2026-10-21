# ADR-0003 — Versi toolchain KMP dikunci

- **Status**: Diterima
- **Tanggal**: 2026-10-22

## Konteks

`AGENTS.md` mencatat baseline FSD/30 (Kotlin 2.1.x, Compose 1.7.x, Navigation3 1.0.0) dan kode awal masih Kotlin 2.0.20 / Ktor 3.0.0. Navigation3 versi 1.1.x di Compose Multiplatform membutuhkan Compose Multiplatform 1.10.0, sehingga baseline lama tidak bisa dipertahankan. PR #13 menaikkan toolchain dan PR #14 menambah graf Navigation3; nomor ADR-0003 dipesan untuk keputusan ini (ADR-0002 = governance, PR #10).

Kenaikan versi ini disetujui dan dikunci oleh koordinator, yang meneruskan keputusan user lewat manajer divisi. Persetujuan tidak diterima langsung dari user oleh agen repo ini.

## Keputusan

Versi berikut dikunci persis dan hanya berubah lewat ADR baru:

| Komponen | Versi | Sumber di repo |
|---|---|---|
| Compose Multiplatform | 1.10.0 | `gradle/libs.versions.toml` (`compose`), PR #13 |
| Navigation3 | 1.1.x (aktual 1.1.2) | `navigation3`, PR #14 |
| kotlinx-datetime | 0.7.1 | belum dipakai kode; dipasang saat dependensi pertama ditambahkan |
| Kotlin | 2.2.20 | `kotlin`, PR #13 (prasyarat Compose 1.10.0) |
| Ktor | 3.1.3 | `ktor`, PR #13 |

Catatan: kode saat ini memakai `java.time` (JVM desktop). Bila kotlinx-datetime ditambahkan, wajib versi 0.7.1 dan zona waktu Asia/Jakarta sesuai aturan AGENTS.md.

## Konsekuensi

- `AGENTS.md` memuat pin versi yang sama (PR terpisah); selisih dari baseline FSD/30 dicatat di sini, bukan dengan mengubah dokumen beku TownHall.
- Bila persetujuan koordinator/user ternyata keliru atau berbeda, konfirmasi ke manusia lalu terbitkan ADR baru yang menggantikan ADR ini (ubah hanya baris Status ADR ini).
- Pemutakhiran dependensi otomatis (mis. Renovate) tidak boleh melewati versi yang dikunci di atas tanpa ADR.
