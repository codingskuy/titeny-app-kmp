package id.skuy.titeny.analis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Titeny Analis Desktop") {
        MaterialTheme {
            Column(Modifier.padding(16.dp)) {
                Text("Titeny — Analis Desktop (Windows)")
                Text("API: http://localhost:8080/insight/ringkas")
                Text("Sumber: titeny-backend-service, DB titeny.")
            }
        }
    }
}
