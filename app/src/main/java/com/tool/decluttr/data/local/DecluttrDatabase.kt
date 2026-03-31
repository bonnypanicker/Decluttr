package com.tool.decluttr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.local.entity.AppEntity

@Database(entities = [AppEntity::class], version = 4, exportSchema = true)
abstract class DecluttrDatabase : RoomDatabase() {
    abstract val appDao: AppDao
    
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
    }
}
