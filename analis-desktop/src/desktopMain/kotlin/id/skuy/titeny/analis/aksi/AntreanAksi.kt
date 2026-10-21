package id.skuy.titeny.analis.aksi

import id.skuy.titeny.analis.api.EventDiterima
import id.skuy.titeny.analis.api.FormatJson
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.PengirimAksi
import id.skuy.titeny.analis.api.TahanPrediksi
import id.skuy.titeny.analis.api.ValidasiAnalis
import id.skuy.titeny.analis.db.AnalisDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hasil mengirim satu aksi: langsung terkirim, diantre (luring), atau ditolak server. */
sealed interface HasilKirim {
    data class Terkirim(val duplikat: Boolean) : HasilKirim
    data object Diantre : HasilKirim
    data class Ditolak(val galat: Hasil.Galat) : HasilKirim
}

data class LaporanKirim(val terkirim: Int, val gagal: Int, val tertunda: Int)

/**
 * Antrean tulis luring. Setiap aksi disimpan (SQLite) dengan `aksi_id` yang sama dengan
 * `X-Idempotency-Key`, sehingga pengiriman ulang aman: server menjawab 200 `duplikat_diabaikan`.
 * Klien TIDAK menulis ke database apa pun selain antrean lokal ini; satu-satunya jalur ke server adalah API analis.
 */
class AntreanAksi(
    private val db: AnalisDb,
    private val pengirim: PengirimAksi,
    private val jam: () -> Long = System::currentTimeMillis,
) {
    private val q get() = db.analisQueries
    private val mutex = Mutex()
    private val _menunggu = MutableStateFlow(q.hitungMenunggu().executeAsOne().toInt())

    /** Jumlah aksi menunggu kirim (untuk ditampilkan di UI). */
    val menunggu: StateFlow<Int> = _menunggu

    fun antre(aksi: ValidasiAnalis) = simpan(aksi.aksiId, JENIS_VALIDASI, FormatJson.json.encodeToString(ValidasiAnalis.serializer(), aksi))

    fun antre(aksi: TahanPrediksi) = simpan(aksi.aksiId, JENIS_TAHAN, FormatJson.json.encodeToString(TahanPrediksi.serializer(), aksi))

    private fun simpan(aksiId: String, jenis: String, payload: String) {
        val t = jam()
        q.antreAksi(aksiId, jenis, payload, t, t) // INSERT OR IGNORE: aksi_id sama tidak menggandakan
        segarkanJumlah()
    }

    /** Coba kirim langsung; bila luring antre (aman diulang). */
    suspend fun kirimAtauAntre(aksi: ValidasiAnalis): HasilKirim = hasil(pengirim.validasi(aksi)) { antre(aksi) }

    suspend fun kirimAtauAntre(aksi: TahanPrediksi): HasilKirim = hasil(pengirim.tahan(aksi)) { antre(aksi) }

    private inline fun hasil(r: Hasil<EventDiterima>, antre: () -> Unit): HasilKirim = when (r) {
        is Hasil.Berhasil -> HasilKirim.Terkirim(r.data.duplikat)
        is Hasil.Galat -> if (bolehDiulang(r)) { antre(); HasilKirim.Diantre } else HasilKirim.Ditolak(r)
        is Hasil.Jaringan -> { antre(); HasilKirim.Diantre }
    }

    /**
     * Kirim semua aksi `menunggu` berurutan. Berhenti pada galat sementara (jaringan, 401, 429, 5xx);
     * galat permanen (400/403/404/409/422) menandai aksi `gagal` dengan alasannya dan lanjut.
     */
    suspend fun kirimTertunda(): LaporanKirim = mutex.withLock {
        var terkirim = 0
        var gagal = 0
        for (a in q.aksiMenunggu().executeAsList()) {
            val hasil = when (a.jenis) {
                JENIS_VALIDASI -> pengirim.validasi(FormatJson.json.decodeFromString(ValidasiAnalis.serializer(), a.payload))
                JENIS_TAHAN -> pengirim.tahan(FormatJson.json.decodeFromString(TahanPrediksi.serializer(), a.payload))
                else -> Hasil.Galat(400, null, "Jenis aksi tidak dikenal: ${a.jenis}")
            }
            when (hasil) {
                is Hasil.Berhasil -> { q.tandaiTerkirim(jam(), a.aksi_id); terkirim++ }
                is Hasil.Galat ->
                    if (bolehDiulang(hasil)) {
                        q.tambahPercobaan("HTTP ${hasil.status}: ${hasil.pesan}", jam(), a.aksi_id)
                        break
                    } else {
                        q.tandaiGagal("HTTP ${hasil.status} ${hasil.kode ?: ""}: ${hasil.pesan}".trim(), jam(), a.aksi_id)
                        gagal++
                    }
                is Hasil.Jaringan -> { q.tambahPercobaan("jaringan: ${hasil.sebab.message}", jam(), a.aksi_id); break }
            }
        }
        segarkanJumlah()
        LaporanKirim(terkirim, gagal, _menunggu.value)
    }

    /** Aksi yang ditolak permanen server (butuh perhatian analis). */
    fun gagal(): List<Pair<String, String>> =
        q.semuaAksi().executeAsList().filter { it.status == "gagal" }.map { it.aksi_id to (it.galat ?: "") }

    private fun bolehDiulang(g: Hasil.Galat) = g.status == 401 || g.status == 429 || g.status >= 500

    private fun segarkanJumlah() {
        _menunggu.value = q.hitungMenunggu().executeAsOne().toInt()
    }

    companion object {
        const val JENIS_VALIDASI = "validasi"
        const val JENIS_TAHAN = "tahan"
    }
}
