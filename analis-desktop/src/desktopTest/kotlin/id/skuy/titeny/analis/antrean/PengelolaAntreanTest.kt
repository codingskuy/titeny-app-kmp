package id.skuy.titeny.analis.antrean

import id.skuy.titeny.analis.api.AntreanItem
import id.skuy.titeny.analis.api.AntreanValidasi
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.SumberAntrean
import id.skuy.titeny.analis.cache.Basisdata
import id.skuy.titeny.analis.cache.CacheAnalis
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PengelolaAntreanTest {
    private fun item(n: Int) =
        AntreanItem("evt_20261004_%06d".format(n), "harga.diperbarui", "perlu_verifikasi", "harga di luar rentang")

    private var hit = 0
    private var gagalJaringan = false
    private val halamanKe = mutableListOf<String?>()

    private fun pengelola(): PengelolaAntrean = PengelolaAntrean(sumberPalsu(), jam = { 42L }, halaman = 2)

    private fun sumberPalsu(): SumberAntrean {
        return object : SumberAntrean {
            override suspend fun antrean(limit: Int, cursor: String?): Hasil<AntreanValidasi> {
                hit++
                if (gagalJaringan) return Hasil.Jaringan(IOException("putus"))
                halamanKe += cursor
                return Hasil.Berhasil(
                    when (cursor) {
                        null -> AntreanValidasi(listOf(item(1), item(2)), "c2")
                        else -> AntreanValidasi(listOf(item(3)), null)
                    },
                )
            }
        }
    }

    @Test
    fun muatMengikutiCursorSampaiHabis() = runTest {
        val m = pengelola()
        m.muat()
        val s = assertIs<StateAntrean.Siap>(m.state.value)
        assertEquals(listOf(1, 2, 3).map { "evt_20261004_%06d".format(it) }, s.item.map { it.eventId })
        assertEquals(42L, s.diperbaruiMs)
        assertEquals(listOf(null, "c2"), halamanKe)
    }

    @Test
    fun jaringanPutusSetelahSiapMenjadiLuringDenganStempelTerakhir() = runTest {
        val m = pengelola()
        m.muat()
        gagalJaringan = true
        m.muat()
        val s = assertIs<StateAntrean.Luring>(m.state.value)
        assertEquals(3, s.item.size)
        assertEquals(42L, s.stempelMs)
    }

    @Test
    fun aplikasiBaruDibukaLuringMemakaiCacheSqlite() = runTest {
        Basisdata.buka(null).use { b ->
            val cache = CacheAnalis(b.db)
            cache.simpanAntrean(listOf(item(7)), 1_000L)
            gagalJaringan = true
            val m = PengelolaAntrean(sumberPalsu(), jam = { 42L }, cache = cache)
            m.muat()
            assertEquals(StateAntrean.Luring(listOf(item(7)), 1_000L), m.state.value)
            assertEquals(item(7), m.cari(item(7).eventId))
        }
    }

    @Test
    fun luringTanpaCacheMenjadiGagal() = runTest {
        gagalJaringan = true
        val m = pengelola()
        m.muat()
        assertIs<StateAntrean.Gagal>(m.state.value)
    }

    @Test
    fun muatBerhasilMenyimpanCacheDanMemanggilSebelumMuat() = runTest {
        Basisdata.buka(null).use { b ->
            val cache = CacheAnalis(b.db)
            var dipanggil = 0
            val m = PengelolaAntrean(sumberPalsu(), jam = { 42L }, halaman = 2, cache = cache, sebelumMuat = { dipanggil++ })
            m.muat()
            assertEquals(1, dipanggil)
            assertEquals(3, cache.bacaAntrean()?.item?.size)
            assertEquals(42L, cache.bacaAntrean()?.stempelMs)
        }
    }

    @Test
    fun serverMati5xxDiperlakukanLuring() = runTest {
        val m = PengelolaAntrean(object : SumberAntrean {
            override suspend fun antrean(limit: Int, cursor: String?): Hasil<AntreanValidasi> = Hasil.Galat(503, null, "tidak tersedia")
        }, jam = { 1L })
        m.muat()
        assertIs<StateAntrean.Gagal>(m.state.value) // belum ada data sama sekali
        val ok = pengelola()
        ok.muat()
    }

    @Test
    fun hapusLokalMenyaringItemDanCariMenemukan() = runTest {
        val m = pengelola()
        m.muat()
        assertEquals("harga.diperbarui", m.cari("evt_20261004_000002")?.eventType)
        m.hapusLokal("evt_20261004_000002")
        assertEquals(null, m.cari("evt_20261004_000002"))
        assertEquals(2, (m.state.value as StateAntrean.Siap).item.size)
    }

    @Test
    fun pollingLimaMenitDanSegarkanManual() = runTest {
        val m = pengelola()
        val segarkan = MutableSharedFlow<Unit>()
        val job = launch { m.polling(segarkan, intervalMs = INTERVAL_ANTREAN_MS) }
        runCurrent()
        val awal = hit // muat pertama: 2 halaman
        assertEquals(2, awal)
        advanceTimeBy(INTERVAL_ANTREAN_MS - 1)
        runCurrent()
        assertEquals(awal, hit)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(awal * 2, hit) // polling 5 menit
        segarkan.emit(Unit) // tombol Segarkan / Ctrl+R: segera, tanpa menunggu interval
        runCurrent()
        assertEquals(awal * 3, hit)
        job.cancel()
    }

    @Test
    fun intervalAntreanLimaMenit() {
        assertEquals(300_000L, INTERVAL_ANTREAN_MS)
    }
}
