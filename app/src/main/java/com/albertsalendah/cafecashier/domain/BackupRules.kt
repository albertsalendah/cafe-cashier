package com.albertsalendah.cafecashier.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Pure rules for backup and restore (no Android classes, so they are unit-tested on the JVM).
//
// A backup is one zip file with exactly these entries:
//   manifest.txt              small text file, see BackupManifest
//   cafe_cashier.db           the database, after a WAL checkpoint (no -wal or -shm file)
//   product_images/<file>     product photos and the café logo, one flat folder

const val BACKUP_MANIFEST_ENTRY = "manifest.txt"
const val BACKUP_DATABASE_ENTRY = "cafe_cashier.db"
const val BACKUP_IMAGES_PREFIX = "product_images/"

/** Version of the zip layout itself. Raise it only if the layout above ever changes. */
const val BACKUP_FORMAT_VERSION = 1

/** [schemaVersion] is the Room database version the backup was made with. */
data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val schemaVersion: Int,
    val createdAtMillis: Long
)

/** The manifest as text: one `key=value` per line. */
fun formatManifest(manifest: BackupManifest): String =
    listOf(
        "format=${manifest.formatVersion}",
        "app_version=${manifest.appVersionName.replace('\r', ' ').replace('\n', ' ')}",
        "schema_version=${manifest.schemaVersion}",
        "created_at=${manifest.createdAtMillis}"
    ).joinToString("\n") + "\n"

/**
 * Reads a manifest written by [formatManifest]. Blank lines, CRLF line ends and unknown keys are
 * tolerated; null if a needed field is missing or not a number (then the file is not a backup).
 */
fun parseManifest(text: String): BackupManifest? {
    val values = HashMap<String, String>()
    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        val eq = line.indexOf('=')
        if (eq <= 0) continue
        values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
    }
    val format = values["format"]?.toIntOrNull() ?: return null
    val appVersion = values["app_version"] ?: return null
    val schema = values["schema_version"]?.toIntOrNull() ?: return null
    val createdAt = values["created_at"]?.toLongOrNull() ?: return null
    return BackupManifest(format, appVersion, schema, createdAt)
}

enum class BackupCheck {
    /** Safe to restore. */
    OK,

    /** No readable manifest, or the database inside is not a usable database. */
    NOT_A_BACKUP,

    /** Made by a newer version of the app than this one; restoring could break things. */
    MADE_BY_NEWER_APP
}

/**
 * Decides whether a staged backup may be restored.
 * [databaseVersion] is the `user_version` read from the database inside the zip (Room stores its
 * schema version there; 0 or less means it is not one of ours). It is checked instead of the
 * manifest's own schema number, because the database file is what actually gets restored.
 * An older backup is fine: Room upgrades it with its migrations when the app opens it.
 */
fun checkBackup(manifest: BackupManifest?, databaseVersion: Int, currentSchemaVersion: Int): BackupCheck {
    if (manifest == null) return BackupCheck.NOT_A_BACKUP
    if (manifest.formatVersion > BACKUP_FORMAT_VERSION) return BackupCheck.MADE_BY_NEWER_APP
    if (manifest.formatVersion < 1 || databaseVersion <= 0) return BackupCheck.NOT_A_BACKUP
    if (databaseVersion > currentSchemaVersion) return BackupCheck.MADE_BY_NEWER_APP
    return BackupCheck.OK
}

/**
 * Whether a zip entry name may be read while restoring. Anything else is skipped or refused, which
 * also stops "zip slip" names such as `../x` or `/data/...` from writing outside the staging folder.
 * Photos must be plain files directly in `product_images/`.
 */
fun isAllowedBackupEntry(name: String): Boolean {
    if (name == BACKUP_MANIFEST_ENTRY || name == BACKUP_DATABASE_ENTRY) return true
    if (!name.startsWith(BACKUP_IMAGES_PREFIX)) return false
    val file = name.substring(BACKUP_IMAGES_PREFIX.length)
    if (file.isEmpty() || file == "." || file == "..") return false
    return file.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' }
}

private val fileNameStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

/** Suggested file name for a new backup, in the café's local time: cafe-cashier-backup-2026-11-25-1930.zip */
fun backupFileName(createdAtMillis: Long, zone: ZoneId): String =
    "cafe-cashier-backup-" + fileNameStamp.format(Instant.ofEpochMilli(createdAtMillis).atZone(zone)) + ".zip"
