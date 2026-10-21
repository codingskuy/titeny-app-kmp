package id.skuy.titeny.analis.token

import id.skuy.titeny.analis.api.PenyimpanTokenMemori
import id.skuy.titeny.analis.api.SesiToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PenyimpanTokenKredensialTest {
    private class BrankasPalsu : BrankasKredensial {
        val isi = mutableMapOf<String, ByteArray>()
        override fun tulis(target: String, data: ByteArray) {
            require(data.size <= 2560) { "melebihi batas blob Windows: ${data.size}" }
            isi[target] = data
        }
        override fun baca(target: String) = isi[target]
        override fun hapus(target: String) { isi.remove(target) }
    }

    private fun besar() = SesiToken("a".repeat(2000), "r".repeat(1800), 123456789L)

    @Test
    fun bolakBalikTokenKecil() = runBlocking<Unit> {
        val p = PenyimpanTokenKredensial(BrankasPalsu())
        assertNull(p.baca())
        val t = SesiToken("A1", "R1", 99L)
        p.simpan(t)
        assertEquals(t, p.baca())
    }

    @Test
    fun tokenBesarDipecahDiBawahBatasBlobDanUtuhKembali() = runBlocking<Unit> {
        val b = BrankasPalsu()
        val p = PenyimpanTokenKredensial(b)
        p.simpan(besar())
        assertEquals(besar(), p.baca())
        assertTrue(b.isi.size > 2, "harus lebih dari satu potongan: ${b.isi.keys}")
        assertTrue(b.isi.values.all { it.size <= 2560 })
    }

    @Test
    fun simpanTokenLebihPendekMembuangPotonganLama() = runBlocking<Unit> {
        val b = BrankasPalsu()
        val p = PenyimpanTokenKredensial(b)
        p.simpan(besar())
        p.simpan(SesiToken("A", "R", 1L))
        assertEquals(setOf("Titeny.Analis.Token", "Titeny.Analis.Token.0"), b.isi.keys)
    }

    @Test
    fun hapusMembersihkanSemuaTarget() = runBlocking<Unit> {
        val b = BrankasPalsu()
        val p = PenyimpanTokenKredensial(b)
        p.simpan(besar())
        p.hapus()
        assertTrue(b.isi.isEmpty())
        assertNull(p.baca())
    }

    @Test
    fun isiRusakDiperlakukanSebagaiBelumMasukDanDibersihkan() = runBlocking<Unit> {
        val b = BrankasPalsu()
        val p = PenyimpanTokenKredensial(b)
        p.simpan(besar())
        b.isi.remove("Titeny.Analis.Token.1") // potongan hilang
        assertNull(p.baca())
        assertTrue(b.isi.isEmpty())
        b.isi["Titeny.Analis.Token"] = "1".toByteArray()
        b.isi["Titeny.Analis.Token.0"] = "bukan json".toByteArray()
        assertNull(p.baca())
    }

    @Test
    fun hanyaWindowsMemakaiBrankasOsLainHanyaMemori() {
        assertIs<PenyimpanTokenMemori>(buatPenyimpanToken("Mac OS X"))
        assertIs<PenyimpanTokenMemori>(buatPenyimpanToken("Linux"))
        assertIs<PenyimpanTokenKredensial>(buatPenyimpanToken("Windows 11"))
    }
}
