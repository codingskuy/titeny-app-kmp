package id.skuy.titeny.analis.kontrak

import au.com.dius.pact.consumer.ConsumerPactBuilder
import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.PactTestRun
import au.com.dius.pact.consumer.PactTestExecutionContext
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.model.MockProviderConfig
import au.com.dius.pact.consumer.runConsumerTest
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.RequestResponsePact
import id.skuy.titeny.analis.api.ApiKlien
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.Keputusan
import id.skuy.titeny.analis.api.LoginHasil
import id.skuy.titeny.analis.api.PenyimpanTokenMemori
import id.skuy.titeny.analis.api.SesiToken
import id.skuy.titeny.analis.api.TahanPrediksi
import id.skuy.titeny.analis.api.ValidasiAnalis
import io.ktor.client.engine.cio.CIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Uji kontrak konsumen (Pact JVM, TTN-KMP-TEST-PACT): klien Ktor `ApiKlien` melawan mock server Pact.
 * Bentuk permintaan/respons diturunkan dari docs/api/openapi.yaml titeny-backend-service
 * (cabang feat/ttn-be-005-aksi-analis-audit). Berkas pact ditulis ke `pacts/` (lihat build.gradle.kts).
 * Verifikasi sisi provider adalah tiket terpisah di repo backend.
 */
class PactKonsumenTest {
    private val konsumen = "titeny-analis-desktop"
    private val provider = "titeny-backend-service"
    private val json = "application/json"

    /** Menutup objek elemen + larik dan kembali ke badan induk (API DSL Pact bertipe nullable). */
    private fun tutupDaftar(elemen: au.com.dius.pact.consumer.dsl.DslPart?): PactDslJsonBody =
        elemen!!.closeObject()!!.closeArray() as PactDslJsonBody

    private fun builder() = ConsumerPactBuilder.consumer(konsumen).hasPactWith(provider)

    private fun jalankan(pact: RequestResponsePact, uji: suspend (ApiKlien) -> Unit) {
        val hasil = runConsumerTest(pact, MockProviderConfig.createDefault(PactSpecVersion.V3), object : PactTestRun<Unit> {
            override fun run(mockServer: MockServer, context: PactTestExecutionContext?) {
                val penyimpan = PenyimpanTokenMemori()
                runBlocking { penyimpan.simpan(SesiToken("token-uji-analis", "refresh-uji", System.currentTimeMillis() + 3_600_000)) }
                val klien = ApiKlien(CIO.create(), mockServer.getUrl(), penyimpan)
                try {
                    runBlocking { uji(klien) }
                } finally {
                    klien.tutup()
                }
            }
        })
        assertTrue(hasil is au.com.dius.pact.consumer.PactVerificationResult.Ok, "Pact gagal: $hasil")
    }

