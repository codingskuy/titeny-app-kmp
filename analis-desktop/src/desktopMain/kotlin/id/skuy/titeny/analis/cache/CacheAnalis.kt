package id.skuy.titeny.analis.cache

import id.skuy.titeny.analis.api.AntreanItem
import id.skuy.titeny.analis.db.AnalisDb
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Isi cache antrean + stempel waktu sinkron terakhir yang berhasil (epoch ms). */
data class CacheAntrean(val item: List<AntreanItem>, val stempelMs: Long)

private val FORMAT_STEMPEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").withZone(ZoneId.of("Asia/Jakarta"))

/** Banner luring sesuai produk/22 §2: "Luring — data 2026-10-03 23.00" (waktu WIB). */
fun bannerLuring(stempelMs: Long): String = "Luring — data ${FORMAT_STEMPEL.format(Instant.ofEpochMilli(stempelMs))}"

/**
 * Cache baca lokal. Server tetap sumber kebenaran: tidak ada jalur tulis ke DB sumber dari klien;
 * cache hanya menyimpan salinan hasil API.
 */
class CacheAnalis(private val db: AnalisDb) {
    private val q get() = db.analisQueries

    /** Ganti seluruh salinan antrean dengan hasil sinkron terbaru (antrean kosong pun tercatat lewat stempel). */
    fun simpanAntrean(item: List<AntreanItem>, waktuMs: Long) {
        db.transaction {
            q.hapusAntreanCache()
            item.forEach { q.simpanAntreanCache(it.eventId, it.eventType, it.flag, it.alasanFlag, waktuMs) }
            q.simpanMeta(META_STEMPEL_ANTREAN, waktuMs.toString())
        }
    }

    fun bacaAntrean(): CacheAntrean? {
        val stempel = q.bacaMeta(META_STEMPEL_ANTREAN).executeAsOneOrNull()?.toLongOrNull() ?: return null
        val item = q.bacaAntreanCache().executeAsList().map { AntreanItem(it.event_id, it.event_type, it.flag, it.alasan_flag) }
        return CacheAntrean(item, stempel)
    }

    fun simpanJson(kunci: String, nilai: String, waktuMs: Long) = q.simpanJson(kunci, nilai, waktuMs)

    /** (isi JSON, waktu ambil) atau null. */
    fun bacaJson(kunci: String): Pair<String, Long>? =
        q.bacaJson(kunci).executeAsOneOrNull()?.let { it.nilai to it.diambil_pada }

    companion object {
        const val META_STEMPEL_ANTREAN = "antrean_stempel"
    }
}
