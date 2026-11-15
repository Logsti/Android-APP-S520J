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
        MediaEntity::class,
        SyncConflictEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class S520JDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun outboxDao(): OutboxDao
    abstract fun userDao(): UserDao
    abstract fun spaceDao(): SpaceDao
    abstract fun notificationDao(): NotificationDao
    abstract fun syncConflictDao(): SyncConflictDao

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

        /** v2 -> v3：新增同步冲突快照表（8B.2），保留 30 天。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_conflicts` (" +
                        "`id` TEXT NOT NULL, `entityId` TEXT NOT NULL, `serverJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `expireAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** v3 -> v4：冲突快照补充本机版本，支持用户选择「保留本机」。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sync_conflicts` ADD COLUMN `localJson` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v4 -> v5：保存云端绑定申请 id，支持跨设备确认绑定。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `spaces` ADD COLUMN `bindRequestId` TEXT DEFAULT NULL"
                )
            }
        }

        /** v5 -> v6：图片改为「本机路径 + 云端引用」双字段，跨设备才能拿到图片。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `contents` ADD COLUMN `mediaRefs` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun create(context: Context): S520JDatabase =
            Room.databaseBuilder(context, S520JDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
    }
}
