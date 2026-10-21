package id.skuy.titeny.analis.antrean

import id.skuy.titeny.analis.api.FormatJson
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.PrediksiHargaRespons
import id.skuy.titeny.analis.api.SumberPrediksi
import id.skuy.titeny.analis.cache.CacheAnalis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Hasil baca prediksi harga; [dariCache] true = luring, [diambilMs] = stempel sinkron data. */
data class PrediksiHargaTersaji(val data: PrediksiHargaRespons, val diambilMs: Long, val dariCache: Boolean)

/**
 * Prediksi harga untuk layar analis: online ke API, luring ke cache SQLite.
 * Pasangan komoditas-pasar diberikan pemanggil: openapi.yaml belum menyediakan katalog ID
 * (GAP kontrak; ID `cmd_*`/`psr_*` hanya diberi contoh).
 */
class PengelolaPrediksi(
    private val api: SumberPrediksi,
    private val cache: CacheAnalis,
    private val jam: () -> Long = System::currentTimeMillis,
) {
    suspend fun muat(komoditasId: String, pasarId: String): PrediksiHargaTersaji? {
        val kunci = kunci(komoditasId, pasarId)
        when (val r = api.harga(komoditasId, pasarId)) {
            is Hasil.Berhasil -> {
                val kini = jam()
                cache.simpanJson(kunci, FormatJson.json.encodeToString(PrediksiHargaRespons.serializer(), r.data), kini)
                return PrediksiHargaTersaji(r.data, kini, dariCache = false)
            }
            is Hasil.Galat -> if (r.status < 500) return null // mis. 404 data_belum_cukup: bukan luring
            is Hasil.Jaringan -> Unit
        }
        val (json, waktu) = cache.bacaJson(kunci) ?: return null
        return PrediksiHargaTersaji(FormatJson.json.decodeFromString(PrediksiHargaRespons.serializer(), json), waktu, dariCache = true)
    }

    /** Muat semua [pasangan] tiap [intervalMs] (15 menit) atau saat [segarkan] menyala. */
    suspend fun polling(
        pasangan: () -> List<Pair<String, String>>,
        segarkan: Flow<Unit>,
        intervalMs: Long = INTERVAL_PREDIKSI_MS,
        saatMuat: (Pair<String, String>, PrediksiHargaTersaji?) -> Unit = { _, _ -> },
    ) {
        while (coroutineContext.isActive) {
            pasangan().forEach { saatMuat(it, muat(it.first, it.second)) }
            withTimeoutOrNull(intervalMs) { segarkan.first() }
        }
    }

    companion object {
        fun kunci(komoditasId: String, pasarId: String) = "harga:$komoditasId:$pasarId"
    }
}
