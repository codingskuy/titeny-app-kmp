package id.skuy.titeny.analis.nav

import androidx.compose.ui.input.key.Key

/** Pintasan papan ketik terkunci: Ctrl+R segarkan, Ctrl+E ekspor, Esc kembali. */
enum class AksiPintasan { Segarkan, Ekspor, Kembali }

fun petaPintasan(tombol: Key, ctrl: Boolean): AksiPintasan? = when {
    ctrl && tombol == Key.R -> AksiPintasan.Segarkan
    ctrl && tombol == Key.E -> AksiPintasan.Ekspor
    !ctrl && tombol == Key.Escape -> AksiPintasan.Kembali
    else -> null
}
