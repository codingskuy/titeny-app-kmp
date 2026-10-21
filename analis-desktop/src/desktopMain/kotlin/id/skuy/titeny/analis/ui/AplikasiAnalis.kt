package id.skuy.titeny.analis.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.skuy.titeny.analis.antrean.StateAntrean
import id.skuy.titeny.analis.cache.bannerLuring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.nav.Rute

private val tanpaAnimasi: () -> ContentTransform = { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun AplikasiAnalis(navigator: Navigator, layanan: Layanan) {
    KerangkaAnalis(navigator, layanan) {
        NavDisplay(
            backStack = navigator.tumpukan,
            onBack = { navigator.kembali() },
            transitionSpec = { tanpaAnimasi() },
            popTransitionSpec = { tanpaAnimasi() },
            predictivePopTransitionSpec = { tanpaAnimasi() },
            entryProvider = entryProvider {
                entry<Rute.Masuk> { IsiRute(it, navigator, layanan) }
                entry<Rute.AntreanValidasi> { IsiRute(it, navigator, layanan) }
                entry<Rute.DetailFlag> { IsiRute(it, navigator, layanan) }
                entry<Rute.TahanPrediksi> { IsiRute(it, navigator, layanan) }
                entry<Rute.BandingModel> { IsiRute(it, navigator, layanan) }
                entry<Rute.KelolaRekomendasi> { IsiRute(it, navigator, layanan) }
                entry<Rute.Ekspor> { IsiRute(it, navigator, layanan) }
            },
        )
    }
}

/** Bilah navigasi + status sinkron di atas [isi]; dipisah agar uji UI dapat memakai host tanpa NavDisplay. */
@Composable
internal fun KerangkaAnalis(navigator: Navigator, layanan: Layanan, isi: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (navigator.sudahMasuk) {
            BilahNavigasi(navigator, layanan)
            StatusSinkron(layanan)
        }
        isi()
    }
}

/** Layar untuk satu rute (satu-satunya pemetaan rute ke layar). */
@Composable
internal fun IsiRute(rute: Rute, navigator: Navigator, layanan: Layanan) {
    when (rute) {
        Rute.Masuk -> LayarMasuk(navigator, layanan)
        Rute.AntreanValidasi -> LayarAntrean(navigator, layanan)
        is Rute.DetailFlag -> LayarDetailFlag(rute.eventId, navigator, layanan)
        Rute.TahanPrediksi -> Placeholder("Tahan Prediksi", "tiket lanjutan")
        Rute.BandingModel -> Placeholder("Bandingkan Model", "tiket lanjutan")
        Rute.KelolaRekomendasi -> Placeholder("Kelola Rekomendasi", "tiket lanjutan")
        Rute.Ekspor -> LayarEkspor(layanan)
    }
}

@Composable
private fun BilahNavigasi(navigator: Navigator, layanan: Layanan) {
    val scope = rememberCoroutineScope()
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { navigator.buka(Rute.AntreanValidasi) }, modifier = Modifier.testTag("nav-antrean")) { Text("Antrean Validasi") }
        TextButton(onClick = { navigator.buka(Rute.TahanPrediksi) }, modifier = Modifier.testTag("nav-tahan")) { Text("Tahan Prediksi") }
        TextButton(onClick = { navigator.buka(Rute.BandingModel) }, modifier = Modifier.testTag("nav-banding")) { Text("Bandingkan Model") }
        TextButton(onClick = { navigator.buka(Rute.KelolaRekomendasi) }, modifier = Modifier.testTag("nav-rekomendasi")) { Text("Kelola Rekomendasi") }
        TextButton(onClick = { navigator.buka(Rute.Ekspor) }, modifier = Modifier.testTag("nav-ekspor")) { Text("Ekspor (Ctrl+E)") }
        TextButton(onClick = { scope.launch { layanan.api.keluar(); navigator.keluar() } }, modifier = Modifier.testTag("nav-keluar")) { Text("Keluar") }
    }
}

@Composable
private fun Placeholder(judul: String, tiket: String) {
    Column(Modifier.padding(24.dp).testTag("layar-$judul")) {
        Text(judul, style = MaterialTheme.typography.headlineSmall)
        Text("Isi layar dikerjakan di $tiket.")
    }
}

/** Banner luring (stempel data terakhir) dan jumlah aksi menunggu kirim. */
@Composable
private fun StatusSinkron(layanan: Layanan) {
    val state by layanan.antrean.state.collectAsState()
    val menunggu by layanan.aksi.menunggu.collectAsState()
    Column(Modifier.padding(horizontal = 16.dp)) {
        (state as? StateAntrean.Luring)?.let {
            Text(bannerLuring(it.stempelMs), color = TemaTiteny.bannerLuring, modifier = Modifier.testTag("status-luring"))
        }
        if (menunggu > 0) Text("$menunggu aksi menunggu dikirim (terkirim otomatis saat daring).", modifier = Modifier.testTag("status-menunggu"))
    }
}
