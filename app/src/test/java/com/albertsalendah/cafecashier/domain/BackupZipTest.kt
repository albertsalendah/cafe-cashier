package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupZipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = BackupManifest(1, "1.0", 1, 1_795_000_000_000L)
    private val dbBytes = ByteArray(200_000) { (it % 251).toByte() }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifestBytes() = formatManifest(manifest).toByteArray()

    // ---- writing ----

    @Test
    fun theZipHoldsTheManifestTheDatabaseAndEveryPhotoInOrder() {
        val db = tmp.newFile("db.bin").apply { writeBytes(dbBytes) }
        val images = tmp.newFolder("images")
        File(images, "b.jpg").writeBytes(byteArrayOf(2))
        File(images, "a.jpg").writeBytes(byteArrayOf(1))
        File(images, "sub").mkdir() // folders are not backed up

        val out = ByteArrayOutputStream()
        writeBackupZip(out, manifest, db, images)

        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        assertEquals(
            listOf("manifest.txt", "cafe_cashier.db", "product_images/a.jpg", "product_images/b.jpg"),
            names
        )
    }

    @Test
    fun aMissingPhotoFolderIsFine() {
        val db = tmp.newFile("db.bin").apply { writeBytes(dbBytes) }
        val out = ByteArrayOutputStream()
        writeBackupZip(out, manifest, db, File(tmp.root, "no-such-folder"))

        val staging = File(tmp.root, "staging")
        val result = extractBackupZip(ByteArrayInputStream(out.toByteArray()), staging)
        assertTrue(result.hasDatabase)
        assertFalse(File(staging, "product_images").exists())
    }

    // ---- round trip ----

    @Test
    fun whatIsWrittenComesBackIdentical() {
        val db = tmp.newFile("db.bin").apply { writeBytes(dbBytes) }
        val images = tmp.newFolder("images")
        val photo = ByteArray(5000) { (it * 7).toByte() }
        File(images, "3f2a.jpg").writeBytes(photo)

        val out = ByteArrayOutputStream()
        writeBackupZip(out, manifest, db, images)

        val staging = File(tmp.root, "staging")
        val result = extractBackupZip(ByteArrayInputStream(out.toByteArray()), staging)

        assertEquals(manifest, result.manifest)
        assertTrue(result.hasDatabase)
        assertArrayEquals(dbBytes, File(staging, "cafe_cashier.db").readBytes())
        assertArrayEquals(photo, File(staging, "product_images/3f2a.jpg").readBytes())
    }

    @Test
    fun extractingFirstEmptiesTheStagingFolder() {
        val staging = tmp.newFolder("staging")
        File(staging, "old.txt").writeText("old")
        val zip = zipOf("manifest.txt" to manifestBytes(), "cafe_cashier.db" to dbBytes)

        extractBackupZip(ByteArrayInputStream(zip), staging)

        assertFalse(File(staging, "old.txt").exists())
        assertTrue(File(staging, "cafe_cashier.db").exists())
    }

    // ---- hostile or odd zips ----

    @Test
    fun zipSlipNamesNeverWriteOutsideTheStagingFolder() {
        val root = tmp.newFolder("root")
        val staging = File(root, "staging")
        val zip = zipOf(
            "../evil.txt" to byteArrayOf(1),
            "product_images/../evil.db" to byteArrayOf(2),
            "/abs.jpg" to byteArrayOf(3),
            "product_images/a/b.jpg" to byteArrayOf(4),
            "product_images/a\\b.jpg" to byteArrayOf(5),
            "cafe_cashier.db-wal" to byteArrayOf(6),
            "manifest.txt" to manifestBytes(),
            "cafe_cashier.db" to dbBytes
        )

        val result = extractBackupZip(ByteArrayInputStream(zip), staging)

        assertEquals(setOf("staging"), root.list()!!.toSet())
        assertEquals(setOf("cafe_cashier.db"), staging.list()!!.toSet())
        assertEquals(manifest, result.manifest)
    }

    @Test
    fun folderEntriesAreIgnored() {
        val staging = File(tmp.root, "staging")
        val zip = zipOf(
            "product_images/" to ByteArray(0),
            "manifest.txt" to manifestBytes(),
            "cafe_cashier.db" to dbBytes
        )
        val result = extractBackupZip(ByteArrayInputStream(zip), staging)
        assertTrue(result.hasDatabase)
        assertFalse(File(staging, "product_images").exists())
    }

    @Test
    fun aZipWithoutAManifestOrWithoutADatabaseIsReportedAsSuch() {
        val noManifest = extractBackupZip(
            ByteArrayInputStream(zipOf("cafe_cashier.db" to dbBytes)), File(tmp.root, "s1")
        )
        assertNull(noManifest.manifest)
        assertTrue(noManifest.hasDatabase)

        val noDatabase = extractBackupZip(
            ByteArrayInputStream(zipOf("manifest.txt" to manifestBytes())), File(tmp.root, "s2")
        )
        assertNotNull(noDatabase.manifest)
        assertFalse(noDatabase.hasDatabase)
    }

    @Test
    fun somethingThatIsNotAZipHasNeitherPart() {
        val result = extractBackupZip(ByteArrayInputStream("hello, I am not a zip".toByteArray()), File(tmp.root, "s"))
        assertNull(result.manifest)
        assertFalse(result.hasDatabase)
    }

    @Test
    fun anOversizedManifestIsNotReadable() {
        val huge = ("format=1\n" + "x".repeat(20_000)).toByteArray()
        val result = extractBackupZip(
            ByteArrayInputStream(zipOf("manifest.txt" to huge, "cafe_cashier.db" to dbBytes)),
            File(tmp.root, "s")
        )
        assertNull(result.manifest)
        assertTrue(result.hasDatabase)
    }

    @Test
    fun moreDataThanAllowedStopsTheExtraction() {
        val zip = zipOf("manifest.txt" to manifestBytes(), "cafe_cashier.db" to dbBytes)
        try {
            extractBackupZip(ByteArrayInputStream(zip), File(tmp.root, "s"), maxTotalBytes = 1_000)
            fail("expected BackupTooLargeException")
        } catch (e: BackupTooLargeException) {
            // expected
        }
    }

    @Test
    fun theLimitCountsPhotosToo() {
        val zip = zipOf(
            "manifest.txt" to manifestBytes(),
            "cafe_cashier.db" to ByteArray(600),
            "product_images/a.jpg" to ByteArray(600)
        )
        try {
            extractBackupZip(ByteArrayInputStream(zip), File(tmp.root, "s"), maxTotalBytes = 1_000)
            fail("expected BackupTooLargeException")
        } catch (e: BackupTooLargeException) {
            // expected
        }
    }
}
