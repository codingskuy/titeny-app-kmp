package id.skuy.titeny.analis

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.skuy.titeny.analis.api.ApiKlien
import id.skuy.titeny.analis.api.Konfigurasi
import id.skuy.titeny.analis.api.PenyimpanTokenMemori
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.ui.Layanan
import io.ktor.client.engine.cio.CIO
import id.skuy.titeny.analis.nav.petaPintasan
import id.skuy.titeny.analis.ui.AplikasiAnalis
import java.awt.Desktop
import java.awt.Dimension

/** Jendela desktop minimum (FSD/10-alur): 1120x720. */
const val LEBAR_MIN = 1120
const val TINGGI_MIN = 720

fun main(args: Array<String>) = application {
    val navigator = remember { Navigator() }
    val layanan = remember {
        Layanan(ApiKlien(CIO.create(), Konfigurasi.urlApi(), PenyimpanTokenMemori()))
    }

    LaunchedEffect(Unit) {
        // Windows mengirim tautan titeny:// sebagai argumen proses; OS lain lewat open-URI handler.
        args.firstOrNull { it.startsWith("titeny://") }?.let(navigator::bukaDeepLink)
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
                Desktop.getDesktop().setOpenURIHandler { navigator.bukaDeepLink(it.uri.toString()) }
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Titeny Analis Desktop",
        state = rememberWindowState(size = DpSize(LEBAR_MIN.dp, TINGGI_MIN.dp)),
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            val aksi = petaPintasan(event.key, event.isCtrlPressed) ?: return@Window false
            navigator.tanganiPintasan(aksi)
            true
        },
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(LEBAR_MIN, TINGGI_MIN) }
        MaterialTheme { AplikasiAnalis(navigator, layanan) }
    }
}
