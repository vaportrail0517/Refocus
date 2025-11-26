package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.refocus.data.db.entity.SuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {

    @Query("SELECT * FROM suggestions ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<SuggestionEntity>>

    @Query("SELECT * FROM suggestions ORDER BY createdAtMillis ASC")
    suspend fun getAll(): List<SuggestionEntity>

    @Insert
    suspend fun insert(entity: SuggestionEntity): Long

    @Update
    suspend fun update(entity: SuggestionEntity)

    @Delete
    suspend fun delete(entity: SuggestionEntity)

    @Query("DELETE FROM suggestions")
    suspend fun deleteAll()
}
