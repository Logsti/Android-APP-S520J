package com.s520j.app.di

import android.content.Context
import com.s520j.app.data.local.ContentDao
import com.s520j.app.data.local.NotificationDao
import com.s520j.app.data.local.OutboxDao
import com.s520j.app.data.local.S520JDatabase
import com.s520j.app.data.local.SpaceDao
import com.s520j.app.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): S520JDatabase = S520JDatabase.create(context)

    @Provides
    fun provideContentDao(db: S520JDatabase): ContentDao = db.contentDao()

    @Provides
    fun provideOutboxDao(db: S520JDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideUserDao(db: S520JDatabase): UserDao = db.userDao()

    @Provides
    fun provideSpaceDao(db: S520JDatabase): SpaceDao = db.spaceDao()

    @Provides
    fun provideNotificationDao(db: S520JDatabase): NotificationDao = db.notificationDao()
}
