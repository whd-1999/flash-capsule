package com.flashcapsule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules WHERE deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE deletedAt IS NULL AND (text LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%') ORDER BY pinned DESC, createdAt DESC")
    fun search(q: String): Flow<List<CapsuleEntity>>

    /** 回收站：只显示软删的。 */
    @Query("SELECT * FROM capsules WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun trashedBefore(cutoff: Long): List<CapsuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CapsuleEntity)

    @Query("SELECT * FROM capsules WHERE id = :id")
    suspend fun byId(id: String): CapsuleEntity?

    /** 软删：进回收站。音频文件保留（恢复后仍可用）。 */
    @Query("UPDATE capsules SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE capsules SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("UPDATE capsules SET doneAt = :doneAt, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: String, doneAt: Long?, now: Long)

    /** 彻底删：物理 DELETE。 */
    @Query("DELETE FROM capsules WHERE id = :id")
    suspend fun permanentDelete(id: String)

    @Query("DELETE FROM capsules WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeTrashBefore(cutoff: Long)
}
