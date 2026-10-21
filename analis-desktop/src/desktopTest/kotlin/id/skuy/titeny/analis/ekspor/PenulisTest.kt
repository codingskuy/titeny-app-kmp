package id.skuy.titeny.analis.ekspor

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenulisTest {
    private val kolom = listOf("komoditas_id", "catatan", "harga")
    private val baris: List<List<Sel>> = listOf(
        listOf("cmd_cabai_merah", "a,b \"kutip\"\nbaris2", 42500L),
        listOf("cmd_tomat", "=HYPERLINK(\"x\")", 8.5),
        listOf("cmd_gula", null, -3L),
    )

    @Test
    fun csvRfc4180DenganBomDanKutip() {
        val b = PenulisCsv.tulis(kolom, baris)
        assertEquals(listOf(0xEF, 0xBB, 0xBF), b.take(3).map { it.toInt() and 0xFF })
        val teks = b.decodeToString().removePrefix("﻿")
        assertEquals(
            "komoditas_id,catatan,harga\r\n" +
                "cmd_cabai_merah,\"a,b \"\"kutip\"\"\nbaris2\",42500\r\n" +
                "cmd_tomat,\"'=HYPERLINK(\"\"x\"\")\",8.5\r\n" +
                "cmd_gula,,-3\r\n",
            teks,
        )
    }

    @Test
    fun csvMenetralkanInjeksiRumusTetapMembiarkanAngkaNegatif() {
        val teks = PenulisCsv.tulis(listOf("a"), listOf(listOf("=1+1"), listOf("+x"), listOf("-x"), listOf("@x"), listOf(-5L), listOf(-0.5))).decodeToString()
        assertTrue("'=1+1" in teks && "'+x" in teks && "'-x" in teks && "'@x" in teks)
        assertTrue("\r\n-5\r\n" in teks && "\r\n-0.5\r\n" in teks, teks)
    }

    @Test
    fun angkaDesimalTanpaNotasiIlmiah() {
        assertEquals("840", teksSel(840.0))
        assertEquals("0.000001", teksSel(0.000001))
        assertEquals("1200000", teksSel(1.2E6))
        assertEquals("", teksSel(null))
    }

    @Test
    fun xlsxBerisiPartOoxmlYangSahDanSelBertipe() {
        val b = PenulisXlsx.tulis(kolom, baris, "Harga")
        val isi = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(b)).use { z ->
            generateSequence { z.nextEntry }.forEach { isi[it.name] = z.readBytes() }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"), isi.keys)
        isi.values.forEach { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(it)) } // XML bentuk baik
        val sheet = isi.getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue("""<c r="C2"><v>42500</v></c>""" in sheet, sheet)
        assertTrue("""<c r="C3"><v>8.5</v></c>""" in sheet)
        assertTrue("""<c r="C4"><v>-3</v></c>""" in sheet)
        assertTrue("a,b &quot;kutip&quot;\nbaris2" in sheet)
        assertFalse("r=\"B4\"" in sheet, "sel null dilewati")
        assertTrue("""name="Harga"""" in isi.getValue("xl/workbook.xml").decodeToString())
    }

    @Test
    fun xlsxMembuangKarakterKontrolIlegalDanDeterministik() {
        val a = PenulisXlsx.tulis(listOf("k"), listOf(listOf("a\u0001b<&>")))
        val sheet = ZipInputStream(ByteArrayInputStream(a)).use { z ->
            generateSequence { z.nextEntry }.first { it.name.endsWith("sheet1.xml") }.let { z.readBytes().decodeToString() }
        }
        assertTrue("ab&lt;&amp;&gt;" in sheet, sheet)
        assertTrue(a.contentEquals(PenulisXlsx.tulis(listOf("k"), listOf(listOf("a\u0001b<&>")))), "hash stabil untuk isi yang sama")
    }

    @Test
    fun hurufKolomXlsx() {
        assertEquals("A", PenulisXlsx.kolomHuruf(0))
        assertEquals("Z", PenulisXlsx.kolomHuruf(25))
        assertEquals("AA", PenulisXlsx.kolomHuruf(26))
        assertEquals("AZ", PenulisXlsx.kolomHuruf(51))
        assertEquals("BA", PenulisXlsx.kolomHuruf(52))
    }

    @Test
    fun namaBerkasSesuaiPola() {
        val t = LocalDateTime.of(2026, 10, 4, 9, 5)
        assertEquals("titeny_harga_h7_20261004_0905.xlsx", namaBerkas("harga_h7", t, FormatEkspor.Xlsx))
        assertEquals("titeny_demand_20261004_0905.csv", namaBerkas("demand", t, FormatEkspor.Csv))
        assertEquals("titeny_panen_20261004_0905_2.xlsx", namaBerkas("panen", t, FormatEkspor.Xlsx, bagian = 2))
        assertTrue(Regex("^titeny_[a-z0-9_]+_\\d{8}_\\d{4}(_\\d+)?\\.(csv|xlsx)$").matches(namaBerkas("harga_h7", t, FormatEkspor.Csv, 3)))
    }

    @Test
    fun sha256DikenalBaik() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".toByteArray()))
    }
}
