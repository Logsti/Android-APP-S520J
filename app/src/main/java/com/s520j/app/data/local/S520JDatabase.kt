package com.s520j.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        /** v1 -> v2：共同空间增加绑定申请/解绑申请与双方确认时间字段，保留 v0.2.x 的测试数据。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spaces ADD COLUMN pendingAction TEXT")
                db.execSQL("ALTER TABLE spaces ADD COLUMN bindRequestedBy TEXT")
                db.execSQL("ALTER TABLE spaces ADD COLUMN unbindRequestedBy TEXT")
                db.execSQL("ALTER TABLE spaces ADD COLUMN ownerConfirmedAt INTEGER")
                db.execSQL("ALTER TABLE spaces ADD COLUMN partnerConfirmedAt INTEGER")
            }
        }

        fun create(context: Context): S520JDatabase =
            Room.databaseBuilder(context, S520JDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
