# Alur E2E: Maestro dan fallback desktop

Keputusan: [ADR-0005](../decisions/ADR-0005-uji-e2e-maestro-dan-fallback-desktop.md). Ringkas: Maestro tidak mendukung Compose Desktop.

## Yang benar-benar berjalan untuk desktop
```bash
./gradlew :analis-desktop:desktopTest --tests '*AlurUiTest*'
```
`AlurUiTest` menjalankan smoke masuk dan alur masuk, OTP, antrean, detail, keputusan Tolak (alasan 10-140 karakter, `X-Idempotency-Key` = `aksi_id`) dengan `testTag` yang sama dengan alur Maestro.

## Alur Maestro (siap, belum dieksekusi terhadap aplikasi)
`.maestro/smoke.yaml`, `.maestro/validasi-flag.yaml`, daftar di `.maestro/config.yaml`.
```bash
maestro check-syntax .maestro/smoke.yaml
TITENY_USERNAME=... TITENY_PASSWORD=... TITENY_OTP=... TITENY_EVENT_ID=evt_YYYYMMDD_NNNNNN maestro test .maestro
```
Perintah kedua butuh perangkat Android/iOS yang menjalankan aplikasi dengan `appId` `id.skuy.titeny.analis` (belum ada). Telemetri: `MAESTRO_CLI_NO_ANALYTICS=1`.

## Menambah `testTag`
Pakai `Modifier.testTag("<layar>-<elemen>")` (huruf kecil, tanda hubung); satu nama dipakai di Compose UI test dan `.maestro`.
