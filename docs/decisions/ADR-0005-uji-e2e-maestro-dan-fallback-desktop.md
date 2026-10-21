# ADR-0005 — Uji E2E: alur Maestro + fallback Compose UI test untuk desktop

- **Status**: Diterima
- **Tanggal**: 2026-10-22

## Konteks

Tiket TTN-KMP-TEST-MAESTRO (#9) meminta alur `.maestro/` (smoke + validasi flag) dengan selector `testTag`. Namun target v1.0.0 adalah Compose Desktop (Windows). Maestro (CLI 2.11.0 dipasang dan dicoba) menggerakkan perangkat Android, iOS, dan browser web; tidak ada driver untuk jendela aplikasi desktop JVM. Hasil `maestro test` di mesin ini: "You have 0 devices connected" (tidak ada target yang dapat dipakai, dan memang tidak ada jenis target desktop). Penomoran: ADR-0002 (PR #10), ADR-0003 (dipesan), ADR-0004 (Pact, PR #18).

## Keputusan

1. Alur `.maestro/smoke.yaml` dan `.maestro/validasi-flag.yaml` ditulis dengan selector `id:` yang sama dengan `testTag` Compose (`layar-masuk`, `masuk-username`, `antrean-baris-<eventId>`, `keputusan-Tolak`, `detail-kirim`, ...). Kredensial dan OTP lewat env (`TITENY_*`), tidak di repo. Sintaks diperiksa dengan `maestro check-syntax` (lulus). Alur ini TIDAK dieksekusi terhadap aplikasi; hanya siap dipakai bila `baca-mobile` (Android/iOS) hidup dan memberi `testTagsAsResourceId`.
2. Untuk desktop, alur yang sama dijalankan sebagai uji Compose UI (`AlurUiTest`, `runComposeUiTest`) memakai `testTag` yang sama, `ApiKlien` asli, dan MockEngine. Ini fallback yang benar-benar dijalankan di `desktopTest` (JVM, tanpa jendela nyata).
3. Navigation3 `NavDisplay` membutuhkan host jendela yang tidak ada di harness uji. Pemetaan rute ke layar diekstrak ke `IsiRute` dan kerangka ke `KerangkaAnalis` (dipakai aplikasi dan uji), sehingga uji menjalankan layar dan navigator sungguhan tanpa `NavDisplay`.

## Keterbatasan (jujur)

- Uji fallback tidak menguji `NavDisplay`, jendela, pintasan keyboard global, atau Credential Manager.
- Maestro web masih beta; tidak relevan untuk repo ini (klien web ada di repo web).
- Kebenaran selector `.maestro` terhadap aplikasi mobile belum terbukti karena belum ada aplikasi mobile.

## Alternatif ditolak

- Menjalankan Maestro lewat emulator Android dengan build terpisah: di luar lingkup, `baca-mobile` hanya stub.
- Otomasi jendela via AWT Robot / Sikuli: rapuh, butuh display nyata; tidak cocok untuk CI headless.
