package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BackupRulesTest {

    private val manifest = BackupManifest(
        formatVersion = 1,
        appVersionName = "1.0",
        schemaVersion = 1,
        createdAtMillis = 1_795_000_000_000L
    )

    // ---- manifest ----

    @Test
    fun manifestSurvivesAWriteAndRead() {
        assertEquals(manifest, parseManifest(formatManifest(manifest)))
    }

    @Test
    fun manifestReadingToleratesCrlfBlankLinesAndUnknownKeys() {
        val text = "format=1\r\n\r\napp_version=1.0\r\nsomething_new=x\r\nschema_version=1\r\ncreated_at=42\r\n"
        assertEquals(BackupManifest(1, "1.0", 1, 42L), parseManifest(text))
    }

    @Test
    fun manifestWithAMissingFieldIsNotReadable() {
        assertNull(parseManifest("format=1\napp_version=1.0\nschema_version=1\n"))
        assertNull(parseManifest(""))
        assertNull(parseManifest("not a backup at all"))
    }

    @Test
    fun manifestWithANonNumberIsNotReadable() {
        assertNull(parseManifest("format=1\napp_version=1.0\nschema_version=abc\ncreated_at=42\n"))
        assertNull(parseManifest("format=1\napp_version=1.0\nschema_version=1\ncreated_at=yesterday\n"))
    }

    @Test
    fun aNewlineInTheVersionNameCannotBreakTheFile() {
        val written = formatManifest(manifest.copy(appVersionName = "1.0\ncreated_at=1"))
        assertEquals(manifest.createdAtMillis, parseManifest(written)?.createdAtMillis)
    }

    // ---- compatibility ----

    @Test
    fun aBackupFromTheSameSchemaIsAccepted() {
        assertEquals(BackupCheck.OK, checkBackup(manifest, databaseVersion = 1, currentSchemaVersion = 1))
    }

    @Test
    fun anOlderBackupIsAccepted() {
        assertEquals(BackupCheck.OK, checkBackup(manifest, databaseVersion = 1, currentSchemaVersion = 3))
    }

    @Test
    fun aBackupFromANewerSchemaIsRefused() {
        assertEquals(BackupCheck.MADE_BY_NEWER_APP, checkBackup(manifest, databaseVersion = 4, currentSchemaVersion = 3))
    }

    @Test
    fun aBackupWithANewerFormatIsRefused() {
        val newer = manifest.copy(formatVersion = BACKUP_FORMAT_VERSION + 1)
        assertEquals(BackupCheck.MADE_BY_NEWER_APP, checkBackup(newer, databaseVersion = 1, currentSchemaVersion = 1))
    }

    @Test
    fun noManifestOrAnUnusableDatabaseIsNotABackup() {
        assertEquals(BackupCheck.NOT_A_BACKUP, checkBackup(null, databaseVersion = 1, currentSchemaVersion = 1))
        assertEquals(BackupCheck.NOT_A_BACKUP, checkBackup(manifest, databaseVersion = 0, currentSchemaVersion = 1))
        assertEquals(BackupCheck.NOT_A_BACKUP, checkBackup(manifest.copy(formatVersion = 0), databaseVersion = 1, currentSchemaVersion = 1))
    }

    // ---- zip entry names ----

    @Test
    fun theThreeKindsOfEntryWeWriteAreAllowed() {
        assertTrue(isAllowedBackupEntry("manifest.txt"))
        assertTrue(isAllowedBackupEntry("cafe_cashier.db"))
        assertTrue(isAllowedBackupEntry("product_images/3f2a-uuid.jpg"))
    }

    @Test
    fun pathTricksAreRefused() {
        assertFalse(isAllowedBackupEntry("../cafe_cashier.db"))
        assertFalse(isAllowedBackupEntry("product_images/../cafe_cashier.db"))
        assertFalse(isAllowedBackupEntry("product_images/.."))
        assertFalse(isAllowedBackupEntry("product_images/."))
        assertFalse(isAllowedBackupEntry("/data/data/com.albertsalendah.cafecashier/files/x.jpg"))
        assertFalse(isAllowedBackupEntry("product_images/a/b.jpg"))
        assertFalse(isAllowedBackupEntry("product_images/a\\b.jpg"))
        assertFalse(isAllowedBackupEntry("product_images/C:evil.jpg"))
        assertFalse(isAllowedBackupEntry("product_images/a\u0000.jpg"))
    }

    @Test
    fun anythingElseIsRefused() {
        assertFalse(isAllowedBackupEntry("cafe_cashier.db-wal"))
        assertFalse(isAllowedBackupEntry("cafe_cashier.db-shm"))
        assertFalse(isAllowedBackupEntry("product_images/"))
        assertFalse(isAllowedBackupEntry("product_images"))
        assertFalse(isAllowedBackupEntry("other.txt"))
        assertFalse(isAllowedBackupEntry(""))
    }

    // ---- file name ----

    @Test
    fun theFileNameUsesTheLocalTime() {
        val zone = ZoneId.of("Asia/Makassar")
        val millis = ZonedDateTime.of(2026, 11, 25, 19, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("cafe-cashier-backup-2026-11-25-1930.zip", backupFileName(millis, zone))
    }

    @Test
    fun theSameMomentCanBeAnotherDayInAnotherZone() {
        val makassar = ZoneId.of("Asia/Makassar")
        val millis = ZonedDateTime.of(2026, 11, 25, 5, 5, 0, 0, makassar).toInstant().toEpochMilli()
        assertEquals("cafe-cashier-backup-2026-11-25-0505.zip", backupFileName(millis, makassar))
        assertEquals("cafe-cashier-backup-2026-11-24-2105.zip", backupFileName(millis, ZoneId.of("UTC")))
    }
}
