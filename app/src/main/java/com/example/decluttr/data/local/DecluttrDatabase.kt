package com.example.decluttr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.decluttr.data.local.dao.AppDao
import com.example.decluttr.data.local.entity.AppEntity

@Database(entities = [AppEntity::class], version = 3, exportSchema = false)
abstract class DecluttrDatabase : RoomDatabase() {
    abstract val appDao: AppDao
    
    companion object {
        const val DATABASE_NAME = "decluttr_db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE archived_apps ADD COLUMN folderName TEXT")
            }
        }
    }
}
