package com.archive.decluttr.di

import android.app.Application
import androidx.room.Room
import com.archive.decluttr.data.local.DecluttrDatabase
import com.archive.decluttr.data.local.dao.AppDao
import com.archive.decluttr.data.repository.AppRepositoryImpl
import com.archive.decluttr.domain.repository.AppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDecluttrDatabase(app: Application): DecluttrDatabase {
        return Room.databaseBuilder(
            app,
            DecluttrDatabase::class.java,
            DecluttrDatabase.DATABASE_NAME
        ).addMigrations(DecluttrDatabase.MIGRATION_2_3)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    @Singleton
    fun provideAppDao(db: DecluttrDatabase): AppDao {
        return db.appDao
    }

    @Provides
    @Singleton
    fun provideAppRepository(dao: AppDao): AppRepository {
        return AppRepositoryImpl(dao)
    }
}
