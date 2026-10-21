package id.skuy.titeny.analis.antrean

import id.skuy.titeny.analis.api.AntreanItem
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.SumberAntrean
import id.skuy.titeny.analis.cache.CacheAnalis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Interval polling antrean validasi: 5 menit (produk/22 §2). */
const val INTERVAL_ANTREAN_MS = 5L * 60 * 1000

/** Interval polling prediksi: 15 menit (produk/22 §2). */
const val INTERVAL_PREDIKSI_MS = 15L * 60 * 1000

sealed interface StateAntrean {
    data object Memuat : StateAntrean
    data class Siap(val item: List<AntreanItem>, val diperbaruiMs: Long) : StateAntrean

    /** Server tak terjangkau: menampilkan cache terakhir dengan stempel waktu sinkron. */
    data class Luring(val item: List<AntreanItem>, val stempelMs: Long) : StateAntrean

    /** Galat tanpa cache; [item] terakhir yang berhasil dimuat tetap ditampilkan bila ada. */
    data class Gagal(val pesan: String, val item: List<AntreanItem> = emptyList(), val diperbaruiMs: Long? = null) : StateAntrean
}

class PengelolaAntrean(
    private val api: SumberAntrean,
    private val jam: () -> Long = System::currentTimeMillis,
    private val halaman: Int = 200,
    private val cache: CacheAnalis? = null,
    /** Dipanggil sebelum tiap muat (mis. kirim aksi tertunda dari antrean luring). */
    private val sebelumMuat: suspend () -> Unit = {},
) {
    private val _state = MutableStateFlow<StateAntrean>(StateAntrean.Memuat)
    val state: StateFlow<StateAntrean> = _state

    /** Muat semua halaman antrean (keyset) lalu perbarui state; jatuh ke cache bila luring (jaringan putus atau 5xx). */
    suspend fun muat() {
        sebelumMuat()
        val terakhir = _state.value
        val semua = mutableListOf<AntreanItem>()
        var cursor: String? = null
        do {
            when (val r = api.antrean(limit = halaman, cursor = cursor)) {
                is Hasil.Berhasil -> {
                    semua += r.data.data
                    cursor = r.data.nextCursor
                }
                is Hasil.Galat -> {
                    _state.value =
                        if (r.status >= 500) luring(terakhir, "Server bermasalah (${r.status}).")
                        else gagal(terakhir, "Gagal memuat antrean: ${r.pesan}")
                    return
                }
                is Hasil.Jaringan -> {
                    _state.value = luring(terakhir, "Tidak dapat terhubung ke server.")
                    return
                }
            }
        } while (cursor != null)
        val kini = jam()
        cache?.simpanAntrean(semua, kini)
        _state.value = StateAntrean.Siap(semua, kini)
    }

    /** Hapus item yang baru diputuskan agar tampilan langsung konsisten sebelum polling berikutnya. */
    fun hapusLokal(eventId: String) {
        _state.value = when (val s = _state.value) {
            is StateAntrean.Siap -> s.copy(item = s.item.filterNot { it.eventId == eventId })
            is StateAntrean.Luring -> s.copy(item = s.item.filterNot { it.eventId == eventId })
            else -> s
        }
    }

    fun cari(eventId: String): AntreanItem? = when (val s = _state.value) {
        is StateAntrean.Siap -> s.item.firstOrNull { it.eventId == eventId }
        is StateAntrean.Luring -> s.item.firstOrNull { it.eventId == eventId }
        is StateAntrean.Gagal -> s.item.firstOrNull { it.eventId == eventId }
        StateAntrean.Memuat -> null
    }

    /**
     * Polling: muat, lalu tunggu [intervalMs] atau sinyal [segarkan] (tombol Segarkan / Ctrl+R),
     * mana yang lebih dulu. Berjalan sampai coroutine dibatalkan.
     */
    suspend fun polling(segarkan: Flow<Unit>, intervalMs: Long = INTERVAL_ANTREAN_MS) {
        while (coroutineContext.isActive) {
            muat()
            withTimeoutOrNull(intervalMs) { segarkan.first() }
        }
    }

    /** Luring: pakai data sesi ini bila ada, jika tidak cache SQLite; tanpa keduanya, galat biasa. */
    private fun luring(terakhir: StateAntrean, pesan: String): StateAntrean {
        when (terakhir) {
            is StateAntrean.Siap -> return StateAntrean.Luring(terakhir.item, terakhir.diperbaruiMs)
            is StateAntrean.Luring -> return terakhir
            else -> Unit
        }
        val tersimpan = cache?.bacaAntrean() ?: return gagal(terakhir, pesan)
        return StateAntrean.Luring(tersimpan.item, tersimpan.stempelMs)
    }

    private fun gagal(terakhir: StateAntrean, pesan: String): StateAntrean.Gagal = when (terakhir) {
        is StateAntrean.Siap -> StateAntrean.Gagal(pesan, terakhir.item, terakhir.diperbaruiMs)
        is StateAntrean.Luring -> StateAntrean.Gagal(pesan, terakhir.item, terakhir.stempelMs)
        is StateAntrean.Gagal -> terakhir.copy(pesan = pesan)
        StateAntrean.Memuat -> StateAntrean.Gagal(pesan)
    }
}
