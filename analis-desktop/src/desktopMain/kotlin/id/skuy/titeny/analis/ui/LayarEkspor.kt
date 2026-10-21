package id.skuy.titeny.analis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import id.skuy.titeny.analis.ekspor.FormatEkspor
import id.skuy.titeny.analis.ekspor.HasilEkspor
import id.skuy.titeny.analis.ekspor.JenisEkspor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar Ekspor: CSV/XLSX massal; tiap berkas dicatat (nama + SHA-256) di jejak audit lokal. */
@Composable
fun LayarEkspor(layanan: Layanan) {
    val scope = rememberCoroutineScope()
    var jenis by rememberSaveable { mutableStateOf(JenisEkspor.HargaH7) }
    var format by rememberSaveable { mutableStateOf(FormatEkspor.Xlsx) }
    var sibuk by rememberSaveable { mutableStateOf(false) }
    var kemajuan by rememberSaveable { mutableStateOf("") }
    var hasil by remember { mutableStateOf<HasilEkspor?>(null) }

    Column(Modifier.padding(16.dp).testTag("layar-ekspor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ekspor", style = MaterialTheme.typography.headlineSmall)
        Text("Folder keluaran: ${layanan.folderEkspor}")
        Text("Jenis", style = MaterialTheme.typography.titleMedium)
        JenisEkspor.entries.forEach { j ->
            Row {
                RadioButton(selected = jenis == j, enabled = j.tersedia && !sibuk, onClick = { jenis = j }, modifier = Modifier.testTag("ekspor-jenis-${j.kode}"))
                Text(j.label + (j.catatan?.let { " — $it" } ?: ""), Modifier.padding(top = 12.dp))
            }
        }
        Text("Format", style = MaterialTheme.typography.titleMedium)
        FormatEkspor.entries.forEach { f ->
            Row {
                RadioButton(selected = format == f, enabled = !sibuk, onClick = { format = f }, modifier = Modifier.testTag("ekspor-format-${f.ekstensi}"))
                Text(f.ekstensi.uppercase(), Modifier.padding(top = 12.dp))
            }
        }
        Button(
            enabled = !sibuk,
            modifier = Modifier.testTag("ekspor-mulai"),
            onClick = {
                sibuk = true; kemajuan = "Mengambil data..."; hasil = null
                scope.launch {
                    val katalog = layanan.katalog()
                    hasil = withContext(Dispatchers.IO) {
                        layanan.eksportir.ekspor(jenis, format, katalog) { s, t -> kemajuan = "Mengambil data $s/$t" }
                    }
                    kemajuan = ""
                    sibuk = false
                }
            },
        ) { Text("Ekspor") }
        if (kemajuan.isNotEmpty()) Text(kemajuan, modifier = Modifier.testTag("ekspor-kemajuan"))
        when (val h = hasil) {
            is HasilEkspor.Berhasil -> SelectionContainer {
                Column(Modifier.testTag("ekspor-hasil")) {
                    Text("Selesai. Manifest hash: ${h.manifest.fileName}")
                    h.berkas.forEach { Text("${it.nama}  (${it.jumlahBaris} baris)  SHA-256 ${it.sha256}") }
                }
            }
            is HasilEkspor.Kosong -> Text(h.alasan, modifier = Modifier.testTag("ekspor-kosong"))
            is HasilEkspor.Gagal -> Text(h.pesan, color = TemaTiteny.statusTolak, modifier = Modifier.testTag("ekspor-galat"))
            null -> Unit
        }
    }
}
