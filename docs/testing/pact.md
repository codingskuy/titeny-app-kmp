# Uji kontrak konsumen (Pact JVM)

Keputusan dan fallback: [ADR-0004](../decisions/ADR-0004-uji-kontrak-pact-jvm.md).

```bash
./gradlew :analis-desktop:desktopTest --tests '*PactKonsumenTest*'
```
Hasil: `pacts/titeny-analis-desktop-titeny-backend-service.json` (7 interaksi). Berkas ini diserahkan ke verifikasi provider di `titeny-backend-service` (tiket terpisah); `providerStates` di berkas menamai data awal yang harus disiapkan provider.

Menambah interaksi: tambahkan uji di `analis-desktop/src/desktopTest/kotlin/id/skuy/titeny/analis/kontrak/PactKonsumenTest.kt`, turunkan bentuknya dari `docs/api/openapi.yaml` backend, jalankan uji, commit berkas pact yang berubah.
