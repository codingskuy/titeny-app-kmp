# ADR-0002 — Governance branch: alur fork dan usulan proteksi `main`

- **Status**: Diterima
- **Tanggal**: 2026-10-21

## Konteks

Repo dikerjakan oleh manusia dan agen AI secara paralel. Perlu alur kontribusi yang seragam agar riwayat `main` bersih, tiap perubahan dapat ditinjau, dan blast radius terkendali (lihat `versions/v1.0.0/docs/60-BLAST-RADIUS.md`).

## Keputusan

1. Kontribusi melalui **fork -> branch -> atomic commit -> PR** ke `main` (rincian di `CONTRIBUTING.md`).
2. Penamaan branch: `feat|fix|test|docs|chore/<kode-tiket-lowercase>-<slug>`; satu branch = satu tiket = satu PR.
3. Commit conventional berbahasa Indonesia; `CHANGELOG.md` diperbarui sesuai AGENTS.md.
4. PR memakai `.github/pull_request_template.md`; `.github/CODEOWNERS` menetapkan `@Coding-Skuy` sebagai pemilik.
5. **Usulan** proteksi branch `main` (belum diterapkan; penerapan oleh admin org):
   - wajib PR dengan minimal 1 persetujuan, dismiss approval stale saat ada commit baru;
   - wajib review dari code owner;
   - wajib status check lulus (build + uji) sebelum merge, branch harus up-to-date;
   - larang force-push dan penghapusan `main`; riwayat linear (squash atau rebase merge);
   - aturan berlaku juga untuk admin.

## Konsekuensi

- Tidak ada perubahan pengaturan repo oleh ADR ini; usulan nomor 5 menunggu keputusan admin.
- Merge hanya oleh manusia setelah tinjauan.
