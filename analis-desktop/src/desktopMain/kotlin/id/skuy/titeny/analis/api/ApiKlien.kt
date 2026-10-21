package id.skuy.titeny.analis.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Aksi tulis analis (diimplementasikan [ApiKlien]; dipisah agar antrean luring dapat diuji tanpa jaringan). */
interface PengirimAksi {
    suspend fun validasi(aksi: ValidasiAnalis): Hasil<EventDiterima>
    suspend fun tahan(aksi: TahanPrediksi): Hasil<EventDiterima>
}

/** Prediksi harga (GET /api/v1/harga). */
interface SumberPrediksi {
    suspend fun harga(komoditasId: String, pasarId: String, dari: String? = null, sampai: String? = null): Hasil<PrediksiHargaRespons>
}

/** Sumber antrean validasi (diimplementasikan [ApiKlien]; dipisah agar pengelola dapat diuji tanpa jaringan). */
interface SumberAntrean {
    suspend fun antrean(limit: Int = 50, cursor: String? = null): Hasil<AntreanValidasi>
}

/** Hasil panggilan API: sukses, galat dari server (amplop `AppError`), atau kegagalan jaringan. */
sealed interface Hasil<out T> {
    data class Berhasil<T>(val data: T) : Hasil<T>

    data class Galat(
        val status: Int,
        val kode: String?,
        val pesan: String,
        val field: String? = null,
        val retryAfterDetik: Int? = null,
    ) : Hasil<Nothing>

    data class Jaringan(val sebab: Throwable) : Hasil<Nothing>
}

/**
 * Klien REST analis. Kontrak: docs/api/openapi.yaml titeny-backend-service (bergantung pada PR #7-#10).
 * Token disimpan lewat [PenyimpanToken]; refresh otomatis sekali bila 401 (refresh sekali pakai,
 * dengan reuse-detection di server, jadi refresh diserialkan lewat mutex).
 */
