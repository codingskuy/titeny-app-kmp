package id.skuy.titeny.analis.api

/**
 * Token sesi analis. `sesiBerakhirPadaMs` = waktu OTP + 8 jam (batas sesi analis);
 * akses JWT sendiri 15 menit dan refresh 7 hari (standar KONTEKS/AGENTS, bukan "8 jam").
 */
@kotlinx.serialization.Serializable
data class SesiToken(
    val accessToken: String,
    val refreshToken: String,
    val sesiBerakhirPadaMs: Long,
)

/**
 * Penyimpan token. Implementasi produksi (Windows Credential Manager) hadir di TTN-KMP-004;
 * dilarang menyimpan token di berkas teks (AGENTS.md).
 */
interface PenyimpanToken {
    suspend fun baca(): SesiToken?
    suspend fun simpan(token: SesiToken)
    suspend fun hapus()
}

/** Hanya memori proses; hilang saat aplikasi ditutup. Dipakai sebelum TTN-KMP-004 dan di uji. */
class PenyimpanTokenMemori : PenyimpanToken {
    @Volatile
    private var tersimpan: SesiToken? = null

    override suspend fun baca() = tersimpan
    override suspend fun simpan(token: SesiToken) { tersimpan = token }
    override suspend fun hapus() { tersimpan = null }
}
