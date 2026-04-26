package com.tool.decluttr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.local.dao.WishlistDao
import com.tool.decluttr.data.local.entity.AppEntity
import com.tool.decluttr.data.local.entity.WishlistEntity

@Database(entities = [AppEntity::class, WishlistEntity::class], version = 6, exportSchema = true)
abstract class DecluttrDatabase : RoomDatabase() {
    abstract val appDao: AppDao
    abstract val wishlistDao: WishlistDao

    companion object {
        const val DATABASE_NAME = "decluttr_db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE archived_apps ADD COLUMN folderName TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE archived_apps ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE archived_apps SET lastModified = archivedAt WHERE lastModified = 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE archived_apps ADD COLUMN archivedSizeBytes INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS wishlist (
                        packageId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        iconUrl TEXT NOT NULL,
                        description TEXT NOT NULL,
                        playStoreUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
