package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreApplierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var databases: File
    private lateinit var db: File
    private lateinit var images: File
    private lateinit var staging: File

    @Before
    fun setUp() {
        databases = tmp.newFolder("databases")
        db = File(databases, "cafe_cashier.db")
        images = File(tmp.root, "product_images")
        staging = File(tmp.root, "restore_staging")
    }

    private fun liveData() {
        db.writeText("OLD-DB")
        File(databases, "cafe_cashier.db-wal").writeText("OLD-WAL")
        File(databases, "cafe_cashier.db-shm").writeText("OLD-SHM")
        images.mkdirs()
        File(images, "old.jpg").writeText("OLD-PHOTO")
    }

    private fun stagedBackup(confirmed: Boolean = true, withPhotos: Boolean = true) {
        staging.mkdirs()
        File(staging, "cafe_cashier.db").writeText("NEW-DB")
        if (withPhotos) {
            File(staging, "product_images").mkdirs()
            File(staging, "product_images/new.jpg").writeText("NEW-PHOTO")
        }
        if (confirmed) File(staging, STAGING_READY_MARKER).writeText("1")
    }

    private fun run() = applyStagedRestore(staging, db, images)

    private fun assertNothingLeftOver() {
        assertEquals(setOf("cafe_cashier.db"), databases.list()!!.toSet().filter { !it.endsWith("-wal") && !it.endsWith("-shm") }.toSet())
        assertFalse(File(images.path + ".before-restore").exists())
        assertFalse(staging.exists())
    }

    private fun assertOldDataIntact() {
        assertEquals("OLD-DB", db.readText())
        assertEquals("OLD-WAL", File(databases, "cafe_cashier.db-wal").readText())
        assertEquals("OLD-SHM", File(databases, "cafe_cashier.db-shm").readText())
        assertEquals("OLD-PHOTO", File(images, "old.jpg").readText())
    }

    @Test
    fun withNothingStagedNothingHappens() {
        liveData()
        assertEquals(RestoreOutcome.NOTHING_PENDING, run())
        assertOldDataIntact()
    }

    @Test
    fun aStagedBackupThatWasNeverConfirmedIsThrownAwayAndTheDataStays() {
        liveData()
        stagedBackup(confirmed = false)
        assertEquals(RestoreOutcome.NOTHING_PENDING, run())
        assertOldDataIntact()
        assertFalse(staging.exists())
    }

    @Test
    fun aConfirmedBackupReplacesTheDatabaseAndThePhotos() {
        liveData()
        stagedBackup()
        assertEquals(RestoreOutcome.RESTORED, run())

        assertEquals("NEW-DB", db.readText())
        assertEquals("NEW-PHOTO", File(images, "new.jpg").readText())
        assertFalse(File(images, "old.jpg").exists())
        // the old write-ahead log must not stay next to the new database
        assertFalse(File(databases, "cafe_cashier.db-wal").exists())
        assertFalse(File(databases, "cafe_cashier.db-shm").exists())
        assertNothingLeftOver()
    }

    @Test
    fun aBackupWithoutPhotosLeavesAnEmptyPhotoFolder() {
        liveData()
        stagedBackup(withPhotos = false)
        assertEquals(RestoreOutcome.RESTORED, run())
        assertEquals("NEW-DB", db.readText())
        assertTrue(images.isDirectory)
        assertEquals(0, images.list()!!.size)
    }

    @Test
    fun aConfirmedBackupWithoutADatabaseFailsAndKeepsTheOldData() {
        liveData()
        staging.mkdirs()
        File(staging, STAGING_READY_MARKER).writeText("1")
        assertEquals(RestoreOutcome.FAILED, run())
        assertOldDataIntact()
        assertFalse(staging.exists())
    }

    @Test
    fun aRestoreInterruptedBeforeTheMarkerWasClearedIsRolledBackAndDoneAgain() {
        // The state a crash in the middle of step 2 leaves behind: old files set aside,
        // a half-written new database in place, the staging folder still complete and confirmed.
        stagedBackup()
        File(databases, "cafe_cashier.db.before-restore").writeText("OLD-DB")
        File(databases, "cafe_cashier.db-wal.before-restore").writeText("OLD-WAL")
        File(images.path + ".before-restore").mkdirs()
        File(images.path + ".before-restore/old.jpg").writeText("OLD-PHOTO")
        db.writeText("NEW-D") // half copied

        assertEquals(RestoreOutcome.RESTORED, run())

        assertEquals("NEW-DB", db.readText())
        assertEquals("NEW-PHOTO", File(images, "new.jpg").readText())
        assertFalse(File(images, "old.jpg").exists())
        assertFalse(File(databases, "cafe_cashier.db-wal").exists())
        assertNothingLeftOver()
    }

    @Test
    fun anInterruptedRestoreWhoseStagingIsGoneGetsTheOldDataBack() {
        File(databases, "cafe_cashier.db.before-restore").writeText("OLD-DB")
        File(images.path + ".before-restore").mkdirs()
        File(images.path + ".before-restore/old.jpg").writeText("OLD-PHOTO")
        db.writeText("HALF")
        staging.mkdirs()
        File(staging, STAGING_READY_MARKER).writeText("1") // marker but no database inside

        assertEquals(RestoreOutcome.FAILED, run())

        assertEquals("OLD-DB", db.readText())
        assertEquals("OLD-PHOTO", File(images, "old.jpg").readText())
        assertFalse(File(databases, "cafe_cashier.db.before-restore").exists())
    }

    @Test
    fun aRestoreInterruptedAfterThePointOfNoReturnOnlyFinishesTheCleanUp() {
        // New data already in place, marker already cleared, old files not yet deleted.
        db.writeText("NEW-DB")
        images.mkdirs()
        File(images, "new.jpg").writeText("NEW-PHOTO")
        File(databases, "cafe_cashier.db.before-restore").writeText("OLD-DB")
        File(images.path + ".before-restore").mkdirs()
        File(images.path + ".before-restore/old.jpg").writeText("OLD-PHOTO")
        staging.mkdirs()
        File(staging, "cafe_cashier.db").writeText("NEW-DB") // no marker

        assertEquals(RestoreOutcome.NOTHING_PENDING, run())

        assertEquals("NEW-DB", db.readText())
        assertEquals("NEW-PHOTO", File(images, "new.jpg").readText())
        assertNothingLeftOver()
    }

    @Test
    fun runningItTwiceDoesNotRestoreTwice() {
        liveData()
        stagedBackup()
        assertEquals(RestoreOutcome.RESTORED, run())
        db.writeText("SALES-MADE-AFTER-THE-RESTORE")
        assertEquals(RestoreOutcome.NOTHING_PENDING, run())
        assertEquals("SALES-MADE-AFTER-THE-RESTORE", db.readText())
    }

    @Test
    fun aFirstEverRestoreWithNoLiveFilesWorks() {
        stagedBackup()
        assertEquals(RestoreOutcome.RESTORED, run())
        assertEquals("NEW-DB", db.readText())
    }
}
