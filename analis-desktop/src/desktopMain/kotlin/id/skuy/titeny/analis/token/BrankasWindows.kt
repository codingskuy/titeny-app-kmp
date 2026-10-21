package id.skuy.titeny.analis.token

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import com.sun.jna.win32.W32APITypeMapper

/**
 * Windows Credential Manager lewat JNA (advapi32 CredWriteW/CredReadW/CredDeleteW).
 * jna-platform tidak menyediakan binding Cred*, jadi binding minimal ditulis di sini.
 *
 * HANYA DITULIS, BELUM DIJALANKAN: pengembangan dilakukan di macOS. Wajib diuji di Windows 10/11
 * sebelum rilis (checklist di PR TTN-KMP-004 dan TTN-OPS-005).
 */
class BrankasWindows : BrankasKredensial {
    /** CREDENTIALW (wincred.h). Urutan dan tipe field mengikuti struct asli. */
    @FieldOrder(
        "Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh",
        "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName",
    )
    open class Credential(p: Pointer? = null) : Structure(p, ALIGN_DEFAULT, W32APITypeMapper.UNICODE) {
        @JvmField var Flags: Int = 0
        @JvmField var Type: Int = 0
        @JvmField var TargetName: String? = null
        @JvmField var Comment: String? = null
        @JvmField var LastWrittenLow: Int = 0
        @JvmField var LastWrittenHigh: Int = 0
        @JvmField var CredentialBlobSize: Int = 0
        @JvmField var CredentialBlob: Pointer? = null
        @JvmField var Persist: Int = 0
        @JvmField var AttributeCount: Int = 0
        @JvmField var Attributes: Pointer? = null
        @JvmField var TargetAlias: String? = null
        @JvmField var UserName: String? = null
    }

    @Suppress("FunctionName")
    interface Advapi32Cred : StdCallLibrary {
        // W32APIOptions.UNICODE_OPTIONS menambahkan sufiks "W" pada nama fungsi.
        fun CredWrite(credential: Credential, flags: Int): Boolean
        fun CredRead(targetName: String, type: Int, flags: Int, credential: PointerByReference): Boolean
        fun CredDelete(targetName: String, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer)
    }

    private val api: Advapi32Cred by lazy { Native.load("Advapi32", Advapi32Cred::class.java, W32APIOptions.UNICODE_OPTIONS) }

    override fun tulis(target: String, data: ByteArray) {
        val blob = Memory(maxOf(data.size, 1).toLong()).apply { write(0, data, 0, data.size) }
        val kred = Credential().apply {
            Type = CRED_TYPE_GENERIC
            TargetName = target
            UserName = "titeny-analis"
            CredentialBlobSize = data.size
            CredentialBlob = blob
            Persist = CRED_PERSIST_LOCAL_MACHINE
        }
        kred.write()
        check(api.CredWrite(kred, 0)) { "CredWrite gagal untuk $target (kode ${Native.getLastError()})" }
    }

    override fun baca(target: String): ByteArray? {
        val ref = PointerByReference()
        if (!api.CredRead(target, CRED_TYPE_GENERIC, 0, ref)) {
            val kode = Native.getLastError()
            if (kode == ERROR_NOT_FOUND) return null
            error("CredRead gagal untuk $target (kode $kode)")
        }
        val penunjuk = ref.value
        try {
            val k = Credential(penunjuk).also { it.read() }
            return if (k.CredentialBlobSize == 0) ByteArray(0) else k.CredentialBlob!!.getByteArray(0, k.CredentialBlobSize)
        } finally {
            api.CredFree(penunjuk)
        }
    }

    override fun hapus(target: String) {
        if (!api.CredDelete(target, CRED_TYPE_GENERIC, 0)) {
            val kode = Native.getLastError()
            if (kode != ERROR_NOT_FOUND) error("CredDelete gagal untuk $target (kode $kode)")
        }
    }

    private companion object {
        const val ERROR_NOT_FOUND = 1168
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
    }
}
