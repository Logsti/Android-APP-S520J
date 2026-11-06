package com.s520j.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        SpaceEntity::class,
        SpaceMemberEntity::class,
        ContentEntity::class,
        OutboxEntity::class,
        NotificationEntity::class,
        MediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class S520JDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun outboxDao(): OutboxDao
    abstract fun userDao(): UserDao
    abstract fun spaceDao(): SpaceDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val NAME = "s520j.db"

        fun create(context: Context): S520JDatabase =
            Room.databaseBuilder(context, S520JDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
