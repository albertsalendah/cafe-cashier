package com.albertsalendah.cafecashier.data

import android.content.Context
import android.content.SharedPreferences
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.sqlite.db.SupportSQLiteDatabase
import com.albertsalendah.cafecashier.data.db.AppDatabase
import com.albertsalendah.cafecashier.domain.BACKUP_DATABASE_ENTRY
import com.albertsalendah.cafecashier.domain.BACKUP_FORMAT_VERSION
import com.albertsalendah.cafecashier.domain.BackupCheck
import com.albertsalendah.cafecashier.domain.BackupManifest
import com.albertsalendah.cafecashier.domain.BackupTooLargeException
import com.albertsalendah.cafecashier.domain.STAGING_READY_MARKER
import com.albertsalendah.cafecashier.domain.checkBackup
import com.albertsalendah.cafecashier.domain.extractBackupZip
import com.albertsalendah.cafecashier.domain.writeBackupZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

/** File name of the Room database inside the app's databases folder. */
const val DATABASE_FILE_NAME = "cafe_cashier.db"

/** Result of [BackupManager.stageRestore]. Nothing live has been touched in any case. */
sealed interface RestoreStage {
    /** The file is a good backup, unzipped and checked. Ask the user, then call confirmRestore(). */
    data class Ready(val manifest: BackupManifest) : RestoreStage

    /** Not a backup of this app, or the database inside is damaged. */
    data object NotABackup : RestoreStage

    /** Made by a newer version of the app. */
    data object MadeByNewerApp : RestoreStage

    /** The file could not be read or unzipped (damaged zip, too big, phone out of space...). */
    data object Failed : RestoreStage
}

// Makes and checks backups (the zip layout: domain/BackupRules.kt and BackupZip.kt).
//
// Backup: the file goes wherever the user picked in the system "save as" screen, so no storage
// permission is needed. Restore is two steps: stageRestore() unzips and checks a picked file in a
// staging folder, and only after the user confirms, confirmRestore() marks it; the swap itself
// happens at the next app start, before the database is opened (see RestoreApplier).
class BackupManager(
    context: Context,
    private val database: AppDatabase,
    private val imagesDir: File
) {
    private val context = context.applicationContext

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When the last backup was made on this phone (epoch ms), or null if never. */
    fun lastBackupMillis(): Long? = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0L }

    /** Writes a backup zip to [target] (a Uri from the "save as" screen). True if it worked. */
    suspend fun createBackup(target: Uri): Boolean = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, SNAPSHOT_NAME)
        var wroteSomething = false
        try {
            val now = System.currentTimeMillis()
            val schemaVersion = snapshotDatabase(snapshot)
            val manifest = BackupManifest(BACKUP_FORMAT_VERSION, appVersionName(), schemaVersion, now)
            val out = context.contentResolver.openOutputStream(target, "w") ?: return@withContext false
            wroteSomething = true
            BufferedOutputStream(out).use { writeBackupZip(it, manifest, snapshot, imagesDir) }
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
            true
        } catch (e: Exception) {
            // Do not leave a half-written file behind that looks like a backup.
            if (wroteSomething) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, target)
                } catch (ignored: Exception) {
                }
            }
            false
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Unzips the picked file into the staging folder and checks it (manifest, database integrity,
     * schema version). Never touches the live data.
     */
    suspend fun stageRestore(source: Uri): RestoreStage = withContext(Dispatchers.IO) {
        val staging = stagingDir(context)
        try {
            val extracted = context.contentResolver.openInputStream(source)?.use {
                extractBackupZip(BufferedInputStream(it), staging)
            }
            if (extracted == null) {
                discardStaged()
                return@withContext RestoreStage.Failed
            }
            val manifest = extracted.manifest
            if (manifest == null || !extracted.hasDatabase) {
                discardStaged()
                return@withContext RestoreStage.NotABackup
            }
            val databaseVersion = inspectStagedDatabase(File(staging, BACKUP_DATABASE_ENTRY))
            if (databaseVersion == null) {
                discardStaged()
                return@withContext RestoreStage.NotABackup
            }
            val current = database.openHelper.readableDatabase.version
            when (checkBackup(manifest, databaseVersion, current)) {
                BackupCheck.OK -> RestoreStage.Ready(manifest)
                BackupCheck.NOT_A_BACKUP -> {
                    discardStaged()
                    RestoreStage.NotABackup
                }

                BackupCheck.MADE_BY_NEWER_APP -> {
                    discardStaged()
                    RestoreStage.MadeByNewerApp
                }
            }
        } catch (e: BackupTooLargeException) {
            discardStaged()
            RestoreStage.Failed
        } catch (e: Exception) {
            discardStaged()
            RestoreStage.Failed
        }
    }

    /** The user said yes: mark the staged backup so the next app start swaps it in. */
    fun confirmRestore(): Boolean = try {
        File(stagingDir(context), STAGING_READY_MARKER).writeText("1")
        true
    } catch (e: IOException) {
        false
    }

    /** Throws away a staged backup (the user said no, or the check failed). */
    fun discardStaged() {
        stagingDir(context).deleteRecursively()
    }

    // A copy of the database file that is complete in itself: the write-ahead log is folded back
    // into the main file first, then the main file is copied while holding the write lock, so no
    // sale can slip in and change it half way. The copy is quick; the slower zip and save-as step
    // runs afterwards from the copy, with the lock already released. Returns the schema version.
    private fun snapshotDatabase(snapshot: File): Int {
        val db = database.openHelper.writableDatabase
        val source = context.getDatabasePath(DATABASE_FILE_NAME)
        val wal = File(source.path + "-wal")
        repeat(SNAPSHOT_ATTEMPTS) {
            if (checkpoint(db)) {
                db.beginTransaction()
                try {
                    // Empty log = the main file is everything. A sale saved in between leaves
                    // something in the log; then try again instead of backing up a stale file.
                    if (!wal.exists() || wal.length() == 0L) {
                        source.copyTo(snapshot, overwrite = true)
                        return db.version
                    }
                } finally {
                    db.endTransaction() // never marked successful: nothing is written
                }
            }
            Thread.sleep(RETRY_DELAY_MS)
        }
        throw IOException("Could not get a consistent copy of the database")
    }

    // First column of the result is 0 when the log was fully folded in, 1 if a reader blocked it.
    private fun checkpoint(db: SupportSQLiteDatabase): Boolean =
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() && it.getInt(0) == 0 }

    // Opens the staged copy (our own private file, never the live database) and returns its schema
    // version, or null if it is damaged or is not this app's database.
    private fun inspectStagedDatabase(file: File): Int? {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: SQLiteException) {
            return null
        }
        try {
            if (DatabaseUtils.stringForQuery(db, "PRAGMA integrity_check", null) != "ok") return null
            for (table in IDENTITY_TABLES) {
                val found = DatabaseUtils.longForQuery(
                    db, "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)
                )
                if (found != 1L) return null
            }
            return db.version
        } catch (e: SQLiteException) {
            return null
        } finally {
            db.close()
        }
    }

    private fun appVersionName(): String {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName ?: "unknown"
    }

    companion object {
        private const val PREFS_NAME = "backup"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val SNAPSHOT_NAME = "backup_snapshot.db"
        private const val SNAPSHOT_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 300L

        // A backup must contain these tables to count as ours. If a future migration ever renames
        // one, change this list in the same step.
        private val IDENTITY_TABLES = listOf("settings", "sale", "product")

        /** Where a picked backup is unzipped. In filesDir (not cache), so Android never clears it between steps. */
        fun stagingDir(context: Context): File = File(context.filesDir, "restore_staging")
    }
}
