package id.skuy.titeny.analis.api

/**
 * Alamat API. Kontrak openapi.yaml: produksi `https://api.titeny.chefgenie.id`, lokal `http://localhost:8080`
 * (path sudah memuat `/api/v1`). `produk/22` menyebut `https://insight.titeny.id/api/v1`: dokumen berbeda,
 * jadi alamat dapat diganti lewat variabel lingkungan TITENY_API_URL; bawaan = lokal.
 */
object Konfigurasi {
    const val URL_LOKAL = "http://localhost:8080"

    fun urlApi(env: (String) -> String? = System::getenv): String =
        env("TITENY_API_URL")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: URL_LOKAL
}
