package com.tool.decluttr.di

import android.app.Application
import androidx.room.Room
import com.tool.decluttr.data.local.DecluttrDatabase
import com.tool.decluttr.data.local.dao.AppDao
import com.tool.decluttr.data.repository.AppRepositoryImpl
import com.tool.decluttr.domain.repository.AppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tool.decluttr.data.repository.AuthRepositoryImpl
import com.tool.decluttr.domain.repository.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(auth: FirebaseAuth): AuthRepository {
        return AuthRepositoryImpl(auth)
    }

    @Provides
    @Singleton
    fun provideDecluttrDatabase(app: Application): DecluttrDatabase {
        return Room.databaseBuilder(
            app,
            DecluttrDatabase::class.java,
            DecluttrDatabase.DATABASE_NAME
        ).addMigrations(
            DecluttrDatabase.MIGRATION_2_3,
            DecluttrDatabase.MIGRATION_3_4
        )
            .build()
    }

    @Provides
    @Singleton
    fun provideAppDao(db: DecluttrDatabase): AppDao {
        return db.appDao
    }

    @Provides
    @Singleton
    fun provideAppRepository(
        dao: AppDao,
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): AppRepository {
        return AppRepositoryImpl(dao, auth, firestore)
    }
}
