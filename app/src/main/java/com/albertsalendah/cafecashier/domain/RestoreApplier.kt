package com.albertsalendah.cafecashier.domain

import java.io.File
import java.io.IOException

// Swaps a confirmed, staged backup in for the live data. Runs at app start, before the database is
// opened (see CafeCashierApp), so nothing holds the old files open. Plain java.io, unit-tested on the JVM.
//
// Order of work, chosen so that a crash or a full phone at any moment never loses data:
//   1. the live database files (+ its -wal, -shm, -journal) and the photo folder are renamed aside
//      (name + ".before-restore"), not deleted
//   2. the staged database and photos are COPIED into place (the staging folder stays complete)
//   3. the READY marker is deleted: this is the point of no return
//   4. the renamed-aside files and the staging folder are deleted
// Failure before step 3 puts the old files back. If the app dies before step 3 the leftovers are seen
// at the next start (aside files + READY marker) and the old files are put back, then the restore
// is tried again. If it dies after step 3 (aside files, no marker) only the clean-up is finished.

enum class RestoreOutcome {
    /** No restore was waiting. */
    NOTHING_PENDING,

    /** The backup is now the live data. */
    RESTORED,

    /** It could not be done; the data from before is back in place and the staged backup is deleted. */
    FAILED
}

private const val ASIDE = ".before-restore"
private val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

fun applyStagedRestore(stagingDir: File, databaseFile: File, imagesDir: File): RestoreOutcome {
    val marker = File(stagingDir, STAGING_READY_MARKER)
    val databaseAsides = DATABASE_SUFFIXES.map { File(databaseFile.path + it + ASIDE) }
    val imagesAside = File(imagesDir.path + ASIDE)

    // Left over from an earlier start that did not finish?
    if (databaseAsides.any { it.exists() } || imagesAside.exists()) {
        if (!marker.exists()) {
            // Past the point of no return: the restore went through, only clean-up is left.
            databaseAsides.forEach { it.delete() }
            imagesAside.deleteRecursively()
            stagingDir.deleteRecursively()
            return RestoreOutcome.NOTHING_PENDING
        }
        putOldFilesBack(databaseFile, imagesDir, databaseAsides, imagesAside)
    }

    if (!stagingDir.exists()) return RestoreOutcome.NOTHING_PENDING
    if (!marker.exists()) {
        // Staged but the user never confirmed (they left the screen, or declined).
        stagingDir.deleteRecursively()
        return RestoreOutcome.NOTHING_PENDING
    }
    val stagedDatabase = File(stagingDir, BACKUP_DATABASE_ENTRY)
    if (!stagedDatabase.isFile) {
        stagingDir.deleteRecursively()
        return RestoreOutcome.FAILED
    }

    try {
        // 1. set the live files aside
        DATABASE_SUFFIXES.forEachIndexed { i, suffix ->
            val live = File(databaseFile.path + suffix)
            if (live.exists() && !live.renameTo(databaseAsides[i])) throw IOException("Cannot move ${live.name}")
        }
        if (imagesDir.exists() && !imagesDir.renameTo(imagesAside)) throw IOException("Cannot move photos")

        // 2. copy the staged files in
        databaseFile.parentFile?.mkdirs()
        stagedDatabase.copyTo(databaseFile, overwrite = true)
        val stagedImages = File(stagingDir, BACKUP_IMAGES_PREFIX.trimEnd('/'))
        if (stagedImages.isDirectory) {
            if (!stagedImages.copyRecursively(imagesDir, overwrite = true)) throw IOException("Cannot copy photos")
        } else {
            imagesDir.mkdirs() // the backup had no photos
        }

        // 3. point of no return
        if (!marker.delete()) throw IOException("Cannot clear the restore marker")
    } catch (e: Exception) {
        putOldFilesBack(databaseFile, imagesDir, databaseAsides, imagesAside)
        stagingDir.deleteRecursively()
        return RestoreOutcome.FAILED
    }

    // 4. clean up
    databaseAsides.forEach { it.delete() }
    imagesAside.deleteRecursively()
    stagingDir.deleteRecursively()
    return RestoreOutcome.RESTORED
}

// Throws away whatever is at the live names (a half-copied new file) and renames the asides back.
private fun putOldFilesBack(databaseFile: File, imagesDir: File, databaseAsides: List<File>, imagesAside: File) {
    DATABASE_SUFFIXES.forEachIndexed { i, suffix ->
        val aside = databaseAsides[i]
        if (aside.exists()) {
            File(databaseFile.path + suffix).delete()
            aside.renameTo(File(databaseFile.path + suffix))
        }
    }
    if (imagesAside.exists()) {
        imagesDir.deleteRecursively()
        imagesAside.renameTo(imagesDir)
    }
}
