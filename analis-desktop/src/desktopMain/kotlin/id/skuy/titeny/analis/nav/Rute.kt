package id.skuy.titeny.analis.nav

/** Pola `event_id` terkunci (KONTEKS-LINTAS-DIVISI bagian 6): evt_YYYYMMDD_NNNNNN. */
val PolaEventId = Regex("^evt_\\d{8}_\\d{6}$")

/**
 * Graf Navigation3 desktop analis, terkunci 7 layar (FSD/10-alur "Urutan Layar Web dan Desktop").
 * Penambahan layar = PRD/FSD versi baru di Titeny-TownHall, bukan perubahan di sini.
 */
sealed interface Rute {
    /** Kode stabil untuk SavedStateHandle dan log. */
    val kode: String

    data object Masuk : Rute { override val kode = "masuk" }
    data object AntreanValidasi : Rute { override val kode = "antrean" }

    data class DetailFlag(val eventId: String) : Rute {
        init {
            require(PolaEventId.matches(eventId)) { "eventId tidak sesuai pola evt_YYYYMMDD_NNNNNN: $eventId" }
        }

        override val kode get() = "flag/$eventId"
    }

    data object TahanPrediksi : Rute { override val kode = "tahan" }
    data object BandingModel : Rute { override val kode = "banding" }
    data object KelolaRekomendasi : Rute { override val kode = "rekomendasi" }
    data object Ekspor : Rute { override val kode = "ekspor" }

    companion object {
        /** Tujuh layar terkunci (DetailFlag diwakili satu entri tak berparameter di dokumentasi). */
        const val JUMLAH_LAYAR = 7

        fun dariKode(kode: String): Rute? = when {
            kode == "masuk" -> Masuk
            kode == "antrean" -> AntreanValidasi
            kode.startsWith("flag/") -> kode.removePrefix("flag/").takeIf(PolaEventId::matches)?.let(::DetailFlag)
            kode == "tahan" -> TahanPrediksi
            kode == "banding" -> BandingModel
            kode == "rekomendasi" -> KelolaRekomendasi
            kode == "ekspor" -> Ekspor
            else -> null
        }
    }
}
