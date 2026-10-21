package id.skuy.titeny.analis.nav

import androidx.compose.ui.input.key.Key
import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigatorTest {
    private val evt = "evt_20261004_000123"

    private fun masuk(handle: SavedStateHandle = SavedStateHandle()) = Navigator(handle).also { it.masukBerhasil() }

    @Test
    fun awalnyaHanyaLayarMasuk() {
        val n = Navigator()
        assertEquals(listOf<Rute>(Rute.Masuk), n.tumpukan)
        assertFalse(n.sudahMasuk)
    }

    @Test
    fun layarLainDitolakSebelumMasuk() {
        val n = Navigator()
        assertFalse(n.buka(Rute.Ekspor))
        assertEquals(listOf<Rute>(Rute.Masuk), n.tumpukan)
    }

    @Test
    fun masukBerhasilMenggantiTumpukanDenganAntrean() {
        val n = masuk()
        assertEquals(listOf<Rute>(Rute.AntreanValidasi), n.tumpukan)
        assertFalse(n.buka(Rute.Masuk))
    }

    @Test
    fun tujuhLayarTerkunciDapatDibuka() {
        val n = masuk()
        val semua = listOf(
            Rute.TahanPrediksi, Rute.BandingModel, Rute.KelolaRekomendasi, Rute.Ekspor, Rute.DetailFlag(evt),
        )
        semua.forEach { assertTrue(n.buka(it)) }
        // Masuk (sebelum login) + AntreanValidasi + 5 di atas = 7 layar terkunci.
        assertEquals(listOf<Rute>(Rute.AntreanValidasi) + semua, n.tumpukan.toList())
        assertEquals(Rute.JUMLAH_LAYAR, 1 + n.tumpukan.map { it::class }.toSet().size)
    }

    @Test
    fun deepLinkMembukaDetailFlag() {
        val n = masuk()
        val hasil = n.bukaDeepLink("titeny://analis/flag/$evt")
        assertEquals(HasilDeepLink.Dibuka(Rute.DetailFlag(evt)), hasil)
        assertEquals(Rute.DetailFlag(evt), n.rutePuncak)
    }

    @Test
    fun deepLinkSebelumMasukDitundaLaluDibukaSetelahLogin() {
        val n = Navigator()
        assertIs<HasilDeepLink.Tertunda>(n.bukaDeepLink("titeny://analis/flag/$evt"))
        assertEquals(listOf<Rute>(Rute.Masuk), n.tumpukan)
        n.masukBerhasil()
        assertEquals(listOf(Rute.AntreanValidasi, Rute.DetailFlag(evt)), n.tumpukan)
    }

    @Test
    fun deepLinkTidakSahDitolak() {
        val n = masuk()
        listOf(
            "titeny://analis/flag/evt_2026_1",
            "titeny://analis/flag/",
            "titeny://analis/antrean/$evt",
            "titeny://petani/flag/$evt",
            "http://analis/flag/$evt",
            "bukan uri ::",
        ).forEach { assertIs<HasilDeepLink.Ditolak>(n.bukaDeepLink(it), it) }
        assertEquals(listOf<Rute>(Rute.AntreanValidasi), n.tumpukan)
    }

    @Test
    fun eventIdHarusSesuaiPola() {
        assertFailsWith<IllegalArgumentException> { Rute.DetailFlag("evt_1") }
        assertNull(Navigator.urai("titeny://analis/flag/evt_20261004_00012"))
    }

    @Test
    fun tumpukanMaksimal20DanAkarTetap() {
        val n = masuk()
        repeat(50) { i -> n.buka(Rute.DetailFlag("evt_20261004_%06d".format(i))) }
        assertEquals(Navigator.BATAS_TUMPUKAN, n.tumpukan.size)
        assertEquals(Rute.AntreanValidasi, n.tumpukan.first())
        assertEquals(Rute.DetailFlag("evt_20261004_000049"), n.tumpukan.last())
    }

    @Test
    fun kembaliTidakMengosongkanTumpukan() {
        val n = masuk()
        assertFalse(n.kembali())
        n.buka(Rute.Ekspor)
        assertTrue(n.kembali())
        assertEquals(listOf<Rute>(Rute.AntreanValidasi), n.tumpukan)
    }

    @Test
    fun stateDipulihkanDariSavedStateHandle() {
        val handle = SavedStateHandle()
        val a = masuk(handle)
        a.buka(Rute.DetailFlag(evt))
        a.buka(Rute.Ekspor)
        val b = Navigator(handle)
        assertTrue(b.sudahMasuk)
        assertEquals(a.tumpukan.toList(), b.tumpukan.toList())
    }

    @Test
    fun keluarMengembalikanKeMasukDanMenghapusSesi() {
        val handle = SavedStateHandle()
        val n = masuk(handle)
        n.buka(Rute.Ekspor)
        n.keluar()
        assertEquals(listOf<Rute>(Rute.Masuk), Navigator(handle).tumpukan)
        assertFalse(Navigator(handle).sudahMasuk)
    }

    @Test
    fun pintasanDipetakanSesuaiKontrak() {
        assertEquals(AksiPintasan.Segarkan, petaPintasan(Key.R, ctrl = true))
        assertEquals(AksiPintasan.Ekspor, petaPintasan(Key.E, ctrl = true))
        assertEquals(AksiPintasan.Kembali, petaPintasan(Key.Escape, ctrl = false))
        assertNull(petaPintasan(Key.R, ctrl = false))
        assertNull(petaPintasan(Key.Escape, ctrl = true))
    }

    @Test
    fun ctrlEMembukaEksporHanyaSetelahMasuk() {
        val n = Navigator()
        n.tanganiPintasan(AksiPintasan.Ekspor)
        assertEquals(Rute.Masuk, n.rutePuncak)
        n.masukBerhasil()
        n.tanganiPintasan(AksiPintasan.Ekspor)
        assertEquals(Rute.Ekspor, n.rutePuncak)
        n.tanganiPintasan(AksiPintasan.Kembali)
        assertEquals(Rute.AntreanValidasi, n.rutePuncak)
    }

    @Test
    fun kodeRuteBolakBalik() {
        listOf(Rute.Masuk, Rute.AntreanValidasi, Rute.DetailFlag(evt), Rute.TahanPrediksi, Rute.BandingModel, Rute.KelolaRekomendasi, Rute.Ekspor)
            .forEach { assertEquals(it, Rute.dariKode(it.kode)) }
        assertNull(Rute.dariKode("tak-dikenal"))
    }
}
