package com.s520j.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {

    @Query("SELECT * FROM contents WHERE module = :module AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeByModule(module: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeRecycleBin(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE id = :id")
    suspend fun findById(id: String): ContentEntity?

    @Query("SELECT * FROM contents")
    suspend fun listAll(): List<ContentEntity>

    @Query("SELECT * FROM contents WHERE module = :module AND deletedAt IS NULL")
    suspend fun listByModule(module: String): List<ContentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ContentEntity>)

    @Update
    suspend fun update(entity: ContentEntity)

    @Query("DELETE FROM contents WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM contents WHERE module = :module AND deletedAt IS NULL")
    suspend fun clearModule(module: String)

    @Query("SELECT COUNT(*) FROM contents")
    suspend fun count(): Int
}

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox ORDER BY seq ASC")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun enqueue(entity: OutboxEntity)

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun remove(seq: Long)
}

@Dao
interface UserDao {

    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun currentUser(): UserEntity?

    @Query("SELECT * FROM users WHERE accountId = :accountId LIMIT 1")
    suspend fun findByAccountId(accountId: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE accountId = :accountId")
    suspend fun countByAccountId(accountId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clear()
}

@Dao
interface SpaceDao {

    @Query("SELECT * FROM spaces LIMIT 1")
    fun observeSpace(): Flow<SpaceEntity?>

    @Query("SELECT * FROM spaces LIMIT 1")
    suspend fun currentSpace(): SpaceEntity?

    @Query("SELECT * FROM spaces WHERE bindCode = :code LIMIT 1")
    suspend fun findByBindCode(code: String): SpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(entity: SpaceMemberEntity)

    @Query("SELECT * FROM space_members WHERE spaceId = :spaceId")
    suspend fun members(spaceId: String): List<SpaceMemberEntity>

    @Query("DELETE FROM spaces")
    suspend fun clear()
}

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NotificationEntity)

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllRead()
}
