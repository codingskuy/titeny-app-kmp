package id.skuy.titeny.analis.ui

import id.skuy.titeny.analis.antrean.PengelolaAntrean
import id.skuy.titeny.analis.api.ApiKlien

/** Wadah dependensi UI; dibuat sekali di `main`. */
class Layanan(
    val api: ApiKlien,
    val antrean: PengelolaAntrean = PengelolaAntrean(api),
)
