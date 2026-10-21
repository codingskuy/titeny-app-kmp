package id.skuy.titeny.analis.cache

import id.skuy.titeny.analis.api.AntreanItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheDanPemeliharaanTest {
    private val wib = ZoneId.of("Asia/Jakarta")
    private fun item(n: Int) = AntreanItem("evt_20261004_%06d".format(n), "harga.diperbarui", "perlu_verifikasi", "di luar rentang")
    private fun ms(tgl: String, jam: Int = 12) = ZonedDateTime.parse("${tgl}T%02d:00:00+07:00[Asia/Jakarta]".format(jam)).toInstant().toEpochMilli()
    private fun hari(n: Long) = TimeUnit.DAYS.toMillis(n)

    @Test
    fun cacheAntreanBolakBalikDenganStempel() {
        Basisdata.buka(null).use { b ->
            val c = CacheAnalis(b.db)
            assertNull(c.bacaAntrean())
            c.simpanAntrean(listOf(item(1), item(2)), 1000L)
            assertEquals(CacheAntrean(listOf(item(1), item(2)), 1000L), c.bacaAntrean())
            // sinkron berikutnya MENGGANTI salinan (server sumber kebenaran)
            c.simpanAntrean(listOf(item(3)), 2000L)
            assertEquals(CacheAntrean(listOf(item(3)), 2000L), c.bacaAntrean())
        }
    }

    @Test
    fun antreanKosongTetapTercatatSebagaiSinkron() {
        Basisdata.buka(null).use { b ->
            val c = CacheAnalis(b.db)
            c.simpanAntrean(emptyList(), 5000L)
            assertEquals(CacheAntrean(emptyList(), 5000L), c.bacaAntrean())
        }
    }

    @Test
    fun cacheJsonDanBannerLuring() {
        Basisdata.buka(null).use { b ->
            val c = CacheAnalis(b.db)
            assertNull(c.bacaJson("k"))
            c.simpanJson("k", """{"a":1}""", 7L)
            assertEquals("""{"a":1}""" to 7L, c.bacaJson("k"))
        }
        assertEquals("Luring — data 2026-10-03 23.00", bannerLuring(ms("2026-10-03", 23)))
    }

    @Test
    fun basisDataDiBerkasDibukaUlangMempertahankanIsi() {
        val dir = Files.createTempDirectory("titeny-db")
        try {
            val berkas = dir.resolve("Titeny").resolve("analis.db")
            Basisdata.buka(berkas).use { CacheAnalis(it.db).simpanAntrean(listOf(item(1)), 10L) }
            assertTrue(Files.exists(berkas))
            Basisdata.buka(berkas).use { assertEquals(listOf(item(1)), CacheAnalis(it.db).bacaAntrean()?.item) }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun lokasiDbMemakaiLocalAppData() {
        val p = LokasiDb.tentukan(env = { if (it == "LOCALAPPDATA") "C:\\Users\\rina\\AppData\\Local" else null })
        assertEquals(Path.of("C:\\Users\\rina\\AppData\\Local").resolve("Titeny").resolve("analis.db"), p)
        val dev = LokasiDb.tentukan(env = { null }, home = "/home/x")
        assertTrue(dev.endsWith(Path.of("Titeny", "analis.db")))
    }

    @Test
    fun pangkasUsiaMembuangDataLebihDari90HariTetapMenyisakanAksiMenunggu() {
        Basisdata.buka(null).use { b ->
            val sekarang = ms("2026-10-20")
            val q = b.db.analisQueries
            q.simpanAntreanCache("evt_20260101_000001", "x", "perlu_verifikasi", null, sekarang - hari(91))
            q.simpanAntreanCache("evt_20261001_000002", "x", "perlu_verifikasi", null, sekarang - hari(89))
            q.simpanJson("lama", "{}", sekarang - hari(100))
            q.simpanJson("baru", "{}", sekarang - hari(1))
            q.antreAksi("aks_lama_menunggu", "validasi", "{}", sekarang - hari(120), sekarang - hari(120))
            q.antreAksi("aks_lama_selesai", "validasi", "{}", sekarang - hari(120), sekarang - hari(120))
            q.tandaiTerkirim(sekarang - hari(120), "aks_lama_selesai")

            assertTrue(Pemeliharaan(b, jam = { sekarang }).pangkasUsia())

            assertEquals(listOf("evt_20261001_000002"), q.bacaAntreanCache().executeAsList().map { it.event_id })
            assertNull(CacheAnalis(b.db).bacaJson("lama"))
            assertEquals("{}" to (sekarang - hari(1)), CacheAnalis(b.db).bacaJson("baru"))
            assertEquals(listOf("aks_lama_menunggu"), q.semuaAksi().executeAsList().map { it.aksi_id }) // menunggu tak pernah dipangkas
        }
    }

    @Test
    fun pangkasUkuranMembuangTerlamaDuluDanTidakMenyentuhAksiMenunggu() {
        Basisdata.buka(null).use { b ->
            val q = b.db.analisQueries
            (1..10).forEach { q.simpanJson("k$it", "{}", it.toLong()) } // k1 paling lama
            q.antreAksi("aks_menunggu", "validasi", "{}", 0L, 0L)
            // ukuran simulasi: 1 MB per baris cache; batas 4 MB
            val p = Pemeliharaan(
                b, batasBytes = 4L * 1024 * 1024,
                ukuranBytes = { q.hitungBarisPemangkasan().executeAsOne() * 1024 * 1024 },
            )
            val dihapus = p.pangkasUkuran()
            assertTrue(dihapus >= 6, "dihapus=$dihapus")
            val sisa = (1..10).filter { CacheAnalis(b.db).bacaJson("k$it") != null }
            assertTrue(sisa.size <= 4)
            assertEquals((11 - sisa.size..10).toList(), sisa) // yang tersisa = yang terbaru
            assertEquals(1L, q.hitungMenunggu().executeAsOne())
        }
    }

    @Test
    fun pangkasUkuranBerhentiBilaSisanyaHanyaAksiMenunggu() {
        Basisdata.buka(null).use { b ->
            val q = b.db.analisQueries
            q.antreAksi("aks_1", "validasi", "{}", 0L, 0L)
            val p = Pemeliharaan(b, batasBytes = 1L, ukuranBytes = { 10L })
            assertEquals(0, p.pangkasUkuran())
            assertEquals(1L, q.hitungMenunggu().executeAsOne())
        }
    }

    @Test
    fun vacuumTiapMingguWib() {
        val minggu = ms("2026-10-18") // Minggu
        val senin = ms("2026-10-19")
        val sabtu = ms("2026-10-17")
        assertTrue(Pemeliharaan.perluVacuum(minggu, null))
        assertTrue(Pemeliharaan.perluVacuum(minggu, sabtu), "Minggu, terakhir kemarin")
        assertFalse(Pemeliharaan.perluVacuum(minggu, ms("2026-10-18", 1)), "sudah divacuum hari ini")
        assertFalse(Pemeliharaan.perluVacuum(senin, minggu), "Senin, baru divacuum kemarin")
        assertTrue(Pemeliharaan.perluVacuum(ms("2026-10-27"), minggu), "lebih dari 7 hari: menyusul walau bukan Minggu")
    }

    @Test
    fun jalankanMenjalankanVacuumLaluMencatatWaktu() {
        Basisdata.buka(null).use { b ->
            val sekarang = ms("2026-10-18")
            val p = Pemeliharaan(b, jam = { sekarang })
            assertTrue(p.jalankan().vacuum)
            assertFalse(p.jalankan().vacuum)
            assertEquals(sekarang.toString(), b.db.analisQueries.bacaMeta(Pemeliharaan.META_VACUUM).executeAsOne())
        }
    }
}