class ApiKlien(
    engine: HttpClientEngine,
    private val baseUrl: String,
    private val penyimpan: PenyimpanToken,
    private val jam: () -> Long = System::currentTimeMillis,
) : SumberAntrean, PengirimAksi, SumberPrediksi {
    private val json = FormatJson.json

    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = false
    }

    private val mutexRefresh = Mutex()

    fun tutup() = http.close()

    // ---------- auth

    suspend fun login(username: String, password: String): Hasil<LoginHasil> {
        val r = kirim(auth = false) {
            method = HttpMethod.Post
            ke("/api/v1/auth/login")
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return when (r) {
            is Hasil.Berhasil -> {
                val teks = r.data.bodyAsText()
                val obj = json.parseToJsonElement(teks) as? JsonObject
                if (obj != null && "access_token" in obj) {
                    val token = json.decodeFromString(TokenPasang.serializer(), teks)
                    // Peran layanan: tanpa OTP, tidak ada batas sesi 8 jam yang berlaku untuk analis.
                    simpanToken(token, sesiBerakhirPada = jam() + SESI_ANALIS_MS)
                    Hasil.Berhasil(LoginHasil.Langsung(token))
                } else {
                    Hasil.Berhasil(LoginHasil.Tantangan(json.decodeFromString(LoginTantangan.serializer(), teks)))
                }
            }
            is Hasil.Galat -> r
            is Hasil.Jaringan -> r
        }
    }

    suspend fun otpVerifikasi(sesiId: String, otp: String): Hasil<TokenPasang> {
        val r = kirim(auth = false) {
            method = HttpMethod.Post
            ke("/api/v1/auth/otp/verifikasi")
            contentType(ContentType.Application.Json)
            setBody(OtpRequest(sesiId, otp))
        }.keHasil<TokenPasang>()
        if (r is Hasil.Berhasil) simpanToken(r.data, sesiBerakhirPada = jam() + SESI_ANALIS_MS)
        return r
    }

    suspend fun keluar() = penyimpan.hapus()

    suspend fun sudahPunyaSesi(): Boolean = penyimpan.baca()?.let { jam() < it.sesiBerakhirPadaMs } ?: false

    // ---------- analis

    override suspend fun antrean(limit: Int, cursor: String?): Hasil<AntreanValidasi> =
        kirim(auth = true) {
            method = HttpMethod.Get
            ke("/api/v1/analis/antrean")
            parameter("limit", limit)
            cursor?.let { parameter("cursor", it) }
        }.keHasil()

    override suspend fun validasi(aksi: ValidasiAnalis): Hasil<EventDiterima> =
        kirim(auth = true) {
            method = HttpMethod.Post
            ke("/api/v1/analis/validasi")
            header("X-Idempotency-Key", aksi.aksiId) // wajib dan sama dengan aksi_id (422 bila beda)
            contentType(ContentType.Application.Json)
            setBody(aksi)
        }.keHasil()

    override suspend fun tahan(aksi: TahanPrediksi): Hasil<EventDiterima> =
        kirim(auth = true) {
            method = HttpMethod.Post
            ke("/api/v1/analis/tahan")
            header("X-Idempotency-Key", aksi.aksiId)
            contentType(ContentType.Application.Json)
            setBody(aksi)
        }.keHasil()

    override suspend fun harga(komoditasId: String, pasarId: String, dari: String?, sampai: String?): Hasil<PrediksiHargaRespons> =
        kirim(auth = true) {
            method = HttpMethod.Get
            ke("/api/v1/harga")
            parameter("komoditas_id", komoditasId)
            parameter("pasar_id", pasarId)
            dari?.let { parameter("dari", it) }
            sampai?.let { parameter("sampai", it) }
        }.keHasil()

    // ---------- internal

    private fun HttpRequestBuilder.ke(path: String) = url("$baseUrl$path")

    private suspend fun simpanToken(t: TokenPasang, sesiBerakhirPada: Long) =
        penyimpan.simpan(SesiToken(t.accessToken, t.refreshToken, sesiBerakhirPada))

    private suspend fun kirim(auth: Boolean, bangun: HttpRequestBuilder.() -> Unit): Hasil<HttpResponse> = try {
        if (!auth) {
            Hasil.Berhasil(http.request { bangun() })
        } else {
            val token = penyimpan.baca()
            when {
                token == null -> galatLokal("tidak_berhak", "Belum masuk.")
                jam() >= token.sesiBerakhirPadaMs -> {
                    penyimpan.hapus()
                    galatLokal("sesi_berakhir", "Sesi analis berakhir (maks 8 jam); masuk ulang dengan OTP.")
                }
                else -> {
                    var resp = http.request { bangun(); bearerAuth(token.accessToken) }
                    if (resp.status == HttpStatusCode.Unauthorized) {
                        val baru = segarkan(token)
                        resp = if (baru != null) http.request { bangun(); bearerAuth(baru.accessToken) } else resp
                    }
                    Hasil.Berhasil(resp)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Hasil.Jaringan(e)
    }

    private fun galatLokal(kode: String, pesan: String) = Hasil.Galat(401, kode, pesan)

    private suspend fun segarkan(lama: SesiToken): SesiToken? = mutexRefresh.withLock {
        val kini = penyimpan.baca() ?: return null
        if (kini.accessToken != lama.accessToken) return kini // sudah disegarkan oleh panggilan lain
        val resp = http.request {
            method = HttpMethod.Post
            ke("/api/v1/auth/refresh")
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(kini.refreshToken))
        }
        if (!resp.status.isSuccess()) {
            penyimpan.hapus() // refresh ditolak/dipakai ulang: sesi dicabut server
            return null
        }
        val token = resp.body<TokenPasang>()
        val baru = SesiToken(token.accessToken, token.refreshToken, kini.sesiBerakhirPadaMs)
        penyimpan.simpan(baru)
        baru
    }

    private suspend inline fun <reified T> Hasil<HttpResponse>.keHasil(): Hasil<T> = when (this) {
        is Hasil.Berhasil -> {
            val resp = data
            if (resp.status.isSuccess()) {
                Hasil.Berhasil(resp.body<T>())
            } else {
                val galat = runCatching { resp.body<GalatBody>().error }.getOrNull()
                Hasil.Galat(
                    status = resp.status.value,
                    kode = galat?.kode,
                    pesan = galat?.pesan ?: "Galat ${resp.status.value}",
                    field = galat?.field,
                    retryAfterDetik = resp.headers[HttpHeaders.RetryAfter]?.toIntOrNull(),
                )
            }
        }
        is Hasil.Galat -> this
        is Hasil.Jaringan -> this
    }

    companion object {
        /** Batas sesi analis: 8 jam sejak OTP (backend: umur sesi analis maks 8 jam). Akses tetap 15 menit. */
        const val SESI_ANALIS_MS = 8L * 60 * 60 * 1000
    }
}
