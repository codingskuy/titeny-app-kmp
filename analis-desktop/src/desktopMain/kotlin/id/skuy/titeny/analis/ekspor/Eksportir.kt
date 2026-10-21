package id.skuy.titeny.analis.ekspor

import id.skuy.titeny.analis.api.FormatJson
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.SumberDataEkspor
import id.skuy.titeny.analis.db.AnalisDb
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** Jenis ekspor (nama berkas memakai [kode]). */
enum class JenisEkspor(val kode: String, val label: String, val tersedia: Boolean, val catatan: String? = null) {
    HargaH7("harga_h7", "Harga H+7", true),
    Demand("demand", "Demand", true),
    Panen("panen", "Panen", true),
    Akurasi(
        "akurasi", "Jejak akurasi", false,
        "Belum ada endpoint akurasi/MAPE di openapi.yaml (gap kontrak); tidak dapat diekspor.",
    ),
}

/**
 * Daftar ID untuk memanggil API. GAP kontrak: openapi.yaml tidak punya endpoint katalog, jadi daftar
 * pasar (`psr_*`) dan zona (demand) harus disediakan dari berkas `katalog.json` (lihat [dariBerkas]).
 * Komoditas dan kabupaten diturunkan dari nama yang tercantum di openapi.yaml sebagai `cmd_<slug>`/`kab_<slug>`
 * (pola ID di kontrak; hanya `cmd_cabai_merah` dan `kab_karawang` diberi contoh: ASUMSI, verifikasi dengan BE).
 */
@Serializable
data class KatalogEkspor(
    val komoditasHarga: List<String> = KOMODITAS_HARGA,
    val komoditasPanen: List<String> = KOMODITAS_PANEN,
    val kabupaten: List<String> = KABUPATEN,
    val pasar: List<String> = emptyList(),
    val zona: List<String> = emptyList(),
) {
    companion object {
        private fun id(awalan: String, nama: String) = awalan + "_" + nama.lowercase().replace(' ', '_')
        val KOMODITAS_HARGA = listOf(
            "beras medium", "telur ayam", "cabai merah", "cabai rawit", "bawang merah", "bawang putih",
            "minyak goreng curah", "gula pasir", "daging ayam", "daging sapi", "tomat", "kangkung",
        ).map { id("cmd", it) }
        val KOMODITAS_PANEN = listOf("padi", "jagung", "cabai merah", "cabai rawit", "bawang merah", "tomat").map { id("cmd", it) }
        val KABUPATEN = listOf("karawang", "subang", "garut", "brebes", "malang").map { id("kab", it) }

        /** Baca `katalog.json` (sama dengan bentuk kelas ini); berkas tidak ada = bawaan (pasar/zona kosong). */
        fun dariBerkas(berkas: Path): KatalogEkspor =
            if (Files.exists(berkas)) FormatJson.json.decodeFromString(serializer(), Files.readString(berkas)) else KatalogEkspor()
    }
}

data class BerkasEkspor(val nama: String, val path: Path, val jumlahBaris: Int, val sha256: String)

sealed interface HasilEkspor {
    data class Berhasil(val berkas: List<BerkasEkspor>, val manifest: Path) : HasilEkspor
    /** Tidak ada baris yang dapat diekspor (data kosong atau katalog tidak lengkap). */
    data class Kosong(val alasan: String) : HasilEkspor
    data class Gagal(val pesan: String) : HasilEkspor
}

private data class Tabel(val kolom: List<String>, val baris: List<List<Sel>>)

/**
 * Ekspor massal beraudit: ambil data dari API (hanya baca), tulis CSV/XLSX per <= 50.000 baris,
 * hitung SHA-256 dari byte berkas yang ditulis, catat nama + hash di tabel append-only `jejak_ekspor`
 * dan manifest `.sha256` di samping berkas. Klien tidak mengubah data sumber.
 */
