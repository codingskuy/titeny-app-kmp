import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.sqldelight.runtime)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.pact.consumer)
                implementation(libs.ktor.client.cio)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.navigation3.ui)
                implementation(libs.lifecycle.viewmodel.savedstate)
                implementation(libs.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.serialization.json)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.jna.platform)
            }
        }
    }
}

sqldelight {
    databases {
        create("AnalisDb") {
            packageName.set("id.skuy.titeny.analis.db")
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

// Uji konsumen Pact menulis berkas kontrak ke <root>/pacts (TTN-KMP-TEST-PACT).
// Pact menggabungkan interaksi antartes ke satu berkas; hapus dulu agar tak ada interaksi basi.
val direktoriPact = rootProject.layout.projectDirectory.dir("pacts")
tasks.withType<Test>().configureEach {
    systemProperty("pact.rootDir", direktoriPact.asFile.absolutePath)
    doFirst { direktoriPact.asFile.listFiles()?.filter { it.extension == "json" }?.forEach { it.delete() } }
}
