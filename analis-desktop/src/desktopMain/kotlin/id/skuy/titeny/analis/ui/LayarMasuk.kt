package id.skuy.titeny.analis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.skuy.titeny.analis.api.Hasil
import id.skuy.titeny.analis.api.LoginHasil
import id.skuy.titeny.analis.nav.Navigator
import id.skuy.titeny.analis.validasi.pesanGalat
import kotlinx.coroutines.launch

/** Masuk dua langkah untuk analis: kata sandi lalu OTP 6 digit (POST /auth/login, /auth/otp/verifikasi). */
@Composable
fun LayarMasuk(navigator: Navigator, layanan: Layanan) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var sesiId by rememberSaveable { mutableStateOf<String?>(null) }
    var otp by rememberSaveable { mutableStateOf("") }
    var pesan by rememberSaveable { mutableStateOf<String?>(null) }
    var sibuk by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.padding(24.dp).testTag("layar-masuk"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Masuk analis", style = MaterialTheme.typography.headlineSmall)
        if (sesiId == null) {
            OutlinedTextField(username, { username = it }, label = { Text("Nama pengguna") }, singleLine = true, modifier = Modifier.testTag("masuk-username"))
            OutlinedTextField(
                password, { password = it }, label = { Text("Kata sandi") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.testTag("masuk-password"),
            )
            Button(
                enabled = !sibuk && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.testTag("masuk-kirim"),
                onClick = {
                    sibuk = true; pesan = null
                    scope.launch {
                        when (val r = layanan.api.login(username.trim(), password)) {
                            is Hasil.Berhasil -> when (val h = r.data) {
                                is LoginHasil.Tantangan -> sesiId = h.tantangan.sesiId
                                is LoginHasil.Langsung -> navigator.masukBerhasil()
                            }
                            is Hasil.Galat -> pesan = if (r.status == 401) "Nama pengguna atau kata sandi salah." else pesanGalat(r)
                            is Hasil.Jaringan -> pesan = "Tidak dapat terhubung ke server."
                        }
                        password = ""
                        sibuk = false
                    }
                },
            ) { Text("Masuk") }
        } else {
            Text("Masukkan kode OTP 6 digit yang dikirim ke Anda.")
            OutlinedTextField(
                otp, { v -> otp = v.filter(Char::isDigit).take(6) }, label = { Text("Kode OTP") }, singleLine = true,
                modifier = Modifier.testTag("masuk-otp"),
            )
            Button(
                enabled = !sibuk && otp.length == 6,
                modifier = Modifier.testTag("masuk-otp-kirim"),
                onClick = {
                    sibuk = true; pesan = null
                    scope.launch {
                        when (val r = layanan.api.otpVerifikasi(sesiId!!, otp)) {
                            is Hasil.Berhasil -> navigator.masukBerhasil()
                            is Hasil.Galat -> pesan = if (r.status == 401) "OTP salah atau kedaluwarsa." else pesanGalat(r)
                            is Hasil.Jaringan -> pesan = "Tidak dapat terhubung ke server."
                        }
                        otp = ""
                        sibuk = false
                    }
                },
            ) { Text("Verifikasi OTP") }
        }
        pesan?.let { Text(it, color = TemaTiteny.statusTolak, modifier = Modifier.testTag("masuk-pesan")) }
    }
}
