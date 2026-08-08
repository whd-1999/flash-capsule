package com.flashcapsule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE text LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<CapsuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CapsuleEntity)

    @Query("SELECT * FROM capsules WHERE id = :id")
    suspend fun byId(id: String): CapsuleEntity?

    @Query("DELETE FROM capsules WHERE id = :id")
    suspend fun delete(id: String)
}
