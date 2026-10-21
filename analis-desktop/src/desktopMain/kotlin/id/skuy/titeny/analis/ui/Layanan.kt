package id.skuy.titeny.analis.ui

import id.skuy.titeny.analis.aksi.AntreanAksi
import id.skuy.titeny.analis.antrean.PengelolaAntrean
import id.skuy.titeny.analis.antrean.PengelolaPrediksi
import id.skuy.titeny.analis.api.ApiKlien
import id.skuy.titeny.analis.cache.Basisdata
import id.skuy.titeny.analis.cache.CacheAnalis
import id.skuy.titeny.analis.cache.LokasiDb
import id.skuy.titeny.analis.cache.Pemeliharaan
import id.skuy.titeny.analis.ekspor.Eksportir
import id.skuy.titeny.analis.ekspor.KatalogEkspor
import java.nio.file.Path

/** Wadah dependensi UI; dibuat sekali di `main`. */
class Layanan(
    val api: ApiKlien,
    val basis: Basisdata,
    val cache: CacheAnalis = CacheAnalis(basis.db),
    val aksi: AntreanAksi = AntreanAksi(basis.db, api),
    val antrean: PengelolaAntrean = PengelolaAntrean(api, cache = cache, sebelumMuat = { aksi.kirimTertunda() }),
    val prediksi: PengelolaPrediksi = PengelolaPrediksi(api, cache),
    val pemeliharaan: Pemeliharaan = Pemeliharaan(basis),
    val folderEkspor: Path = Path.of(System.getProperty("user.home"), "Documents", "Titeny", "Ekspor"),
    val berkasKatalog: Path = LokasiDb.tentukan().resolveSibling("katalog.json"),
    val eksportir: Eksportir = Eksportir(api, basis.db, folderEkspor),
) {
    /** Katalog ID untuk ekspor (pasar/zona dari katalog.json; lihat [KatalogEkspor]). */
    fun katalog(): KatalogEkspor = runCatching { KatalogEkspor.dariBerkas(berkasKatalog) }.getOrDefault(KatalogEkspor())
}
