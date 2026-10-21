package id.skuy.titeny.analis.validasi

import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.Keputusan
import id.skuy.titeny.analis.api.ValidasiAnalis
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

const val ALASAN_MIN = 10
const val ALASAN_MAKS = 140

/**
 * Form keputusan analis (kontrak POST /api/v1/analis/validasi):
 * `alasan` wajib 10-140 karakter; `nilai_baru` wajib (> 0, integer IDR) untuk `koreksi_nilai`
 * dan DILARANG untuk keputusan lain (server 422).
 */
data class FormValidasi(
    val keputusan: Keputusan? = null,
    val alasan: String = "",
    val nilaiBaru: String = "",
) {
    val panjangAlasan: Int get() = alasan.trim().let { it.codePointCount(0, it.length) }

    val alasanValid: Boolean get() = panjangAlasan in ALASAN_MIN..ALASAN_MAKS

    val nilaiBaruAngka: Long? get() = nilaiBaru.trim().toLongOrNull()?.takeIf { it > 0 }

    val nilaiValid: Boolean
        get() = when (keputusan) {
            Keputusan.KoreksiNilai -> nilaiBaruAngka != null
            else -> true
        }

    /** Tombol kirim aktif hanya bila keputusan dipilih, alasan valid, dan nilai (bila koreksi) valid. */
    val bisaKirim: Boolean get() = keputusan != null && alasanValid && nilaiValid

    fun keRequest(aksiId: String, eventId: String): ValidasiAnalis? {
        if (!bisaKirim) return null
        return ValidasiAnalis(
            aksiId = aksiId,
            eventId = eventId,
            keputusan = keputusan!!,
            nilaiBaru = if (keputusan == Keputusan.KoreksiNilai) nilaiBaruAngka else null,
            alasan = alasan.trim(),
        )
    }
}

private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")
private val FORMAT_TANGGAL: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

/**
 * Pembuat `aksi_id`. Kontrak hanya memberi contoh `aks_YYYYMMDD_NNN` (string maks 64 karakter);
 * NNN diganti 8 heksa acak agar unik tanpa penghitung server (ditandai sebagai asumsi di PR).
 */
fun buatAksiId(hariIni: LocalDate = LocalDate.now(WIB), acak: () -> String = { UUID.randomUUID().toString().take(8) }): String =
    "aks_${hariIni.format(FORMAT_TANGGAL)}_${acak()}"

/** Pesan untuk analis dari galat API (status HTTP -> kalimat Indonesia). */
fun pesanGalat(g: Hasil.Galat): String = when (g.status) {
    401 -> "Sesi berakhir; masuk ulang dengan OTP."
    403 -> "Peran Anda tidak diizinkan untuk aksi ini."
    404 -> "Flag tidak ditemukan."
    409 -> "Flag sudah diputuskan atau tidak lagi berstatus perlu_verifikasi."
    422 -> g.pesan + (g.field?.let { " (kolom: $it)" } ?: "")
    429 -> "Terlalu banyak permintaan" + (g.retryAfterDetik?.let { "; coba lagi dalam $it detik." } ?: ".")
    else -> g.pesan
}
