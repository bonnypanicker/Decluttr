package com.example.decluttr.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.decluttr.data.local.dao.AppDao
import com.example.decluttr.data.local.entity.AppEntity

@Database(entities = [AppEntity::class], version = 1, exportSchema = false)
abstract class DecluttrDatabase : RoomDatabase() {
    abstract val appDao: AppDao
    
    companion object {
        const val DATABASE_NAME = "decluttr_db"
    }
}
