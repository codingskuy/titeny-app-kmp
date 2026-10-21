package id.skuy.titeny.analis.validasi

import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.Keputusan
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormValidasiTest {
    private val alasanSah = "Salah input lapak 042."

    @Test
    fun tombolNonaktifSampaiKeputusanDanAlasanValid() {
        assertFalse(FormValidasi().bisaKirim)
        assertFalse(FormValidasi(Keputusan.Setuju, "").bisaKirim)
        assertFalse(FormValidasi(null, alasanSah).bisaKirim)
        assertTrue(FormValidasi(Keputusan.Setuju, alasanSah).bisaKirim)
    }

    @Test
    fun batasAlasan10Sampai140Karakter() {
        assertFalse(FormValidasi(Keputusan.Tolak, "a".repeat(9)).bisaKirim)
        assertTrue(FormValidasi(Keputusan.Tolak, "a".repeat(10)).bisaKirim)
        assertTrue(FormValidasi(Keputusan.Tolak, "a".repeat(140)).bisaKirim)
        assertFalse(FormValidasi(Keputusan.Tolak, "a".repeat(141)).bisaKirim)
    }

    @Test
    fun spasiDiAwalAkhirTidakDihitung() {
        assertFalse(FormValidasi(Keputusan.Tolak, "   abc   ").bisaKirim)
        assertEquals(3, FormValidasi(Keputusan.Tolak, "   abc   ").panjangAlasan)
    }

    @Test
    fun koreksiNilaiWajibAngkaPositif() {
        assertFalse(FormValidasi(Keputusan.KoreksiNilai, alasanSah, "").bisaKirim)
        assertFalse(FormValidasi(Keputusan.KoreksiNilai, alasanSah, "0").bisaKirim)
        assertFalse(FormValidasi(Keputusan.KoreksiNilai, alasanSah, "-5").bisaKirim)
        assertFalse(FormValidasi(Keputusan.KoreksiNilai, alasanSah, "41.000").bisaKirim)
        assertTrue(FormValidasi(Keputusan.KoreksiNilai, alasanSah, "41000").bisaKirim)
    }

    @Test
    fun nilaiBaruDilarangUntukKeputusanLain() {
        val req = FormValidasi(Keputusan.Setuju, alasanSah, "41000").keRequest("aks_1", "evt_20261004_000123")!!
        assertNull(req.nilaiBaru)
        val koreksi = FormValidasi(Keputusan.KoreksiNilai, alasanSah, " 41000 ").keRequest("aks_1", "evt_20261004_000123")!!
        assertEquals(41000L, koreksi.nilaiBaru)
    }

    @Test
    fun requestMemakaiAlasanTerpangkasDanTidakDibuatBilaTakValid() {
        assertNull(FormValidasi(Keputusan.Setuju, "pendek").keRequest("aks_1", "evt_20261004_000123"))
        val r = FormValidasi(Keputusan.Tolak, "  $alasanSah  ").keRequest("aks_1", "evt_20261004_000123")!!
        assertEquals(alasanSah, r.alasan)
        assertEquals("aks_1", r.aksiId)
    }

    @Test
    fun aksiIdBerpolaDanMuatDalam64Karakter() {
        val id = buatAksiId(LocalDate.of(2026, 10, 4)) { "ab12cd34" }
        assertEquals("aks_20261004_ab12cd34", id)
        assertTrue(buatAksiId().length <= 64)
        assertTrue(Regex("^aks_\\d{8}_[0-9a-f]{8}$").matches(buatAksiId()))
    }

    @Test
    fun pesanGalatMenjelaskanKonflikDanKuota() {
        assertTrue("sudah diputuskan" in pesanGalat(Hasil.Galat(409, "validasi_gagal", "x")))
        assertTrue("30 detik" in pesanGalat(Hasil.Galat(429, "kuota_habis", "x", retryAfterDetik = 30)))
        assertTrue("alasan" in pesanGalat(Hasil.Galat(422, "validasi_gagal", "Alasan harus 10-140 karakter.", "alasan")))
        assertTrue("masuk ulang" in pesanGalat(Hasil.Galat(401, "sesi_berakhir", "x")))
    }
}
