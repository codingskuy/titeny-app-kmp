import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "id.skuy.titeny.analis.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "id.skuy.titeny.analis"
            packageVersion = "1.0.0"
            windows {
                console = false
                dirChooser = true
            }
        }
    }
}
