package id.skuy.titeny.analis.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import id.skuy.titeny.analis.api.ApiKlien
import id.skuy.titeny.analis.api.PenyimpanTokenMemori
import id.skuy.titeny.analis.cache.Basisdata
import id.skuy.titeny.analis.nav.Navigator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Uji alur UI Compose Desktop (TTN-KMP-TEST-MAESTRO, fallback eksekusi): smoke masuk dan alur validasi flag,
 * dengan selector `testTag` yang sama dengan berkas YAML di `.maestro`. Maestro tidak mendukung Compose Desktop
 * (lihat ADR-0005), jadi alur yang sama dijalankan di sini dengan `runComposeUiTest` melawan ApiKlien nyata
 * dan MockEngine (tanpa jaringan). NavDisplay Navigation3 butuh host jendela, jadi uji memakai
 * `KerangkaAnalis` + `IsiRute` (pemetaan rute ke layar yang sama dengan aplikasi).
 */
@OptIn(ExperimentalTestApi::class)
class AlurUiTest {
    private val permintaan = mutableListOf<HttpRequestData>()
    private val diputuskan = mutableSetOf<String>()
    private val SEMUA_FLAG = linkedMapOf(
        "evt_20261004_000123" to """{"event_id":"evt_20261004_000123","event_type":"harga_lapak","flag":"perlu_verifikasi","alasan_flag":"harga di luar rentang"}""",
        "evt_20261004_000124" to """{"event_id":"evt_20261004_000124","event_type":"stok_berubah","flag":"perlu_verifikasi","alasan_flag":"selisih stok 2%"}""",
    )

    private fun json(isi: String, status: HttpStatusCode = HttpStatusCode.OK) =
        Headers.build { append(HttpHeaders.ContentType, "application/json") }.let { h -> Triple(isi, status, h) }

    private fun layanan(): Layanan {
        val engine = MockEngine { req ->
            permintaan += req
            val (isi, status, h) = when (req.url.encodedPath) {
                "/api/v1/auth/login" -> json("""{"sesi_id":"ses_1","otp_diperlukan":true}""")
                "/api/v1/auth/otp/verifikasi" ->
                    json("""{"access_token":"A1","refresh_token":"R1","token_type":"Bearer","expires_in":900,"peran":"analis"}""")
                "/api/v1/analis/antrean" -> json(
                    // Backend meniru: flag yang sudah diputuskan keluar dari antrean.
                    SEMUA_FLAG.filterKeys { it !in diputuskan }.values.joinToString(prefix = """{"data":[""", postfix = """],"next_cursor":null}"""),
                )
                "/api/v1/analis/validasi" -> {
                    diputuskan += SEMUA_FLAG.keys.first { id -> id in (req.body as TextContent).text }
                    json("""{"status":"diterima"}""")
                }
                else -> json("""{"error":{"kode":"tidak_ditemukan","pesan":"x","field":null}}""", HttpStatusCode.NotFound)
            }
            respond(isi, status, h)
        }
        val api = ApiKlien(engine, "http://api.test", PenyimpanTokenMemori())
        return Layanan(api, Basisdata.buka(null))
    }

    @Test
    fun smokeLayarMasukTampilDanTombolMenungguIsian() = runComposeUiTest {
        val navigator = Navigator()
        val layanan = layanan()
        setContent { MaterialTheme { KerangkaAnalis(navigator, layanan) { IsiRute(navigator.rutePuncak, navigator, layanan) } } }
        onNodeWithTag("layar-masuk").assertExists()
        onNodeWithTag("masuk-kirim").assertIsNotEnabled()
        onNodeWithTag("masuk-username").performTextInput("analis01")
        onNodeWithTag("masuk-password").performTextInput("kata-sandi-uji")
        onNodeWithTag("masuk-kirim").assertIsEnabled()
    }

    @Test
    fun alurMasukOtpAntreanDetailValidasiTolak() = runComposeUiTest {
        val navigator = Navigator()
        val layanan = layanan()
        setContent { MaterialTheme { KerangkaAnalis(navigator, layanan) { IsiRute(navigator.rutePuncak, navigator, layanan) } } }

        onNodeWithTag("masuk-username").performTextInput("analis01")
        onNodeWithTag("masuk-password").performTextInput("kata-sandi-uji")
        onNodeWithTag("masuk-kirim").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesTag("masuk-otp").isNotEmpty() }
        onNodeWithTag("masuk-otp").performTextInput("123456")
        onNodeWithTag("masuk-otp-kirim").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesTag("antrean-baris-evt_20261004_000123").isNotEmpty() }
        onNodeWithTag("antrean-baris-evt_20261004_000124").assertExists()
        onNodeWithTag("antrean-baris-evt_20261004_000123").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesTag("layar-detail-flag").isNotEmpty() }
        onNodeWithTag("detail-kirim").assertIsNotEnabled()
        onNodeWithTag("keputusan-Tolak").performClick()
        onNodeWithTag("detail-alasan").performTextInput("pendek")
        onNodeWithTag("detail-kirim").assertIsNotEnabled() // alasan < 10 karakter
        onNodeWithTag("detail-alasan").performTextInput(" alasan cukup panjang")
        onNodeWithTag("detail-kirim").assertIsEnabled()
        onNodeWithTag("detail-kirim").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesTag("layar-antrean").isNotEmpty() }
        val kirim = permintaan.single { it.url.encodedPath == "/api/v1/analis/validasi" }
        val aksiId = kirim.headers["X-Idempotency-Key"]
        assertTrue(aksiId != null && aksiId.startsWith("aks_"), "X-Idempotency-Key = aksi_id: $aksiId")
        val badan = (kirim.body as TextContent).text
        assertTrue("\"keputusan\":\"tolak\"" in badan && "\"aksi_id\":\"$aksiId\"" in badan, badan)
        assertEquals("Bearer A1", kirim.headers[HttpHeaders.Authorization])
        onNodeWithTag("antrean-baris-evt_20261004_000123").assertDoesNotExist() // dihapus lokal setelah terkirim
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesTag(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes()
}
