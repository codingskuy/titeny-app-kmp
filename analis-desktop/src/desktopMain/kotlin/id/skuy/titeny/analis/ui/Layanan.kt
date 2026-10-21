package id.skuy.titeny.analis.ui

import id.skuy.titeny.analis.aksi.AntreanAksi
import id.skuy.titeny.analis.antrean.PengelolaAntrean
import id.skuy.titeny.analis.antrean.PengelolaPrediksi
import id.skuy.titeny.analis.api.ApiKlien
import id.skuy.titeny.analis.cache.Basisdata
import id.skuy.titeny.analis.cache.CacheAnalis
import id.skuy.titeny.analis.cache.Pemeliharaan

/** Wadah dependensi UI; dibuat sekali di `main`. */
class Layanan(
    val api: ApiKlien,
    val basis: Basisdata,
    val cache: CacheAnalis = CacheAnalis(basis.db),
    val aksi: AntreanAksi = AntreanAksi(basis.db, api),
    val antrean: PengelolaAntrean = PengelolaAntrean(api, cache = cache, sebelumMuat = { aksi.kirimTertunda() }),
    val prediksi: PengelolaPrediksi = PengelolaPrediksi(api, cache),
    val pemeliharaan: Pemeliharaan = Pemeliharaan(basis),
)
