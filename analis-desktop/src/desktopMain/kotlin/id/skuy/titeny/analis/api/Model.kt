package id.skuy.titeny.analis.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Tipe turunan docs/api/openapi.yaml (titeny-backend-service, branch PR #10 = feat/ttn-be-005-aksi-analis-audit).
 * Nama properti JSON mengikuti kontrak (snake_case Indonesia); jangan diubah tanpa mengubah kontrak.
 */

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginTantangan(
    @SerialName("sesi_id") val sesiId: String,
    @SerialName("otp_diperlukan") val otpDiperlukan: Boolean,
)

@Serializable
data class OtpRequest(@SerialName("sesi_id") val sesiId: String, val otp: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenPasang(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    /** Detik; kontrak menetapkan 900 (akses 15 menit). */
    @SerialName("expires_in") val expiresIn: Int,
    val peran: String? = null,
)

/** Balasan `POST /auth/login`: oneOf LoginTantangan | TokenPasang (dibedakan lewat `access_token`). */
sealed interface LoginHasil {
    data class Tantangan(val tantangan: LoginTantangan) : LoginHasil
    data class Langsung(val token: TokenPasang) : LoginHasil
}

@Serializable
enum class Keputusan {
    @SerialName("setuju") Setuju,
    @SerialName("koreksi_nilai") KoreksiNilai,
    @SerialName("tolak") Tolak,
}

@Serializable
data class AntreanItem(
    @SerialName("event_id") val eventId: String,
    @SerialName("event_type") val eventType: String,
    val flag: String,
    @SerialName("alasan_flag") val alasanFlag: String? = null,
)

@Serializable
data class AntreanValidasi(
    val data: List<AntreanItem>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class ValidasiAnalis(
    @SerialName("aksi_id") val aksiId: String,
    @SerialName("event_id") val eventId: String,
    val keputusan: Keputusan,
    @SerialName("nilai_baru") val nilaiBaru: Long? = null,
    val alasan: String,
)

@Serializable
data class TahanPrediksi(
    @SerialName("aksi_id") val aksiId: String,
    @SerialName("komoditas_id") val komoditasId: String,
    @SerialName("pasar_id") val pasarId: String,
    val alasan: String,
    @SerialName("berlaku_sampai") val berlakuSampai: String,
)

@Serializable
data class EventDiterima(val status: String) {
    val duplikat: Boolean get() = status == "duplikat_diabaikan"
}

@Serializable
data class GalatBody(val error: GalatIsi)

@Serializable
data class GalatIsi(val kode: String, val pesan: String, val field: String? = null)

/** Konfigurasi JSON tunggal: `nilai_baru` dilarang untuk keputusan selain koreksi_nilai, jadi null tidak dikirim. */
object FormatJson {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

@Serializable
data class PrediksiHarga(
    val tanggal: String,
    val horizon: String,
    @SerialName("harga_prediksi") val hargaPrediksi: Long,
    @SerialName("rentang_bawah") val rentangBawah: Long,
    @SerialName("rentang_atas") val rentangAtas: Long,
    val kepercayaan: String,
    @SerialName("alasan_singkat") val alasanSingkat: String? = null,
)

@Serializable
data class PrediksiHargaRespons(
    @SerialName("komoditas_id") val komoditasId: String,
    @SerialName("pasar_id") val pasarId: String,
    val deret: List<PrediksiHarga>,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("dihitung_pada") val dihitungPada: String,
)

@Serializable
data class PrediksiDemand(
    @SerialName("komoditas_id") val komoditasId: String,
    val zona: String,
    val tanggal: String? = null,
    val horizon: String,
    @SerialName("demand_prediksi_kg") val demandPrediksiKg: Double,
    @SerialName("rentang_bawah_kg") val rentangBawahKg: Double? = null,
    @SerialName("rentang_atas_kg") val rentangAtasKg: Double? = null,
    val sinyal: String,
    val rekomendasi: List<String> = emptyList(),
    @SerialName("model_version") val modelVersion: String,
)

@Serializable
data class PrediksiPanen(
    @SerialName("komoditas_id") val komoditasId: String,
    @SerialName("kabupaten_id") val kabupatenId: String,
    val minggu: String,
    @SerialName("panen_prediksi_kg") val panenPrediksiKg: Long,
    @SerialName("rentang_bawah_kg") val rentangBawahKg: Long? = null,
    @SerialName("rentang_atas_kg") val rentangAtasKg: Long? = null,
    val kepercayaan: String,
    @SerialName("model_version") val modelVersion: String,
)

@Serializable
data class PrediksiPanenRespons(
    val deret: List<PrediksiPanen>,
    @SerialName("dihitung_pada") val dihitungPada: String? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
)