class Eksportir(
    private val sumber: SumberDataEkspor,
    private val db: AnalisDb,
    private val folder: Path,
    private val sekarang: () -> LocalDateTime = { LocalDateTime.now(ZoneId.of("Asia/Jakarta")) },
    private val epochMs: () -> Long = System::currentTimeMillis,
    /** Batas server: 60 permintaan/menit per token, jadi jeda antarpanggilan >= 1 detik. */
    private val jedaMs: Long = 1_100L,
    private val maksBarisPerBerkas: Int = MAKS_BARIS_PER_BERKAS,
    private val maksUlang429: Int = 3,
) {
    suspend fun ekspor(
        jenis: JenisEkspor,
        format: FormatEkspor,
        katalog: KatalogEkspor,
        kemajuan: (selesai: Int, total: Int) -> Unit = { _, _ -> },
    ): HasilEkspor {
        if (!jenis.tersedia) return HasilEkspor.Gagal(jenis.catatan ?: "Jenis ekspor tidak tersedia.")
        val tabel = try {
            ambil(jenis, katalog, kemajuan)
        } catch (e: GalatAmbil) {
            return HasilEkspor.Gagal(e.message ?: "Gagal mengambil data.")
        }
        if (tabel.baris.isEmpty()) {
            return HasilEkspor.Kosong(
                if (jenis == JenisEkspor.HargaH7 && katalog.pasar.isEmpty() || jenis == JenisEkspor.Demand && katalog.zona.isEmpty()) {
                    "Katalog pasar/zona kosong (belum ada endpoint katalog): isi katalog.json."
                } else {
                    "Tidak ada data untuk diekspor."
                },
            )
        }
        return try {
            tulis(jenis, format, tabel)
        } catch (e: java.io.IOException) {
            HasilEkspor.Gagal("Gagal menulis berkas: ${e.message}")
        }
    }

    private fun tulis(jenis: JenisEkspor, format: FormatEkspor, tabel: Tabel): HasilEkspor {
        Files.createDirectories(folder)
        val waktu = sekarang()
        val bagian = tabel.baris.chunked(maksBarisPerBerkas)
        val berkas = bagian.mapIndexed { i, baris ->
            val nama = namaBerkas(jenis.kode, waktu, format, i + 1)
            val data = when (format) {
                FormatEkspor.Csv -> PenulisCsv.tulis(tabel.kolom, baris)
                FormatEkspor.Xlsx -> PenulisXlsx.tulis(tabel.kolom, baris)
            }
            val path = folder.resolve(nama)
            Files.write(path, data)
            BerkasEkspor(nama, path, baris.size, sha256Hex(data))
        }
        // Jejak audit (nama berkas + SHA-256): satu transaksi untuk seluruh bagian.
        db.transaction {
            berkas.forEach { db.analisQueries.catatEkspor(it.nama, jenis.kode, format.ekstensi, it.jumlahBaris.toLong(), it.sha256, epochMs()) }
        }
        // Manifest format `sha256sum`: "<hash>  <nama>".
        val manifest = folder.resolve(namaBerkas(jenis.kode, waktu, format).substringBeforeLast('.') + ".sha256")
        Files.writeString(manifest, berkas.joinToString("") { "${it.sha256}  ${it.nama}\n" })
        return HasilEkspor.Berhasil(berkas, manifest)
    }

    private class GalatAmbil(pesan: String) : Exception(pesan)

    private suspend fun ambil(jenis: JenisEkspor, k: KatalogEkspor, kemajuan: (Int, Int) -> Unit): Tabel = when (jenis) {
        JenisEkspor.HargaH7 -> {
            val pasangan = k.komoditasHarga.flatMap { c -> k.pasar.map { p -> c to p } }
            val baris = mutableListOf<List<Sel>>()
            pasangan.forEachIndexed { i, (c, p) ->
                jeda(i)
                val r = panggil { sumber.harga(c, p) }
                // 404 (`data_belum_cukup` atau pasangan tak dikenal) = tidak ada data: dilewati, bukan galat ekspor.
                if (r != null) r.deret.filter { it.horizon == "H+7" }.forEach {
                    baris += listOf(r.komoditasId, r.pasarId, it.tanggal, it.horizon, it.hargaPrediksi, it.rentangBawah, it.rentangAtas, it.kepercayaan, r.modelVersion, r.dihitungPada)
                }
                kemajuan(i + 1, pasangan.size)
            }
            Tabel(listOf("komoditas_id", "pasar_id", "tanggal", "horizon", "harga_prediksi_idr", "rentang_bawah_idr", "rentang_atas_idr", "kepercayaan", "model_version", "dihitung_pada"), baris)
        }
        JenisEkspor.Demand -> {
            val pasangan = k.komoditasHarga.flatMap { c -> k.zona.map { z -> c to z } }
            val baris = mutableListOf<List<Sel>>()
            pasangan.forEachIndexed { i, (c, z) ->
                jeda(i)
                panggil { sumber.demand(c, z) }?.let {
                    baris += listOf(it.komoditasId, it.zona, it.tanggal, it.horizon, it.demandPrediksiKg, it.rentangBawahKg, it.rentangAtasKg, it.sinyal, it.rekomendasi.joinToString(" | "), it.modelVersion)
                }
                kemajuan(i + 1, pasangan.size)
            }
            Tabel(listOf("komoditas_id", "zona", "tanggal", "horizon", "demand_prediksi_kg", "rentang_bawah_kg", "rentang_atas_kg", "sinyal", "rekomendasi", "model_version"), baris)
        }
        JenisEkspor.Panen -> {
            val baris = mutableListOf<List<Sel>>()
            var n = 0
            k.komoditasPanen.forEachIndexed { i, c ->
                var cursor: String? = null
                do {
                    jeda(n++)
                    val r = panggil { sumber.panen(c, cursor = cursor) }
                    r?.deret?.forEach {
                        baris += listOf(it.komoditasId, it.kabupatenId, it.minggu, it.panenPrediksiKg, it.rentangBawahKg, it.rentangAtasKg, it.kepercayaan, it.modelVersion)
                    }
                    cursor = r?.nextCursor
                } while (cursor != null)
                kemajuan(i + 1, k.komoditasPanen.size)
            }
            Tabel(listOf("komoditas_id", "kabupaten_id", "minggu", "panen_prediksi_kg", "rentang_bawah_kg", "rentang_atas_kg", "kepercayaan", "model_version"), baris)
        }
        JenisEkspor.Akurasi -> throw GalatAmbil(jenis.catatan.orEmpty())
    }

    private suspend fun jeda(indeks: Int) {
        if (indeks > 0 && jedaMs > 0) delay(jedaMs)
    }

    /** Panggil API; 404 -> null (lewati); 429 -> tunggu Retry-After lalu ulang; galat lain menghentikan ekspor. */
    private suspend fun <T> panggil(blok: suspend () -> Hasil<T>): T? {
        var percobaan = 0
        while (true) {
            when (val r = blok()) {
                is Hasil.Berhasil -> return r.data
                is Hasil.Jaringan -> throw GalatAmbil("Tidak dapat terhubung ke server; ekspor dibatalkan (tidak ada berkas parsial).")
                is Hasil.Galat -> when {
                    r.status == 404 -> return null
                    r.status == 429 && percobaan < maksUlang429 -> {
                        percobaan++
                        delay((r.retryAfterDetik ?: 5) * 1000L)
                    }
                    else -> throw GalatAmbil("Server menolak (${r.status}): ${r.pesan}")
                }
            }
        }
    }
}
