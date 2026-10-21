package id.skuy.titeny.analis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.skuy.titeny.analis.antrean.StateAntrean
import id.skuy.titeny.analis.api.AntreanItem
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.nav.Rute
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private val FORMAT_JAM = DateTimeFormatter.ofPattern("dd/MM HH.mm").withZone(ZoneId.of("Asia/Jakarta"))

fun labelFlag(flag: String): String = when (flag) {
    "perlu_verifikasi" -> "Perlu verifikasi"
    else -> flag
}

@Composable
fun LayarAntrean(navigator: Navigator, layanan: Layanan) {
    val state by layanan.antrean.state.collectAsState()
    val scope = rememberCoroutineScope()
    val manual = androidx.compose.runtime.remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    // Polling 5 menit; tombol Segarkan dan Ctrl+R memicu muat ulang segera.
    LaunchedEffect(Unit) { layanan.antrean.polling(merge(manual, navigator.segarkan)) }

    Column(Modifier.padding(16.dp).testTag("layar-antrean"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Antrean Validasi", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { scope.launch { manual.emit(Unit) } }, modifier = Modifier.testTag("antrean-segarkan")) { Text("Segarkan (Ctrl+R)") }
        }
        when (val s = state) {
            StateAntrean.Memuat -> Text("Memuat antrean...")
            is StateAntrean.Siap -> {
                Text("Diperbarui ${FORMAT_JAM.format(Instant.ofEpochMilli(s.diperbaruiMs))} WIB; ${s.item.size} flag.")
                Tabel(s.item) { navigator.buka(Rute.DetailFlag(it.eventId)) }
            }
            is StateAntrean.Gagal -> {
                Text(s.pesan, color = TemaTiteny.statusTolak, modifier = Modifier.testTag("antrean-galat"))
                if (s.item.isNotEmpty()) Tabel(s.item) { navigator.buka(Rute.DetailFlag(it.eventId)) }
            }
        }
    }
}

@Composable
private fun Tabel(item: List<AntreanItem>, bukaDetail: (AntreanItem) -> Unit) {
    if (item.isEmpty()) {
        Text("Tidak ada flag perlu_verifikasi.", modifier = Modifier.testTag("antrean-kosong"))
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("ID event", Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
        Text("Jenis event", Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
        Text("Status", Modifier.weight(1.5f), style = MaterialTheme.typography.labelLarge)
        Text("Alasan flag", Modifier.weight(4f), style = MaterialTheme.typography.labelLarge)
    }
    LazyColumn(Modifier.testTag("antrean-tabel")) {
        itemsIndexed(item, key = { _, it -> it.eventId }) { i, it ->
            Row(
                Modifier.fillMaxWidth()
                    .background(if (i % 2 == 0) TemaTiteny.zebraGenap else TemaTiteny.zebraGanjil)
                    .clickable { bukaDetail(it) }
                    .padding(vertical = 4.dp)
                    .testTag("antrean-baris-${it.eventId}"),
            ) {
                Text(it.eventId, Modifier.weight(2f))
                Text(it.eventType, Modifier.weight(2f))
                // Status berwarna PLUS teks (tidak mengandalkan warna saja).
                Text(labelFlag(it.flag), Modifier.weight(1.5f), color = TemaTiteny.statusMenunggu)
                Text(it.alasanFlag ?: "-", Modifier.weight(4f))
            }
        }
    }
}
