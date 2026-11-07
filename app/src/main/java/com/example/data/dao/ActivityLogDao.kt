package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: String): ActivityLog?

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): ActivityLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog)

    @Update
    suspend fun updateLog(log: ActivityLog)

    @Delete
    suspend fun deleteLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()

    @Query("UPDATE activity_logs SET uploadStatus = :status, serverMessage = :message WHERE id = :id")
    suspend fun updateUploadStatus(id: String, status: String, message: String?)
}
