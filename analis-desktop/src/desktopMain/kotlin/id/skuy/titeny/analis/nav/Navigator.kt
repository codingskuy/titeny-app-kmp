package id.skuy.titeny.analis.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import java.net.URI
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface HasilDeepLink {
    data class Dibuka(val rute: Rute.DetailFlag) : HasilDeepLink
    /** Belum masuk: tujuan disimpan dan dibuka setelah login berhasil. */
    data class Tertunda(val rute: Rute.DetailFlag) : HasilDeepLink
    data class Ditolak(val alasan: String) : HasilDeepLink
}

/**
 * Pemilik back stack Navigation3 (daftar `Rute` yang dibaca `NavDisplay`).
 * State dipulihkan lewat [SavedStateHandle]; back stack maks [BATAS_TUMPUKAN].
 */
class Navigator(private val handle: SavedStateHandle = SavedStateHandle()) {
    private val _tumpukan = mutableStateListOf<Rute>()
    val tumpukan: List<Rute> get() = _tumpukan

    var sudahMasuk by mutableStateOf(false)
        private set

    private var tertunda: Rute.DetailFlag? = null

    private val _segarkan = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Dipicu Ctrl+R; layar mengumpulkan sinyal ini untuk memuat ulang. */
    val segarkan: SharedFlow<Unit> = _segarkan.asSharedFlow()

    val rutePuncak: Rute get() = _tumpukan.last()

    init {
        val tersimpan = handle.get<ArrayList<String>>(KUNCI_TUMPUKAN)
            ?.mapNotNull(Rute::dariKode)
            ?.takeIf { it.isNotEmpty() }
        sudahMasuk = handle.get<Boolean>(KUNCI_MASUK) ?: false
        _tumpukan.addAll(tersimpan ?: listOf(Rute.Masuk))
        // Tumpukan tanpa sesi tidak boleh memuat layar selain Masuk.
        if (!sudahMasuk && _tumpukan.any { it != Rute.Masuk }) {
            _tumpukan.clear()
            _tumpukan.add(Rute.Masuk)
        }
    }

    fun buka(rute: Rute): Boolean {
        if (!sudahMasuk && rute != Rute.Masuk) return false
        if (rute == Rute.Masuk && sudahMasuk) return false
        if (_tumpukan.last() == rute) return false
        _tumpukan.add(rute)
        while (_tumpukan.size > BATAS_TUMPUKAN) _tumpukan.removeAt(1) // akar tetap
        simpan()
        return true
    }

    /** Esc / tombol kembali. Tidak pernah mengosongkan tumpukan. */
    fun kembali(): Boolean {
        if (_tumpukan.size <= 1) return false
        _tumpukan.removeAt(_tumpukan.lastIndex)
        simpan()
        return true
    }

    fun masukBerhasil() {
        sudahMasuk = true
        _tumpukan.clear()
        _tumpukan.add(Rute.AntreanValidasi)
        tertunda?.let { _tumpukan.add(it) }
        tertunda = null
        simpan()
    }

    fun keluar() {
        sudahMasuk = false
        tertunda = null
        _tumpukan.clear()
        _tumpukan.add(Rute.Masuk)
        simpan()
    }

    /** Tautan `titeny://analis/flag/<eventId>` membuka DetailFlag. */
    fun bukaDeepLink(tautan: String): HasilDeepLink {
        val rute = urai(tautan) ?: return HasilDeepLink.Ditolak("Tautan tidak dikenal: $tautan")
        if (!sudahMasuk) {
            tertunda = rute
            return HasilDeepLink.Tertunda(rute)
        }
        buka(rute)
        return HasilDeepLink.Dibuka(rute)
    }

    fun tanganiPintasan(aksi: AksiPintasan) {
        when (aksi) {
            AksiPintasan.Segarkan -> if (sudahMasuk) _segarkan.tryEmit(Unit)
            AksiPintasan.Ekspor -> buka(Rute.Ekspor)
            AksiPintasan.Kembali -> kembali()
        }
    }

    private fun simpan() {
        handle[KUNCI_TUMPUKAN] = ArrayList(_tumpukan.map(Rute::kode))
        handle[KUNCI_MASUK] = sudahMasuk
    }

    companion object {
        const val BATAS_TUMPUKAN = 20
        private const val KUNCI_TUMPUKAN = "tumpukan"
        private const val KUNCI_MASUK = "sudah_masuk"

        fun urai(tautan: String): Rute.DetailFlag? {
            val uri = runCatching { URI(tautan.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "titeny" || uri.host != "analis") return null
            val segmen = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            if (segmen.size != 2 || segmen[0] != "flag") return null
            return segmen[1].takeIf(PolaEventId::matches)?.let(Rute::DetailFlag)
        }
    }
}
