package com.albertsalendah.cafecashier.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Reading and writing the backup zip (layout and rules: BackupRules.kt). Plain java.io / java.util.zip,
// no Android classes, so it is unit-tested on the JVM, including hostile zip files.

/** Name of the file that marks a staged restore as confirmed by the user (see BackupManager.confirmRestore). */
const val STAGING_READY_MARKER = "READY"

/** Upper limit for everything unzipped from one backup (1 GiB). Stops a "zip bomb" from filling the phone. */
const val MAX_BACKUP_BYTES = 1L shl 30

private const val MAX_MANIFEST_BYTES = 8L * 1024

/** The zip holds more data than allowed. */
class BackupTooLargeException : IOException("The backup is larger than the allowed size")

/**
 * What [extractBackupZip] found. [manifest] is null if the zip has no readable manifest;
 * [hasDatabase] is false if it has no database entry. Either one means "not a backup".
 */
data class ExtractedBackup(val manifest: BackupManifest?, val hasDatabase: Boolean)

/**
 * Writes the backup zip to [out]: the manifest, the [database] file (already checkpointed, see
 * BackupManager) and every plain file in [imagesDir]. [out] is flushed but left open; the caller closes it.
 */
fun writeBackupZip(out: OutputStream, manifest: BackupManifest, database: File, imagesDir: File) {
    val zip = ZipOutputStream(out)

    fun entry(name: String) = ZipEntry(name).apply { time = manifest.createdAtMillis }

    zip.putNextEntry(entry(BACKUP_MANIFEST_ENTRY))
    zip.write(formatManifest(manifest).toByteArray(Charsets.UTF_8))
    zip.closeEntry()

    zip.putNextEntry(entry(BACKUP_DATABASE_ENTRY))
    database.inputStream().use { it.copyTo(zip) }
    zip.closeEntry()

    imagesDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
        val name = BACKUP_IMAGES_PREFIX + file.name
        if (!isAllowedBackupEntry(name)) return@forEach
        // A photo deleted while the backup runs is simply left out.
        val input = try {
            file.inputStream()
        } catch (e: FileNotFoundException) {
            return@forEach
        }
        input.use {
            zip.putNextEntry(entry(name))
            it.copyTo(zip)
            zip.closeEntry()
        }
    }
    zip.finish()
    zip.flush()
}

/**
 * Unzips a backup into [stagingDir] (emptied first): `cafe_cashier.db` and `product_images/<file>`.
 * Only entries accepted by [isAllowedBackupEntry] are read; everything else (folders, odd names,
 * "../" tricks) is ignored, so nothing can ever be written outside [stagingDir]. The manifest is read
 * into memory, not written. Throws [BackupTooLargeException] past [maxTotalBytes], or an IOException
 * for a damaged zip; the caller then deletes [stagingDir]. [input] is left open.
 */
fun extractBackupZip(input: InputStream, stagingDir: File, maxTotalBytes: Long = MAX_BACKUP_BYTES): ExtractedBackup {
    stagingDir.deleteRecursively()
    stagingDir.mkdirs()
    val imagesDir = File(stagingDir, BACKUP_IMAGES_PREFIX.trimEnd('/'))

    var manifest: BackupManifest? = null
    var hasDatabase = false
    var total = 0L

    val zip = ZipInputStream(input)
    while (true) {
        val entry = zip.nextEntry ?: break
        val name = entry.name
        if (entry.isDirectory || !isAllowedBackupEntry(name)) continue
        when {
            name == BACKUP_MANIFEST_ENTRY -> {
                val buffer = ByteArrayOutputStream()
                manifest = try {
                    copyLimited(zip, buffer, MAX_MANIFEST_BYTES)
                    parseManifest(buffer.toString(Charsets.UTF_8.name()))
                } catch (e: BackupTooLargeException) {
                    null // a manifest this big is not ours
                }
                total += buffer.size()
                if (total > maxTotalBytes) throw BackupTooLargeException()
            }

            name == BACKUP_DATABASE_ENTRY -> {
                File(stagingDir, BACKUP_DATABASE_ENTRY).outputStream().use {
                    total += copyLimited(zip, it, maxTotalBytes - total)
                }
                hasDatabase = true
            }

            else -> { // product_images/<file>, already checked
                imagesDir.mkdirs()
                File(imagesDir, name.substring(BACKUP_IMAGES_PREFIX.length)).outputStream().use {
                    total += copyLimited(zip, it, maxTotalBytes - total)
                }
            }
        }
    }
    return ExtractedBackup(manifest, hasDatabase)
}

/** Copies [from] to [to]; throws [BackupTooLargeException] if more than [limit] bytes come out. Returns the byte count. */
private fun copyLimited(from: InputStream, to: OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = from.read(buffer)
        if (read < 0) return copied
        copied += read
        if (copied > limit) throw BackupTooLargeException()
        to.write(buffer, 0, read)
    }
}
