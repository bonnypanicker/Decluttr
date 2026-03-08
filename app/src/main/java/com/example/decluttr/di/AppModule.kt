package com.example.decluttr.di

import android.app.Application
import androidx.room.Room
import com.example.decluttr.data.local.DecluttrDatabase
import com.example.decluttr.data.local.dao.AppDao
import com.example.decluttr.data.repository.AppRepositoryImpl
import com.example.decluttr.domain.repository.AppRepository
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
        ).fallbackToDestructiveMigration().build()
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
