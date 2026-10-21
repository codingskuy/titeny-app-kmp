# Panduan Kontribusi — titeny-app-kmp

Baca dulu [AGENTS.md](./AGENTS.md) dan semua ADR di [`docs/decisions/`](./docs/decisions/). Dokumen `versions/v*` bersifat immutable.

## Alur kerja (fork -> branch -> atomic commit -> PR)

1. **Fork** repo ke akun sendiri, lalu clone fork dan tambahkan remote `upstream`:
   ```bash
   gh repo fork Coding-Skuy/titeny-app-kmp --clone=false
   gh repo clone <akun>/titeny-app-kmp && cd titeny-app-kmp
   git remote add upstream https://github.com/Coding-Skuy/titeny-app-kmp.git
   ```
2. **Branch** dari `upstream/main`, satu branch = satu tiket = satu PR:
   `feat/<kode-tiket>-<slug>`, `fix/…`, `test/…`, `docs/…`, `chore/…` (kode tiket huruf kecil, mis. `feat/ttn-kmp-003-antrean-validasi`).
3. **Atomic commit**: satu perubahan logis per commit, conventional commits berbahasa Indonesia (`feat:`, `fix:`, `docs:`, `test:`, `chore:`). Perbarui `CHANGELOG.md` pada commit `feat:`/`fix:`/`docs:`.
4. **Uji lokal** sebelum PR (TDD merah-hijau; QA gate tidak boleh skip test).
5. **Push** ke fork, buka **PR** ke `Coding-Skuy/titeny-app-kmp` base `main` memakai template PR; cantumkan `Closes #N`.
6. Tinjauan manusia wajib. Jangan merge PR sendiri dan jangan force-push ke `main`.

## Aturan

- Jangan commit rahasia (`.env`, kunci, token).
- Perubahan arsitektur/kontrak = ADR baru bernomor berurutan (ADR lama tidak diedit).
- Requirement berubah = PR di Titeny-TownHall lebih dulu.
- Perubahan di backend (`titeny-backend-service`) dilakukan lewat issue di repo tersebut, bukan dari repo ini.
