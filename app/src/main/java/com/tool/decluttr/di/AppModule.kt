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
import javax.inject.Provider

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.tool.decluttr.data.repository.AuthRepositoryImpl
import com.tool.decluttr.data.repository.BillingRepositoryImpl
import com.tool.decluttr.domain.repository.AuthRepository
import com.tool.decluttr.domain.repository.BillingRepository

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
    fun provideFirebaseFunctions(): FirebaseFunctions {
        // Must match callable function region in functions/index.js (asia-south1).
        return FirebaseFunctions.getInstance("asia-south1")
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        app: Application,
        authProvider: Provider<FirebaseAuth>
    ): AuthRepository {
        return AuthRepositoryImpl(app, authProvider)
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
            DecluttrDatabase.MIGRATION_3_4,
            DecluttrDatabase.MIGRATION_4_5
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
        app: Application,
        dao: AppDao,
        authProvider: Provider<FirebaseAuth>,
        firestoreProvider: Provider<FirebaseFirestore>,
        billingRepository: BillingRepository
    ): AppRepository {
        return AppRepositoryImpl(app, dao, authProvider, firestoreProvider, billingRepository)
    }

    @Provides
    @Singleton
    fun provideBillingRepository(
        app: Application,
        authProvider: Provider<FirebaseAuth>,
        firestoreProvider: Provider<FirebaseFirestore>,
        functionsProvider: Provider<FirebaseFunctions>
    ): BillingRepository {
        return BillingRepositoryImpl(
            app = app,
            authProvider = authProvider,
            firestoreProvider = firestoreProvider,
            functionsProvider = functionsProvider
        )
    }
}
