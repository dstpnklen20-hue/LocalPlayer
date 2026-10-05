package com.localplayer.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.localplayer.app.data.TransactionRunner
import com.localplayer.app.data.db.AppDatabase
import com.localplayer.app.data.db.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(AppDatabase.Callback)
            .build()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner = object : TransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = db.withTransaction { block() }
    }
}
