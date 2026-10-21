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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.nav.Rute

private val tanpaAnimasi: () -> ContentTransform = { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun AplikasiAnalis(navigator: Navigator) {
    Column(Modifier.fillMaxSize()) {
        if (navigator.sudahMasuk) BilahNavigasi(navigator)
        NavDisplay(
            backStack = navigator.tumpukan,
            onBack = { navigator.kembali() },
            transitionSpec = { tanpaAnimasi() },
            popTransitionSpec = { tanpaAnimasi() },
            predictivePopTransitionSpec = { tanpaAnimasi() },
            entryProvider = entryProvider {
                entry<Rute.Masuk> { LayarMasuk(navigator) }
                entry<Rute.AntreanValidasi> { Placeholder("Antrean Validasi", "TTN-KMP-003") }
                entry<Rute.DetailFlag> { Placeholder("Detail Flag ${it.eventId}", "TTN-KMP-003") }
                entry<Rute.TahanPrediksi> { Placeholder("Tahan Prediksi", "tiket lanjutan") }
                entry<Rute.BandingModel> { Placeholder("Bandingkan Model", "tiket lanjutan") }
                entry<Rute.KelolaRekomendasi> { Placeholder("Kelola Rekomendasi", "tiket lanjutan") }
                entry<Rute.Ekspor> { Placeholder("Ekspor", "TTN-KMP-005") }
            },
        )
    }
}

@Composable
private fun BilahNavigasi(navigator: Navigator) {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { navigator.buka(Rute.AntreanValidasi) }, modifier = Modifier.testTag("nav-antrean")) { Text("Antrean Validasi") }
        TextButton(onClick = { navigator.buka(Rute.TahanPrediksi) }, modifier = Modifier.testTag("nav-tahan")) { Text("Tahan Prediksi") }
        TextButton(onClick = { navigator.buka(Rute.BandingModel) }, modifier = Modifier.testTag("nav-banding")) { Text("Bandingkan Model") }
        TextButton(onClick = { navigator.buka(Rute.KelolaRekomendasi) }, modifier = Modifier.testTag("nav-rekomendasi")) { Text("Kelola Rekomendasi") }
        TextButton(onClick = { navigator.buka(Rute.Ekspor) }, modifier = Modifier.testTag("nav-ekspor")) { Text("Ekspor (Ctrl+E)") }
        TextButton(onClick = { navigator.keluar() }, modifier = Modifier.testTag("nav-keluar")) { Text("Keluar") }
    }
}

@Composable
private fun LayarMasuk(navigator: Navigator) {
    Column(Modifier.padding(24.dp).testTag("layar-masuk"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masuk analis", style = MaterialTheme.typography.headlineSmall)
        // TODO(TTN-KMP-003): form username/password + OTP memakai POST /auth/login dan /auth/otp/verifikasi.
        Text("Autentikasi dihubungkan di TTN-KMP-003; tombol ini sementara melewati login.")
        Button(onClick = { navigator.masukBerhasil() }, modifier = Modifier.testTag("tombol-masuk")) { Text("Masuk (sementara)") }
    }
}

@Composable
private fun Placeholder(judul: String, tiket: String) {
    Column(Modifier.padding(24.dp).testTag("layar-$judul")) {
        Text(judul, style = MaterialTheme.typography.headlineSmall)
        Text("Isi layar dikerjakan di $tiket.")
    }
}
