package id.skuy.titeny.analis.ekspor

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Maks baris DATA per berkas (produk/22 §3.5); di atasnya dipecah ke berkas berikutnya. */
const val MAKS_BARIS_PER_BERKAS = 50_000

enum class FormatEkspor(val ekstensi: String) { Csv("csv"), Xlsx("xlsx") }

/** Sel bertipe: angka disimpan sebagai angka (bukan teks), selainnya teks. */
typealias Sel = Any?

/** Format sel angka tanpa notasi ilmiah dan tanpa ekor nol, independen terhadap locale. */
fun teksSel(sel: Sel): String = when (sel) {
    null -> ""
    is Int, is Long -> sel.toString()
    is Double -> BigDecimal.valueOf(sel).stripTrailingZeros().toPlainString()
    else -> sel.toString()
}

/**
 * Nama berkas: `titeny_<jenis>_YYYYMMDD_HHmm.<ekstensi>` (produk/22 §3.5).
 * Bagian ke-2 dan seterusnya (bila > 50.000 baris) diberi sufiks `_2`, `_3` sebelum ekstensi
 * (asumsi: dokumen tidak mengatur penamaan bagian).
 */
fun namaBerkas(jenis: String, waktu: LocalDateTime, format: FormatEkspor, bagian: Int = 1): String {
    val stempel = waktu.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
    val sufiks = if (bagian > 1) "_$bagian" else ""
    return "titeny_${jenis}_$stempel$sufiks.${format.ekstensi}"
}

fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

/** Penulis tabel ke ByteArray utuh (<= 50.000 baris, jadi aman di memori) agar hash dihitung dari byte yang ditulis. */
object PenulisCsv {
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * RFC 4180 (CRLF, kutip bila ada koma/kutip/baris baru) dengan BOM UTF-8 agar Excel membaca UTF-8.
     * Teks yang diawali `=`, `+`, `-`, `@`, TAB, atau CR diberi awalan `'` untuk mencegah injeksi rumus;
     * angka bertipe Number tidak disentuh.
     */
    fun tulis(kolom: List<String>, baris: List<List<Sel>>): ByteArray {
        val sb = StringBuilder()
        sb.append(kolom.joinToString(",") { kutip(it) }).append("\r\n")
        baris.forEach { b -> sb.append(b.joinToString(",") { sel -> kutip(aman(sel)) }).append("\r\n") }
        return BOM + sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun aman(sel: Sel): String {
        val t = teksSel(sel)
        return if (sel !is Number && t.isNotEmpty() && t[0] in "=+-@\t\r") "'$t" else t
    }

    private fun kutip(t: String) =
        if (t.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
}

/** XLSX minimal (OOXML) tanpa pustaka luar: satu lembar, teks sebagai inlineStr, angka sebagai angka. */
object PenulisXlsx {
    fun tulis(kolom: List<String>, baris: List<List<Sel>>, namaLembar: String = "Data"): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entri("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.entri("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.entri("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xml(namaLembar)}" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.entri("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml").apply { time = 0L }) // waktu tetap: keluaran deterministik
            val w = zip.bufferedWriter(Charsets.UTF_8)
            w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n")
            w.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            tulisBaris(w, 1, kolom)
            baris.forEachIndexed { i, b -> tulisBaris(w, i + 2, b) }
            w.write("</sheetData></worksheet>")
            w.flush()
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun tulisBaris(w: java.io.Writer, nomor: Int, sel: List<Sel>) {
        w.write("""<row r="$nomor">""")
        sel.forEachIndexed { j, s ->
            val ref = "${kolomHuruf(j)}$nomor"
            when (s) {
                null -> Unit
                is Int, is Long, is Double -> w.write("""<c r="$ref"><v>${teksSel(s)}</v></c>""")
                else -> w.write("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${xml(s.toString())}</t></is></c>""")
            }
        }
        w.write("</row>")
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    fun kolomHuruf(indeks: Int): String {
        var n = indeks
        val sb = StringBuilder()
        do {
            sb.append('A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.reverse().toString()
    }

    /** Escape XML dan buang karakter kontrol yang tidak sah di XML 1.0. */
    private fun xml(t: String): String {
        val sb = StringBuilder(t.length)
        t.forEach { c ->
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun ZipOutputStream.entri(nama: String, isi: String) {
        putNextEntry(ZipEntry(nama).apply { time = 0L })
        write(isi.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
