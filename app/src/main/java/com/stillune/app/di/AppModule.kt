package com.stillune.app.di

import android.content.Context
import androidx.room.Room
import com.stillune.app.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, "stillune-library.db")
            .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4).build()
    @Provides fun dao(db: LibraryDatabase) = db.tracks()
    @Provides fun analysisDao(db: LibraryDatabase) = db.analysis()
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
}
