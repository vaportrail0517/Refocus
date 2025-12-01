package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.refocus.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE packageName = :packageName")
    suspend fun findByPackageName(packageName: String): List<SessionEntity>

    @Query("SELECT * FROM sessions")
    suspend fun findAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions")
    fun observeAllSessions(): Flow<List<SessionEntity>>
}
