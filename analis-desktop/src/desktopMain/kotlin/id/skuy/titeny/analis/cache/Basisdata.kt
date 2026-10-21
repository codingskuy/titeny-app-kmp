package id.skuy.titeny.analis.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.skuy.titeny.analis.db.AnalisDb
import java.nio.file.Files
import java.nio.file.Path

/** Lokasi berkas cache: `%LOCALAPPDATA%/Titeny/analis.db` (produk/22 §2). */
object LokasiDb {
    const val NAMA_BERKAS = "analis.db"

    /**
     * Windows: `<LOCALAPPDATA>/Titeny/analis.db`. Tanpa LOCALAPPDATA (hanya pengembangan di OS lain;
     * target rilis v1.0.0 hanya Windows): `<home>/.local/share/Titeny/analis.db`.
     */
    fun tentukan(
        env: (String) -> String? = System::getenv,
        home: String = System.getProperty("user.home"),
    ): Path {
        val dasar = env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: Path.of(home, ".local", "share")
        return dasar.resolve("Titeny").resolve(NAMA_BERKAS)
    }
}

/** Basis data terbuka: DB SQLDelight + driver (untuk VACUUM) + berkasnya (null = memori, untuk uji). */
class Basisdata(val db: AnalisDb, val driver: SqlDriver, val berkas: Path?) : AutoCloseable {
    override fun close() = driver.close()

    companion object {
        /** Buka (dan buat/migrasikan skema) di [berkas]; `null` = SQLite dalam memori. */
        fun buka(berkas: Path?): Basisdata {
            val driver = if (berkas == null) {
                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            } else {
                Files.createDirectories(berkas.parent)
                JdbcSqliteDriver("jdbc:sqlite:${berkas.toAbsolutePath()}")
            }
            val versi = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { c -> QueryResult.Value(if (c.next().value) c.getLong(0) ?: 0L else 0L) },
                parameters = 0,
            ).value
            val skema = AnalisDb.Schema
            if (versi == 0L) {
                skema.create(driver)
                driver.execute(null, "PRAGMA user_version = ${skema.version}", 0)
            } else if (versi < skema.version) {
                skema.migrate(driver, versi, skema.version)
                driver.execute(null, "PRAGMA user_version = ${skema.version}", 0)
            }
            return Basisdata(AnalisDb(driver), driver, berkas)
        }
    }
}
