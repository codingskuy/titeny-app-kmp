package id.skuy.titeny.analis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.skuy.titeny.analis.aksi.HasilKirim
import id.skuy.titeny.analis.api.Keputusan
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.validasi.ALASAN_MAKS
import id.skuy.titeny.analis.validasi.ALASAN_MIN
import id.skuy.titeny.analis.validasi.FormValidasi
import id.skuy.titeny.analis.validasi.buatAksiId
import id.skuy.titeny.analis.validasi.pesanGalat
import kotlinx.coroutines.launch

/**
 * Detail flag + keputusan. GAP kontrak: openapi.yaml belum punya GET detail flag (nilai, komoditas,
 * pasar); layar ini hanya menampilkan field antrean (event_id, event_type, flag, alasan_flag).
 */
@Composable
fun LayarDetailFlag(eventId: String, navigator: Navigator, layanan: Layanan) {
    val scope = rememberCoroutineScope()
    val item = layanan.antrean.cari(eventId)
    var keputusan by rememberSaveable(eventId) { mutableStateOf<Keputusan?>(null) }
    var alasan by rememberSaveable(eventId) { mutableStateOf("") }
    var nilaiBaru by rememberSaveable(eventId) { mutableStateOf("") }
    // aksi_id dibuat sekali per form dan dipakai ulang saat kirim ulang (idempoten), bukan per klik.
    val aksiId = rememberSaveable(eventId) { buatAksiId() }
    var pesan by rememberSaveable(eventId) { mutableStateOf<String?>(null) }
    var sibuk by rememberSaveable(eventId) { mutableStateOf(false) }
    val form = FormValidasi(keputusan, alasan, nilaiBaru)

    Column(Modifier.padding(16.dp).testTag("layar-detail-flag"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Detail flag", style = MaterialTheme.typography.headlineSmall)
        Text("ID event: $eventId")
        if (item != null) {
            Text("Jenis event: ${item.eventType}")
            Text("Status: ${labelFlag(item.flag)}", color = TemaTiteny.statusMenunggu)
            Text("Alasan flag: ${item.alasanFlag ?: "-"}")
        } else {
            Text("Flag tidak ada di antrean saat ini (mungkin sudah diputuskan). Segarkan antrean (Ctrl+R).", modifier = Modifier.testTag("detail-tidak-ada"))
        }

        Text("Keputusan", style = MaterialTheme.typography.titleMedium)
        listOf(Keputusan.Setuju to "Setuju", Keputusan.KoreksiNilai to "Koreksi nilai", Keputusan.Tolak to "Tolak").forEach { (k, label) ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(selected = keputusan == k, onClick = { keputusan = k }, modifier = Modifier.testTag("keputusan-$label"))
                Text(label, Modifier.padding(top = 12.dp))
            }
        }
        if (keputusan == Keputusan.KoreksiNilai) {
            OutlinedTextField(
                nilaiBaru, { v -> nilaiBaru = v.filter(Char::isDigit) }, label = { Text("Nilai baru (IDR, bilangan bulat)") },
                singleLine = true, isError = !form.nilaiValid, modifier = Modifier.testTag("detail-nilai"),
            )
        }
        OutlinedTextField(
            alasan, { alasan = it }, label = { Text("Alasan ($ALASAN_MIN-$ALASAN_MAKS karakter)") },
            supportingText = { Text("${form.panjangAlasan}/$ALASAN_MAKS") },
            isError = alasan.isNotEmpty() && !form.alasanValid,
            modifier = Modifier.fillMaxWidth().testTag("detail-alasan"),
        )
        Button(
            enabled = form.bisaKirim && !sibuk,
            modifier = Modifier.testTag("detail-kirim"),
            onClick = {
                val req = form.keRequest(aksiId, eventId) ?: return@Button
                sibuk = true; pesan = null
                scope.launch {
                    when (val r = layanan.aksi.kirimAtauAntre(req)) {
                        is HasilKirim.Terkirim -> {
                            layanan.antrean.hapusLokal(eventId)
                            navigator.kembali()
                        }
                        // Luring: aksi tersimpan di SQLite dengan aksi_id yang sama dan terkirim saat daring.
                        HasilKirim.Diantre -> {
                            layanan.antrean.hapusLokal(eventId)
                            navigator.kembali()
                        }
                        is HasilKirim.Ditolak -> pesan = pesanGalat(r.galat)
                    }
                    sibuk = false
                }
            },
        ) { Text("Kirim keputusan") }
        pesan?.let { Text(it, color = TemaTiteny.statusTolak, modifier = Modifier.testTag("detail-pesan")) }
    }
}
