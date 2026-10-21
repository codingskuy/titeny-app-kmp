package id.skuy.titeny.analis.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ApiKlienTest {
    private val permintaan = mutableListOf<HttpRequestData>()
    private var kini = 1_000_000L

    private fun MockRequestHandleScope.balas(status: HttpStatusCode, isi: String, extra: Map<String, String> = emptyMap()) =
        respond(isi, status, Headers.build { append(HttpHeaders.ContentType, "application/json"); extra.forEach { (k, v) -> append(k, v) } })

    private fun klien(penyimpan: PenyimpanToken = PenyimpanTokenMemori(), handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        ApiKlien(
            engine = MockEngine { req -> permintaan += req; handler(req) },
            baseUrl = "http://api.test",
            penyimpan = penyimpan,
            jam = { kini },
        )

    private val tokenJson = """{"access_token":"A1","refresh_token":"R1","token_type":"Bearer","expires_in":900,"peran":"analis"}"""
    private fun sesi(akses: String = "A1", refresh: String = "R1", berakhir: Long = kini + 60_000) = SesiToken(akses, refresh, berakhir)

    @Test
    fun loginAnalisMengembalikanTantanganOtp() = runBlocking<Unit> {
        val k = klien { balas(HttpStatusCode.OK, """{"sesi_id":"ses_1","otp_diperlukan":true}""") }
        val h = k.login("rina", "rahasia")
        val tantangan = ((h as Hasil.Berhasil).data as LoginHasil.Tantangan).tantangan
        assertEquals("ses_1", tantangan.sesiId)
        assertEquals("http://api.test/api/v1/auth/login", permintaan.single().url.toString())
        assertNull(PenyimpanTokenMemori().baca())
    }

    @Test
    fun loginPeranLayananLangsungMendapatToken() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori()
        val k = klien(p) { balas(HttpStatusCode.OK, tokenJson) }
        val h = (k.login("svc", "x") as Hasil.Berhasil).data
        assertIs<LoginHasil.Langsung>(h)
        assertEquals("A1", p.baca()?.accessToken)
    }

    @Test
    fun otpVerifikasiMenyimpanTokenDenganBatasSesi8Jam() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori()
        val k = klien(p) { balas(HttpStatusCode.OK, tokenJson) }
        val r = k.otpVerifikasi("ses_1", "123456")
        assertIs<Hasil.Berhasil<TokenPasang>>(r)
        val t = assertNotNull(p.baca())
        assertEquals(kini + ApiKlien.SESI_ANALIS_MS, t.sesiBerakhirPadaMs)
        assertEquals("R1", t.refreshToken)
        val body = (permintaan.single().body as TextContent).text
        assertTrue(""""sesi_id":"ses_1"""" in body && """"otp":"123456"""" in body, body)
    }

    @Test
    fun antreanMengirimBearerDanMemetakanItem() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) {
            balas(HttpStatusCode.OK, """{"data":[{"event_id":"evt_20261004_000123","event_type":"harga.diperbarui","flag":"perlu_verifikasi","alasan_flag":"harga di luar rentang"}],"next_cursor":null,"ekstra":1}""")
        }
        val r = (k.antrean(limit = 50) as Hasil.Berhasil).data
        assertEquals("evt_20261004_000123", r.data.single().eventId)
        assertNull(r.nextCursor)
        val req = permintaan.single()
        assertEquals("Bearer A1", req.headers[HttpHeaders.Authorization])
        assertEquals("50", req.url.parameters["limit"])
    }

    @Test
    fun tanpaSesiAntreanDitolakLokalTanpaMemanggilJaringan() = runBlocking<Unit> {
        val k = klien { balas(HttpStatusCode.OK, "{}") }
        val r = k.antrean()
        assertEquals(401, (r as Hasil.Galat).status)
        assertTrue(permintaan.isEmpty())
    }

    @Test
    fun sesiMelewati8JamMenghapusTokenDanMemintaOtpUlang() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi(berakhir = kini - 1)) }
        val k = klien(p) { balas(HttpStatusCode.OK, "{}") }
        val r = k.antrean() as Hasil.Galat
        assertEquals("sesi_berakhir", r.kode)
        assertNull(p.baca())
        assertTrue(permintaan.isEmpty())
        assertFalse(k.sudahPunyaSesi())
    }

    @Test
    fun akses401DisegarkanSekaliLaluDiulang() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { req ->
            when {
                req.url.encodedPath.endsWith("/auth/refresh") ->
                    balas(HttpStatusCode.OK, """{"access_token":"A2","refresh_token":"R2","token_type":"Bearer","expires_in":900}""")
                req.headers[HttpHeaders.Authorization] == "Bearer A1" ->
                    balas(HttpStatusCode.Unauthorized, """{"error":{"kode":"tidak_berhak","pesan":"Token kedaluwarsa.","field":null}}""")
                else -> balas(HttpStatusCode.OK, """{"data":[],"next_cursor":null}""")
            }
        }
        assertIs<Hasil.Berhasil<AntreanValidasi>>(k.antrean())
        assertEquals(3, permintaan.size)
        val tersimpan = assertNotNull(p.baca())
        assertEquals("A2", tersimpan.accessToken)
        assertEquals("R2", tersimpan.refreshToken)
        // batas sesi 8 jam tidak diperpanjang oleh refresh
        assertEquals(kini + 60_000, tersimpan.sesiBerakhirPadaMs)
        assertTrue("""refresh_token":"R1"""" in (permintaan[1].body as TextContent).text)
    }

    @Test
    fun refreshDitolakMenghapusSesi() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { req ->
            if (req.url.encodedPath.endsWith("/auth/refresh")) balas(HttpStatusCode.Unauthorized, """{"error":{"kode":"tidak_berhak","pesan":"Refresh dipakai ulang.","field":null}}""")
            else balas(HttpStatusCode.Unauthorized, """{"error":{"kode":"tidak_berhak","pesan":"Token kedaluwarsa.","field":null}}""")
        }
        assertEquals(401, (k.antrean() as Hasil.Galat).status)
        assertNull(p.baca())
    }

    @Test
    fun validasiMengirimKunciIdempotenSamaDenganAksiId() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { balas(HttpStatusCode.OK, """{"status":"duplikat_diabaikan"}""") }
        val r = k.validasi(
            ValidasiAnalis("aks_20261004_ab12cd34", "evt_20261004_000123", Keputusan.Setuju, nilaiBaru = null, alasan = "Sesuai nota lapak."),
        )
        assertTrue((r as Hasil.Berhasil).data.duplikat)
        val req = permintaan.single()
        assertEquals("aks_20261004_ab12cd34", req.headers["X-Idempotency-Key"])
        val body = (req.body as TextContent).text
        assertFalse("nilai_baru" in body, "nilai_baru dilarang untuk keputusan selain koreksi_nilai: $body")
        assertTrue(""""keputusan":"setuju"""" in body, body)
    }

    @Test
    fun koreksiNilaiMenyertakanNilaiBaru() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { balas(HttpStatusCode.OK, """{"status":"diterima"}""") }
        k.validasi(ValidasiAnalis("aks_1", "evt_20261004_000123", Keputusan.KoreksiNilai, 41000, "Salah input lapak 042."))
        val body = (permintaan.single().body as TextContent).text
        assertTrue(""""nilai_baru":41000""" in body && """"keputusan":"koreksi_nilai"""" in body, body)
    }

    @Test
    fun galatServerDipetakanDenganKodeFieldDanRetryAfter() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        var status = HttpStatusCode.Conflict
        val k = klien(p) {
            balas(status, """{"error":{"kode":"validasi_gagal","pesan":"Konflik versi; muat ulang data.","field":"event_id"}}""", mapOf("Retry-After" to "30"))
        }
        val aksi = ValidasiAnalis("aks_1", "evt_20261004_000123", Keputusan.Tolak, null, "Data tidak masuk akal.")
        val g409 = k.validasi(aksi) as Hasil.Galat
        assertEquals(409, g409.status)
        assertEquals("validasi_gagal", g409.kode)
        assertEquals("event_id", g409.field)
        status = HttpStatusCode.TooManyRequests
        assertEquals(30, (k.validasi(aksi) as Hasil.Galat).retryAfterDetik)
    }

    @Test
    fun tubuhGalatBukanJsonTetapMenghasilkanGalat() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { respond("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        val g = k.antrean() as Hasil.Galat
        assertEquals(502, g.status)
        assertNull(g.kode)
    }

    @Test
    fun kegagalanJaringanDilaporkanSebagaiJaringan() = runBlocking<Unit> {
        val p = PenyimpanTokenMemori().also { it.simpan(sesi()) }
        val k = klien(p) { throw IOException("putus") }
        assertIs<Hasil.Jaringan>(k.antrean())
    }
}