    @Test
    fun loginAnalisMenghasilkanTantanganOtp() {
        val pact = builder()
            .given("akun analis ada dan kata sandi benar")
            .uponReceiving("login analis (langkah 1)")
            .path("/api/v1/auth/login").method("POST")
            .matchHeader("Content-Type", "application/json.*", json)
            .body(PactDslJsonBody().stringType("username", "analis01").stringType("password", "kata-sandi-uji"))
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(PactDslJsonBody().stringType("sesi_id", "ses_abc123").booleanValue("otp_diperlukan", true))
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.login("analis01", "kata-sandi-uji")
            assertIs<Hasil.Berhasil<LoginHasil>>(r)
            assertIs<LoginHasil.Tantangan>(r.data)
        }
    }

    @Test
    fun otpVerifikasiMenerbitkanToken() {
        val pact = builder()
            .given("sesi OTP analis aktif")
            .uponReceiving("verifikasi OTP analis (langkah 2)")
            .path("/api/v1/auth/otp/verifikasi").method("POST")
            .matchHeader("Content-Type", "application/json.*", json)
            .body(PactDslJsonBody().stringType("sesi_id", "ses_abc123").stringType("otp", "123456"))
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(
                PactDslJsonBody()
                    .stringType("access_token", "aaa.bbb.ccc")
                    .stringType("refresh_token", "refresh-baru")
                    .stringValue("token_type", "Bearer")
                    .integerType("expires_in", 900)
                    .stringValue("peran", "analis"),
            )
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.otpVerifikasi("ses_abc123", "123456")
            assertIs<Hasil.Berhasil<*>>(r)
            assertEquals(900, (r as Hasil.Berhasil).data.expiresIn)
        }
    }

    @Test
    fun antreanValidasiMemuatFlagPerluVerifikasi() {
        val pact = builder()
            .given("ada event perlu_verifikasi yang belum diputuskan")
            .uponReceiving("antrean validasi analis")
            .path("/api/v1/analis/antrean").method("GET")
            .matchQuery("limit", "\\d+", "50")
            .matchHeader("Authorization", "Bearer .+", "Bearer token-uji-analis")
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(
                tutupDaftar(
                    PactDslJsonBody()
                        .minArrayLike("data", 1)
                        .stringMatcher("event_id", "^evt_\\d{8}_\\d{6}$", "evt_20261004_000123")
                        .stringType("event_type", "harga_lapak")
                        .stringValue("flag", "perlu_verifikasi")
                        .stringType("alasan_flag", "harga di luar rentang"),
                ).nullValue("next_cursor"),
            )
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.antrean(limit = 50)
            assertIs<Hasil.Berhasil<*>>(r)
            assertEquals("perlu_verifikasi", (r as Hasil.Berhasil).data.data.first().flag)
        }
    }

    @Test
    fun validasiKoreksiNilaiDicatat() {
        val pact = builder()
            .given("event evt_20261004_000123 berstatus perlu_verifikasi")
            .uponReceiving("keputusan koreksi_nilai analis")
            .path("/api/v1/analis/validasi").method("POST")
            .matchHeader("Content-Type", "application/json.*", json)
            .headers("X-Idempotency-Key", "aks_20261004_001")
            .matchHeader("Authorization", "Bearer .+", "Bearer token-uji-analis")
            .body(
                PactDslJsonBody()
                    .stringValue("aksi_id", "aks_20261004_001")
                    .stringValue("event_id", "evt_20261004_000123")
                    .stringValue("keputusan", "koreksi_nilai")
                    .integerType("nilai_baru", 41000)
                    .stringType("alasan", "Salah input lapak 042, nota terlampir."),
            )
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(PactDslJsonBody().stringValue("status", "diterima"))
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.validasi(
                ValidasiAnalis("aks_20261004_001", "evt_20261004_000123", Keputusan.KoreksiNilai, 41000, "Salah input lapak 042, nota terlampir."),
            )
            assertIs<Hasil.Berhasil<*>>(r)
            assertEquals("diterima", (r as Hasil.Berhasil).data.status)
        }
    }

    @Test
    fun validasiAlasanTerlaluPendekDitolak422() {
        val pact = builder()
            .given("event evt_20261004_000123 berstatus perlu_verifikasi")
            .uponReceiving("keputusan dengan alasan di bawah 10 karakter")
            .path("/api/v1/analis/validasi").method("POST")
            .matchHeader("Content-Type", "application/json.*", json)
            .headers("X-Idempotency-Key", "aks_20261004_009")
            .matchHeader("Authorization", "Bearer .+", "Bearer token-uji-analis")
            .body(
                PactDslJsonBody()
                    .stringValue("aksi_id", "aks_20261004_009")
                    .stringValue("event_id", "evt_20261004_000123")
                    .stringValue("keputusan", "tolak")
                    .stringValue("alasan", "pendek"),
            )
            .willRespondWith().status(422).headers(mapOf("Content-Type" to json))
            .body(
                PactDslJsonBody().`object`("error")
                    .stringValue("kode", "validasi_gagal").stringType("pesan", "alasan harus 10-140 karakter.")
                    .stringValue("field", "alasan").closeObject()!!,
            )
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.validasi(ValidasiAnalis("aks_20261004_009", "evt_20261004_000123", Keputusan.Tolak, null, "pendek"))
            assertIs<Hasil.Galat>(r)
            assertEquals(422, r.status)
            assertEquals("validasi_gagal", r.kode)
            assertEquals("alasan", r.field)
        }
    }

    @Test
    fun tahanPrediksiDicatat() {
        val pact = builder()
            .given("pasangan komoditas-pasar punya prediksi harga")
            .uponReceiving("tahan prediksi komoditas-pasar")
            .path("/api/v1/analis/tahan").method("POST")
            .matchHeader("Content-Type", "application/json.*", json)
            .headers("X-Idempotency-Key", "aks_20261004_002")
            .matchHeader("Authorization", "Bearer .+", "Bearer token-uji-analis")
            .body(
                PactDslJsonBody()
                    .stringValue("aksi_id", "aks_20261004_002")
                    .stringValue("komoditas_id", "cmd_cabai_merah")
                    .stringValue("pasar_id", "psr_jakarta_timur_01")
                    .stringType("alasan", "Sampel hanya 2 lapak.")
                    .stringMatcher("berlaku_sampai", "^\\d{4}-\\d{2}-\\d{2}$", "2026-10-07"),
            )
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(PactDslJsonBody().stringMatcher("status", "diterima|duplikat_diabaikan", "diterima"))
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.tahan(TahanPrediksi("aks_20261004_002", "cmd_cabai_merah", "psr_jakarta_timur_01", "Sampel hanya 2 lapak.", "2026-10-07"))
            assertIs<Hasil.Berhasil<*>>(r)
        }
    }

    @Test
    fun hargaMengembalikanDeretPrediksi() {
        val pact = builder()
            .given("pasangan komoditas-pasar punya prediksi harga")
            .uponReceiving("baca prediksi harga")
            .path("/api/v1/harga").method("GET")
            .query("komoditas_id=cmd_cabai_merah&pasar_id=psr_jakarta_timur_01")
            .matchHeader("Authorization", "Bearer .+", "Bearer token-uji-analis")
            .willRespondWith().status(200).headers(mapOf("Content-Type" to json))
            .body(
                tutupDaftar(
                    PactDslJsonBody()
                        .stringValue("komoditas_id", "cmd_cabai_merah")
                        .stringValue("pasar_id", "psr_jakarta_timur_01")
                        .minArrayLike("deret", 1)
                        .stringMatcher("tanggal", "^\\d{4}-\\d{2}-\\d{2}$", "2026-10-05")
                        .stringType("horizon", "H+1")
                        .integerType("harga_prediksi", 42500)
                        .integerType("rentang_bawah", 39000)
                        .integerType("rentang_atas", 46000)
                        .stringType("kepercayaan", "sedang"),
                )
                    .stringType("model_version", "harga_v1.0.0")
                    .stringType("dihitung_pada", "2026-10-04T23:00:00Z"),
            )
            .toPact()
        jalankan(pact) { klien ->
            val r = klien.harga("cmd_cabai_merah", "psr_jakarta_timur_01")
            assertIs<Hasil.Berhasil<*>>(r)
            assertEquals(42500L, (r as Hasil.Berhasil).data.deret.first().hargaPrediksi)
        }
    }
}
