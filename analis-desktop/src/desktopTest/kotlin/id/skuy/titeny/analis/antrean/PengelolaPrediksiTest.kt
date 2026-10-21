package id.skuy.titeny.analis.antrean

import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.PrediksiHarga
import id.skuy.titeny.analis.api.PrediksiHargaRespons
import id.skuy.titeny.analis.api.SumberPrediksi
import id.skuy.titeny.analis.cache.Basisdata
import id.skuy.titeny.analis.cache.CacheAnalis
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PengelolaPrediksiTest {
    private val respons = PrediksiHargaRespons(
        "cmd_cabai_merah", "psr_jakarta_timur_01",
        listOf(PrediksiHarga("2026-10-05", "H+1", 42500, 39000, 46000, "sedang")),
        "harga_v1.0.0", "2026-10-04T04:00:12+07:00",
    )
    private var hasil: Hasil<PrediksiHargaRespons> = Hasil.Berhasil(respons)
    private var panggilan = 0
    private val sumber = object : SumberPrediksi {
        override suspend fun harga(komoditasId: String, pasarId: String, dari: String?, sampai: String?): Hasil<PrediksiHargaRespons> {
            panggilan++
            return hasil
        }
    }

    @Test
    fun daringMenyimpanCacheLuringMembacaCache() = runTest {
        Basisdata.buka(null).use { b ->
            val m = PengelolaPrediksi(sumber, CacheAnalis(b.db), jam = { 777L })
            val a = m.muat("cmd_cabai_merah", "psr_jakarta_timur_01")!!
            assertFalse(a.dariCache)
            assertEquals(777L, a.diambilMs)

            hasil = Hasil.Jaringan(IOException("putus"))
            val l = m.muat("cmd_cabai_merah", "psr_jakarta_timur_01")!!
            assertTrue(l.dariCache)
            assertEquals(respons, l.data)
            assertEquals(777L, l.diambilMs)
        }
    }

    @Test
    fun galat404BukanLuringDanTidakMemakaiCache() = runTest {
        Basisdata.buka(null).use { b ->
            val m = PengelolaPrediksi(sumber, CacheAnalis(b.db))
            m.muat("cmd_cabai_merah", "psr_jakarta_timur_01")
            hasil = Hasil.Galat(404, "data_belum_cukup", "Data harga belum cukup.")
            assertNull(m.muat("cmd_cabai_merah", "psr_jakarta_timur_01"))
        }
    }

    @Test
    fun luringTanpaCacheMengembalikanNull() = runTest {
        Basisdata.buka(null).use { b ->
            hasil = Hasil.Jaringan(IOException("putus"))
            assertNull(PengelolaPrediksi(sumber, CacheAnalis(b.db)).muat("cmd_x", "psr_y"))
        }
    }

    @Test
    fun pollingLimaBelasMenit() = runTest {
        Basisdata.buka(null).use { b ->
            val m = PengelolaPrediksi(sumber, CacheAnalis(b.db))
            val job = launch { m.polling({ listOf("cmd_cabai_merah" to "psr_jakarta_timur_01") }, MutableSharedFlow(), INTERVAL_PREDIKSI_MS) }
            runCurrent()
            assertEquals(1, panggilan)
            advanceTimeBy(INTERVAL_PREDIKSI_MS - 1); runCurrent()
            assertEquals(1, panggilan)
            advanceTimeBy(2); runCurrent()
            assertEquals(2, panggilan)
            job.cancel()
        }
        assertEquals(900_000L, INTERVAL_PREDIKSI_MS)
    }
}
