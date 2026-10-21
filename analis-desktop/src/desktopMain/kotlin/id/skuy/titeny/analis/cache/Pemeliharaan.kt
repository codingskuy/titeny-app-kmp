package id.skuy.titeny.analis.cache

import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Hasil pemeliharaan, untuk log dan uji. */
data class LaporanPemeliharaan(
    val dipangkasUsia: Boolean,
    val dipangkasUkuran: Int,
    val vacuum: Boolean,
)

/**
 * Pemeliharaan cache (FSD/10 butir 4): data > 90 hari dipangkas FIFO, ukuran maks 500 MB,
 * VACUUM tiap Minggu. Aksi berstatus `menunggu` tidak pernah dipangkas (belum terkirim ke server).
 */
class Pemeliharaan(
    private val basis: Basisdata,
    private val jam: () -> Long = System::currentTimeMillis,
    private val batasUsiaHari: Long = BATAS_USIA_HARI,
    private val batasBytes: Long = BATAS_BYTES,
    private val ukuranBytes: () -> Long = { basis.berkas?.let { runCatching { Files.size(it) }.getOrDefault(0L) } ?: 0L },
    private val zona: ZoneId = WIB,
) {
    private val q get() = basis.db.analisQueries

    fun jalankan(): LaporanPemeliharaan {
        val sekarang = jam()
        val usia = pangkasUsia(sekarang)
        val ukuran = pangkasUkuran()
        var divacuum = false
        if (perluVacuum(sekarang, q.bacaMeta(META_VACUUM).executeAsOneOrNull()?.toLongOrNull(), zona)) {
            vacuum()
            q.simpanMeta(META_VACUUM, sekarang.toString())
            divacuum = true
        }
        return LaporanPemeliharaan(usia, ukuran, divacuum)
    }

    /** Hapus data tersinkron lebih tua dari [batasUsiaHari]. */
    fun pangkasUsia(sekarang: Long = jam()): Boolean {
        val batas = sekarang - TimeUnit.DAYS.toMillis(batasUsiaHari)
        val sebelum = q.hitungBarisPemangkasan().executeAsOne()
        basis.db.transaction {
            q.pangkasAntreanCache(batas)
            q.pangkasCacheJson(batas)
            q.pangkasAksiSelesai(batas)
        }
        return q.hitungBarisPemangkasan().executeAsOne() < sebelum
    }

    /** Selama berkas melebihi batas, buang baris terlama (FIFO) lalu VACUUM agar ruang kembali ke OS. */
    fun pangkasUkuran(): Int {
        var dihapus = 0
        var putaran = 0
        while (ukuranBytes() > batasBytes && putaran < MAKS_PUTARAN) {
            val baris = q.hitungBarisPemangkasan().executeAsOne()
            if (baris == 0L) break // sisanya hanya aksi menunggu: tidak boleh dibuang
            val n = maxOf(BATCH, baris / 10)
            basis.db.transaction {
                q.hapusAntreanCacheTerlama(n)
                q.hapusCacheJsonTerlama(n)
                q.hapusAksiSelesaiTerlama(n)
            }
            dihapus += (baris - q.hitungBarisPemangkasan().executeAsOne()).toInt()
            vacuum()
            putaran++
        }
        return dihapus
    }

    fun vacuum() {
        basis.driver.execute(null, "VACUUM", 0)
    }

    companion object {
        const val BATAS_USIA_HARI = 90L
        const val BATAS_BYTES = 500L * 1024 * 1024
        const val META_VACUUM = "vacuum_terakhir"
        private const val BATCH = 100L
        private const val MAKS_PUTARAN = 50
        private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

        /**
         * VACUUM tiap Minggu (WIB): jatuh tempo bila hari ini Minggu dan belum divacuum hari ini,
         * atau bila belum pernah / sudah lebih dari 7 hari (menyusul bila aplikasi tidak dibuka Minggu).
         */
        fun perluVacuum(sekarangMs: Long, terakhirMs: Long?, zona: ZoneId = WIB): Boolean {
            if (terakhirMs == null) return true
            if (sekarangMs - terakhirMs > TimeUnit.DAYS.toMillis(7)) return true
            val hariIni = Instant.ofEpochMilli(sekarangMs).atZone(zona).toLocalDate()
            val terakhirTgl = Instant.ofEpochMilli(terakhirMs).atZone(zona).toLocalDate()
            return hariIni.dayOfWeek == DayOfWeek.SUNDAY && terakhirTgl != hariIni
        }
    }
}
