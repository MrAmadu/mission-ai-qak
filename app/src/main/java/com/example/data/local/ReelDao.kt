package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Query("SELECT * FROM generated_reels ORDER BY createdAt DESC")
    fun observeAllReels(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM generated_reels WHERE id = :id LIMIT 1")
    fun observeReelById(id: String): Flow<ReelEntity?>

    @Query("SELECT * FROM generated_reels WHERE id = :id LIMIT 1")
    suspend fun getReelById(id: String): ReelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReel(reel: ReelEntity)

    @Query("DELETE FROM generated_reels WHERE id = :id")
    suspend fun deleteReelById(id: String)
}
