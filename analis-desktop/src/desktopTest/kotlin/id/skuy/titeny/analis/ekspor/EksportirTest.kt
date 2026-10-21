package id.skuy.titeny.analis.ekspor

import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.PrediksiDemand
import id.skuy.titeny.analis.api.PrediksiHarga
import id.skuy.titeny.analis.api.PrediksiHargaRespons
import id.skuy.titeny.analis.api.PrediksiPanen
import id.skuy.titeny.analis.api.PrediksiPanenRespons
import id.skuy.titeny.analis.api.SumberDataEkspor
import id.skuy.titeny.analis.cache.Basisdata
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EksportirTest {
    private val waktu = LocalDateTime.of(2026, 10, 4, 9, 5)
    private val katalog = KatalogEkspor(
        komoditasHarga = listOf("cmd_cabai_merah", "cmd_tomat"),
        komoditasPanen = listOf("cmd_padi"),
        pasar = listOf("psr_a", "psr_b"),
        zona = listOf("bekasi_utara"),
    )

    private class Palsu : SumberDataEkspor {
        var hargaHandler: (String, String) -> Hasil<PrediksiHargaRespons> = { c, p ->
            Hasil.Berhasil(
                PrediksiHargaRespons(
                    c, p,
                    (1..7).map { PrediksiHarga("2026-10-%02d".format(it + 4), "H+$it", 40000L + it * 500, 39000, 46000, "sedang") },
                    "harga_v1.0.0", "2026-10-04T04:00:12+07:00",
                ),
            )
        }
        val waktuPanggil = mutableListOf<Long>()
        var jam: () -> Long = { 0 }
        var halamanPanen = 0
        override suspend fun harga(komoditasId: String, pasarId: String, dari: String?, sampai: String?): Hasil<PrediksiHargaRespons> {
            waktuPanggil += jam()
            return hargaHandler(komoditasId, pasarId)
        }
        override suspend fun demand(komoditasId: String, zona: String, horizon: String): Hasil<PrediksiDemand> =
            Hasil.Berhasil(PrediksiDemand(komoditasId, zona, "2026-10-04", horizon, 840.0, 700.0, 900.5, "defisit", listOf("Amankan pasokan.", "Naikkan buffer."), "demand_v1.0.0"))
        override suspend fun panen(komoditasId: String, kabupatenId: String?, cursor: String?, limit: Int): Hasil<PrediksiPanenRespons> {
            halamanPanen++
            return Hasil.Berhasil(
                if (cursor == null) PrediksiPanenRespons(listOf(PrediksiPanen(komoditasId, "kab_karawang", "2026-W41", 1250, 1000, 1500, "tinggi", "panen_v1.0.0")), null, "c2")
                else PrediksiPanenRespons(listOf(PrediksiPanen(komoditasId, "kab_subang", "2026-W41", 900, null, null, "rendah", "panen_v1.0.0")), null, null),
            )
        }
    }

    private inline fun uji(maksBaris: Int = MAKS_BARIS_PER_BERKAS, blok: (Eksportir, Palsu, Basisdata, Path) -> Unit) {
        val dir = Files.createTempDirectory("titeny-ekspor")
        try {
            Basisdata.buka(null).use { b ->
                val p = Palsu()
                blok(Eksportir(p, b.db, dir.resolve("Ekspor"), { waktu }, { 1234L }, jedaMs = 0, maksBarisPerBerkas = maksBaris), p, b, dir.resolve("Ekspor"))
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun eksporHargaH7CsvMenulisBerkasHashDanJejakAudit() = runTest {
        uji { e, _, b, folder ->
            val h = assertIs<HasilEkspor.Berhasil>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, katalog) })
            val f = h.berkas.single()
            assertEquals("titeny_harga_h7_20261004_0905.csv", f.nama)
            assertEquals(4, f.jumlahBaris) // 2 komoditas x 2 pasar, hanya horizon H+7
            val bytes = Files.readAllBytes(folder.resolve(f.nama))
            assertEquals(sha256Hex(bytes), f.sha256) // hash dari byte yang benar-benar tertulis
            val isi = bytes.decodeToString().removePrefix("﻿").trimEnd().lines()
            assertEquals(5, isi.size)
            assertTrue(isi.drop(1).all { ",H+7," in it && "43500" in it }, isi.joinToString("\n"))

            val jejak = b.db.analisQueries.semuaEkspor().executeAsList()
            assertEquals(1, jejak.size)
            assertEquals(f.sha256, jejak.single().sha256)
            assertEquals("titeny_harga_h7_20261004_0905.csv", jejak.single().nama_berkas)
            assertEquals(4L, jejak.single().jumlah_baris)
            assertEquals("${f.sha256}  ${f.nama}\n", Files.readString(h.manifest))
        }
    }

    @Test
    fun eksporXlsxHashSamaDenganByteBerkas() = runTest {
        uji { e, _, _, folder ->
            val h = assertIs<HasilEkspor.Berhasil>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.Demand, FormatEkspor.Xlsx, katalog) })
            val f = h.berkas.single()
            assertEquals("titeny_demand_20261004_0905.xlsx", f.nama)
            assertEquals(2, f.jumlahBaris)
            val bytes = Files.readAllBytes(folder.resolve(f.nama))
            assertEquals(sha256Hex(bytes), f.sha256)
            val sheet = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                generateSequence { z.nextEntry }.first { it.name.endsWith("sheet1.xml") }.let { z.readBytes().decodeToString() }
            }
            assertTrue("<v>840</v>" in sheet && "<v>900.5</v>" in sheet && "Amankan pasokan. | Naikkan buffer." in sheet, sheet)
        }
    }

    @Test
    fun lebihDariBatasBarisDipecahDanSetiapBagianDiauditTerpisah() = runTest {
        uji(maksBaris = 3) { e, _, b, folder ->
            val h = assertIs<HasilEkspor.Berhasil>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, katalog) })
            assertEquals(listOf(3, 1), h.berkas.map { it.jumlahBaris })
            assertEquals(listOf("titeny_harga_h7_20261004_0905.csv", "titeny_harga_h7_20261004_0905_2.csv"), h.berkas.map { it.nama })
            assertTrue(h.berkas.all { Files.exists(folder.resolve(it.nama)) })
            assertEquals(2, b.db.analisQueries.semuaEkspor().executeAsList().size)
            assertEquals(2, Files.readAllLines(h.manifest).size)
        }
    }

    @Test
    fun panenMengikutiCursorDanMenulisDesimalKosong() = runTest {
        uji { e, p, _, _ ->
            val h = assertIs<HasilEkspor.Berhasil>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.Panen, FormatEkspor.Csv, katalog) })
            assertEquals(2, h.berkas.single().jumlahBaris)
            assertEquals(2, p.halamanPanen)
        }
    }

    @Test
    fun jejakAkurasiTidakTersediaDanTidakMenulisApaPun() = runTest {
        uji { e, _, b, folder ->
            val h = kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.Akurasi, FormatEkspor.Csv, katalog) }
            assertIs<HasilEkspor.Gagal>(h)
            assertTrue(!Files.exists(folder))
            assertEquals(0, b.db.analisQueries.semuaEkspor().executeAsList().size)
        }
    }

    @Test
    fun katalogPasarKosongMenghasilkanKosongBukanBerkasKosong() = runTest {
        uji { e, _, b, folder ->
            val h = kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, KatalogEkspor(pasar = emptyList())) }
            assertTrue(assertIs<HasilEkspor.Kosong>(h).alasan.contains("katalog.json"))
            assertTrue(!Files.exists(folder))
            assertEquals(0, b.db.analisQueries.semuaEkspor().executeAsList().size)
        }
    }

    @Test
    fun galatJaringanMembatalkanTanpaBerkasParsial() = runTest {
        uji { e, p, b, folder ->
            p.hargaHandler = { _, _ -> Hasil.Jaringan(IOException("putus")) }
            assertIs<HasilEkspor.Gagal>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, katalog) })
            assertTrue(!Files.exists(folder))
            assertEquals(0, b.db.analisQueries.semuaEkspor().executeAsList().size)
        }
    }

    @Test
    fun data404DilewatiBukanGalat() = runTest {
        uji { e, p, _, _ ->
            p.hargaHandler = { c, pasar -> if (pasar == "psr_b") Hasil.Galat(404, "data_belum_cukup", "x") else Palsu().hargaHandler(c, pasar) }
            val h = assertIs<HasilEkspor.Berhasil>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, katalog) })
            assertEquals(2, h.berkas.single().jumlahBaris)
        }
    }

    @Test
    fun galat403MenghentikanEkspor() = runTest {
        uji { e, p, _, _ ->
            p.hargaHandler = { _, _ -> Hasil.Galat(403, "tidak_berhak", "Peran tidak diizinkan.") }
            assertIs<HasilEkspor.Gagal>(kotlinx.coroutines.runBlocking { e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, katalog) })
        }
    }

    @Test
    fun batasLajuServerDihormatiJedaDan429() = runTest {
        val dir = Files.createTempDirectory("titeny-ekspor")
        try {
            Basisdata.buka(null).use { b ->
                val p = Palsu().also { it.jam = { currentTime } }
                var gagalPertama = true
                val asli = p.hargaHandler
                p.hargaHandler = { c, pasar ->
                    if (gagalPertama) { gagalPertama = false; Hasil.Galat(429, "kuota_habis", "x", retryAfterDetik = 7) } else asli(c, pasar)
                }
                val e = Eksportir(p, b.db, dir.resolve("e"), { waktu }, { 1L }, jedaMs = 1_100L)
                val h = e.ekspor(JenisEkspor.HargaH7, FormatEkspor.Csv, KatalogEkspor(komoditasHarga = listOf("cmd_a"), pasar = listOf("psr_a", "psr_b", "psr_c")))
                assertIs<HasilEkspor.Berhasil>(h)
                // panggilan: t=0 (429), ulang setelah 7 dtk, lalu jeda 1,1 dtk antarpasangan
                assertEquals(listOf(0L, 7_000L, 8_100L, 9_200L), p.waktuPanggil)
                // rata-rata < 60 permintaan/menit
                assertTrue(p.waktuPanggil.zipWithNext { a, c -> c - a }.drop(1).all { it >= 1_100 })
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun katalogBawaanMemuat12KomoditasHarga6Panen5KabupatenDariKontrak() {
        val k = KatalogEkspor()
        assertEquals(12, k.komoditasHarga.size)
        assertEquals(6, k.komoditasPanen.size)
        assertEquals(5, k.kabupaten.size)
        assertTrue("cmd_cabai_merah" in k.komoditasHarga && "kab_karawang" in k.kabupaten)
        assertTrue(k.komoditasHarga.all { Regex("^cmd_[a-z_]+$").matches(it) } && k.kabupaten.all { Regex("^kab_[a-z_]+$").matches(it) })
    }

    @Test
    fun katalogDariBerkasMembacaPasarDanZona() {
        val dir = Files.createTempDirectory("titeny-kat")
        try {
            val f = dir.resolve("katalog.json")
            Files.writeString(f, """{"pasar":["psr_jakarta_timur_01"],"zona":["bekasi_utara"]}""")
            val k = KatalogEkspor.dariBerkas(f)
            assertEquals(listOf("psr_jakarta_timur_01"), k.pasar)
            assertEquals(listOf("bekasi_utara"), k.zona)
            assertEquals(12, k.komoditasHarga.size)
            assertEquals(emptyList(), KatalogEkspor.dariBerkas(dir.resolve("tidak-ada.json")).pasar)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun migrasiV1KeV2MenambahTabelJejakEkspor() {
        val dir = Files.createTempDirectory("titeny-mig")
        try {
            val berkas = dir.resolve("analis.db")
            // Simulasikan DB lama v1 (tanpa jejak_ekspor).
            val lama = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver("jdbc:sqlite:$berkas")
            lama.execute(null, "CREATE TABLE meta (kunci TEXT NOT NULL PRIMARY KEY, nilai TEXT NOT NULL)", 0)
            lama.execute(null, "PRAGMA user_version = 1", 0)
            lama.close()
            Basisdata.buka(berkas).use { b ->
                b.db.analisQueries.catatEkspor("titeny_x_20261004_0905.csv", "x", "csv", 1, "abc", 1L)
                assertEquals(1, b.db.analisQueries.semuaEkspor().executeAsList().size)
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
