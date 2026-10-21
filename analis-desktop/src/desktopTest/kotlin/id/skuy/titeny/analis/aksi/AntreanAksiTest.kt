package id.skuy.titeny.analis.aksi

import id.skuy.titeny.analis.api.EventDiterima
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.Keputusan
import id.skuy.titeny.analis.api.PengirimAksi
import id.skuy.titeny.analis.api.TahanPrediksi
import id.skuy.titeny.analis.api.ValidasiAnalis
import id.skuy.titeny.analis.cache.Basisdata
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class AntreanAksiTest {
    private class PengirimPalsu : PengirimAksi {
        val dikirim = mutableListOf<String>()
        var balasan: (String) -> Hasil<EventDiterima> = { Hasil.Berhasil(EventDiterima("diterima")) }
        override suspend fun validasi(aksi: ValidasiAnalis): Hasil<EventDiterima> { dikirim += aksi.aksiId; return balasan(aksi.aksiId) }
        override suspend fun tahan(aksi: TahanPrediksi): Hasil<EventDiterima> { dikirim += aksi.aksiId; return balasan(aksi.aksiId) }
    }

    private fun validasi(id: String) = ValidasiAnalis(id, "evt_20261004_000123", Keputusan.KoreksiNilai, 41000, "Salah input lapak 042.")
    private fun tahan(id: String) = TahanPrediksi(id, "cmd_cabai_merah", "psr_jakarta_timur_01", "Sampel hanya 2 lapak.", "2026-10-07")
    private fun galat(status: Int) = Hasil.Galat(status, "validasi_gagal", "x")

    private inline fun uji(blok: (AntreanAksi, PengirimPalsu, Basisdata) -> Unit) {
        Basisdata.buka(null).use { b ->
            val p = PengirimPalsu()
            var t = 0L
            blok(AntreanAksi(b.db, p) { ++t }, p, b)
        }
    }

    @Test
    fun daringLangsungTerkirimTanpaAntre() = runBlocking<Unit> {
        uji { a, p, _ ->
            assertEquals(HasilKirim.Terkirim(duplikat = false), runBlocking { a.kirimAtauAntre(validasi("aks_1")) })
            assertEquals(0, a.menunggu.value)
            assertEquals(listOf("aks_1"), p.dikirim)
        }
    }

    @Test
    fun luringMengantreLaluTerkirimSaatDaringDenganAksiIdSama() = runBlocking<Unit> {
        uji { a, p, _ ->
            p.balasan = { Hasil.Jaringan(IOException("putus")) }
            assertEquals(HasilKirim.Diantre, runBlocking { a.kirimAtauAntre(validasi("aks_1")) })
            assertEquals(HasilKirim.Diantre, runBlocking { a.kirimAtauAntre(tahan("aks_2")) })
            assertEquals(2, a.menunggu.value)

            // masih luring: kirim tertunda berhenti, tetap menunggu
            assertEquals(LaporanKirim(0, 0, 2), runBlocking { a.kirimTertunda() })

            p.dikirim.clear()
            p.balasan = { Hasil.Berhasil(EventDiterima("diterima")) }
            assertEquals(LaporanKirim(2, 0, 0), runBlocking { a.kirimTertunda() })
            assertEquals(listOf("aks_1", "aks_2"), p.dikirim) // urutan dibuat; aksi_id sama dengan saat diantre
            assertEquals(0, a.menunggu.value)
        }
    }

    @Test
    fun aksiIdSamaTidakMenggandakanAntrean() = runBlocking<Unit> {
        uji { a, p, _ ->
            p.balasan = { Hasil.Jaringan(IOException("putus")) }
            repeat(3) { runBlocking { a.kirimAtauAntre(validasi("aks_1")) } }
            assertEquals(1, a.menunggu.value)
        }
    }

    @Test
    fun payloadYangDikirimUlangSamaDenganYangDiantre() = runBlocking<Unit> {
        uji { a, p, b ->
            p.balasan = { Hasil.Jaringan(IOException("putus")) }
            runBlocking { a.kirimAtauAntre(validasi("aks_1")) }
            val payload = b.db.analisQueries.aksiMenunggu().executeAsOne().payload
            assertEquals(true, """"nilai_baru":41000""" in payload && """"aksi_id":"aks_1"""" in payload, payload)
        }
    }

    @Test
    fun galatPermanenMenandaiGagalDanMelanjutkan() = runBlocking<Unit> {
        uji { a, p, _ ->
            p.balasan = { Hasil.Jaringan(IOException("putus")) }
            runBlocking { a.kirimAtauAntre(validasi("aks_1")) }
            runBlocking { a.kirimAtauAntre(validasi("aks_2")) }
            p.balasan = { id -> if (id == "aks_1") galat(409) else Hasil.Berhasil(EventDiterima("duplikat_diabaikan")) }
            assertEquals(LaporanKirim(1, 1, 0), runBlocking { a.kirimTertunda() })
            assertEquals(listOf("aks_1"), a.gagal().map { it.first })
            assertEquals(0, a.menunggu.value)
        }
    }

    @Test
    fun galatSementaraMenghentikanTanpaMenghapus() = runBlocking<Unit> {
        uji { a, p, _ ->
            p.balasan = { Hasil.Jaringan(IOException("putus")) }
            runBlocking { a.kirimAtauAntre(validasi("aks_1")) }
            runBlocking { a.kirimAtauAntre(validasi("aks_2")) }
            p.dikirim.clear()
            p.balasan = { galat(503) }
            assertEquals(LaporanKirim(0, 0, 2), runBlocking { a.kirimTertunda() })
            assertEquals(listOf("aks_1"), p.dikirim) // berhenti di yang pertama
            p.balasan = { galat(429) }
            assertEquals(LaporanKirim(0, 0, 2), runBlocking { a.kirimTertunda() })
            p.balasan = { galat(401) }
            assertEquals(LaporanKirim(0, 0, 2), runBlocking { a.kirimTertunda() })
        }
    }

    @Test
    fun ditolakServerTidakDiantre() = runBlocking<Unit> {
        uji { a, p, _ ->
            p.balasan = { galat(422) }
            assertIs<HasilKirim.Ditolak>(runBlocking { a.kirimAtauAntre(validasi("aks_1")) })
            assertEquals(0, a.menunggu.value)
            p.balasan = { galat(500) }
            assertEquals(HasilKirim.Diantre, runBlocking { a.kirimAtauAntre(validasi("aks_2")) })
        }
    }
}
