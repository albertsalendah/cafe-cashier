package com.albertsalendah.cafecashier.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Version 1 -> 2: staff accounts. Adds the app_user table, three Settings columns (the switch and
// the admin's recovery code) and six columns on sale (who created, paid and cancelled or voided it).
// Existing rows are kept: the new columns start empty (accounts off, nobody recorded).
//
// The SQL is a plain list so it can be checked without Android. It must match the entities exactly;
// the exported schema app/schemas/.../2.json is the proof.
val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `app_user` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `pinSalt` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",

    "ALTER TABLE `settings` ADD COLUMN `accountsEnabled` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `settings` ADD COLUMN `recoveryHash` TEXT",
    "ALTER TABLE `settings` ADD COLUMN `recoverySalt` TEXT",

    "ALTER TABLE `sale` ADD COLUMN `createdById` INTEGER",
    "ALTER TABLE `sale` ADD COLUMN `createdByName` TEXT",
    "ALTER TABLE `sale` ADD COLUMN `paidById` INTEGER",
    "ALTER TABLE `sale` ADD COLUMN `paidByName` TEXT",
    "ALTER TABLE `sale` ADD COLUMN `cancelledById` INTEGER",
    "ALTER TABLE `sale` ADD COLUMN `cancelledByName` TEXT"
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
    }
}
