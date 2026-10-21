package id.skuy.titeny.analis.antrean

import id.skuy.titeny.analis.api.AntreanItem
import id.skuy.titeny.analis.api.SumberAntrean
import id.skuy.titeny.analis.api.Hasil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Interval polling antrean validasi: 5 menit (produk/22 §2). */
const val INTERVAL_ANTREAN_MS = 5L * 60 * 1000

sealed interface StateAntrean {
    data object Memuat : StateAntrean
    data class Siap(val item: List<AntreanItem>, val diperbaruiMs: Long) : StateAntrean
    /** Galat; [item] terakhir yang berhasil dimuat tetap ditampilkan bila ada. */
    data class Gagal(val pesan: String, val item: List<AntreanItem> = emptyList(), val diperbaruiMs: Long? = null) : StateAntrean
}

class PengelolaAntrean(
    private val api: SumberAntrean,
    private val jam: () -> Long = System::currentTimeMillis,
    private val halaman: Int = 200,
) {
    private val _state = MutableStateFlow<StateAntrean>(StateAntrean.Memuat)
    val state: StateFlow<StateAntrean> = _state

    /** Muat semua halaman antrean (keyset) lalu perbarui state. */
    suspend fun muat() {
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
                    _state.value = gagal(terakhir, "Gagal memuat antrean: ${r.pesan}")
                    return
                }
                is Hasil.Jaringan -> {
                    _state.value = gagal(terakhir, "Tidak dapat terhubung ke server.")
                    return
                }
            }
        } while (cursor != null)
        _state.value = StateAntrean.Siap(semua, jam())
    }

    /** Hapus item yang baru diputuskan agar tampilan langsung konsisten sebelum polling berikutnya. */
    fun hapusLokal(eventId: String) {
        val s = _state.value
        if (s is StateAntrean.Siap) _state.value = s.copy(item = s.item.filterNot { it.eventId == eventId })
    }

    fun cari(eventId: String): AntreanItem? = when (val s = _state.value) {
        is StateAntrean.Siap -> s.item.firstOrNull { it.eventId == eventId }
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

    private fun gagal(terakhir: StateAntrean, pesan: String): StateAntrean.Gagal = when (terakhir) {
        is StateAntrean.Siap -> StateAntrean.Gagal(pesan, terakhir.item, terakhir.diperbaruiMs)
        is StateAntrean.Gagal -> terakhir.copy(pesan = pesan)
        StateAntrean.Memuat -> StateAntrean.Gagal(pesan)
    }
}
