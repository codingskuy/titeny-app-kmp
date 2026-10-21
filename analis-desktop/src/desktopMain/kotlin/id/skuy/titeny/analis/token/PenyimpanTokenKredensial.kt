package id.skuy.titeny.analis.token

import id.skuy.titeny.analis.api.FormatJson
import id.skuy.titeny.analis.api.PenyimpanToken
import id.skuy.titeny.analis.api.PenyimpanTokenMemori
import id.skuy.titeny.analis.api.SesiToken

/** Brankas rahasia milik OS (Windows Credential Manager). Dipisah agar logika dapat diuji tanpa Windows. */
interface BrankasKredensial {
    fun tulis(target: String, data: ByteArray)
    fun baca(target: String): ByteArray?
    fun hapus(target: String)
}

/**
 * Token analis di brankas OS, BUKAN berkas teks (AGENTS.md; produk/22 §5).
 * Windows membatasi blob kredensial 2.560 byte, sedangkan dua JWT dapat melebihinya, jadi isi
 * dipecah menjadi potongan <= [UKURAN_POTONGAN] byte pada target `Titeny.Analis.Token.<i>`;
 * target `Titeny.Analis.Token` menyimpan jumlah potongan.
 */
class PenyimpanTokenKredensial(
    private val brankas: BrankasKredensial,
    private val ukuranPotongan: Int = UKURAN_POTONGAN,
) : PenyimpanToken {

    override suspend fun baca(): SesiToken? {
        val jumlah = brankas.baca(TARGET_INDUK)?.toString(Charsets.UTF_8)?.toIntOrNull() ?: return null
        if (jumlah <= 0 || jumlah > MAKS_POTONGAN) return rusak()
        val utuh = ByteArray(0).let { awal ->
            (0 until jumlah).fold(awal) { acc, i -> acc + (brankas.baca("$TARGET_INDUK.$i") ?: return rusak()) }
        }
        return runCatching { FormatJson.json.decodeFromString(SesiToken.serializer(), utuh.toString(Charsets.UTF_8)) }
            .getOrElse { return rusak() }
    }

    override suspend fun simpan(token: SesiToken) {
        val data = FormatJson.json.encodeToString(SesiToken.serializer(), token).toByteArray(Charsets.UTF_8)
        val potongan = data.toList().chunked(ukuranPotongan).map { it.toByteArray() }
        require(potongan.size <= MAKS_POTONGAN) { "Token terlalu besar untuk brankas" }
        potongan.forEachIndexed { i, p -> brankas.tulis("$TARGET_INDUK.$i", p) }
        brankas.tulis(TARGET_INDUK, potongan.size.toString().toByteArray(Charsets.UTF_8))
        // Potongan lama yang tidak terpakai lagi (token baru lebih pendek) dihapus.
        (potongan.size until MAKS_POTONGAN).forEach { brankas.hapus("$TARGET_INDUK.$it") }
    }

    override suspend fun hapus() {
        brankas.hapus(TARGET_INDUK)
        (0 until MAKS_POTONGAN).forEach { brankas.hapus("$TARGET_INDUK.$it") }
    }

    private suspend fun rusak(): SesiToken? {
        hapus() // isi rusak/tidak lengkap: perlakukan sebagai belum masuk
        return null
    }

    companion object {
        const val TARGET_INDUK = "Titeny.Analis.Token"
        const val UKURAN_POTONGAN = 1500
        const val MAKS_POTONGAN = 8
    }
}

/**
 * Penyimpan token untuk OS saat ini. Hanya Windows yang mempunyai brankas (target rilis v1.0.0);
 * OS lain (pengembangan) memakai memori proses: token hilang saat aplikasi ditutup, TIDAK ditulis ke berkas.
 */
fun buatPenyimpanToken(namaOs: String = System.getProperty("os.name").orEmpty()): PenyimpanToken =
    if (namaOs.startsWith("Windows", ignoreCase = true)) PenyimpanTokenKredensial(BrankasWindows()) else PenyimpanTokenMemori()
