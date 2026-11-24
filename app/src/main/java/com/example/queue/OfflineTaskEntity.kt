package com.example.queue

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object TaskStatus {
    const val PENDING_NETWORK = "PENDING_NETWORK"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

object TaskType {
    const val WEB_SEARCH = "WEB_SEARCH"
    const val WEB_CONNECT = "WEB_CONNECT"
    const val WEBSITE_AUDIT = "WEBSITE_AUDIT"
    const val AI_QUERY = "AI_QUERY"
    const val WEB_BUILD = "WEB_BUILD"
    const val APP_DISCOVERY = "APP_DISCOVERY"
    const val PRICE_FINDER = "PRICE_FINDER"
    const val OFFLINE_ACTION = "OFFLINE_ACTION"
}

object TaskPriority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2

    fun label(priority: Int): String = when (priority) {
        HIGH -> "HIGH"
        LOW -> "LOW"
        else -> "NORMAL"
    }
}

@Entity(tableName = "offline_tasks")
data class OfflineTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val prompt: String,
    val taskType: String,
    val targetValue: String,
    val status: String = TaskStatus.PENDING_NETWORK,
    val priority: Int = TaskPriority.NORMAL,
    val intentName: String = "UNKNOWN",
    val confidence: Float = 0f,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val resultSummary: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
)

@Dao
interface OfflineTaskDao {
    @Query(
        """
        SELECT * FROM offline_tasks
        ORDER BY
            CASE WHEN status IN ('PENDING_NETWORK', 'RUNNING') THEN 0 ELSE 1 END,
            priority DESC,
            createdAtMillis DESC
        """
    )
    fun observeAllTasks(): Flow<List<OfflineTaskEntity>>

    @Query("SELECT * FROM offline_tasks WHERE status = :status ORDER BY priority DESC, createdAtMillis ASC")
    suspend fun getTasksByStatus(status: String): List<OfflineTaskEntity>

    @Query("SELECT * FROM offline_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): OfflineTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: OfflineTaskEntity): Long

    @Update
    suspend fun updateTask(task: OfflineTaskEntity)

    @Query("DELETE FROM offline_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM offline_tasks WHERE status = :completedStatus")
    suspend fun clearTasksByStatus(completedStatus: String = TaskStatus.COMPLETED)
}

@Database(entities = [OfflineTaskEntity::class], version = 2, exportSchema = false)
abstract class JarvisLocalDatabase : RoomDatabase() {
    abstract fun offlineTaskDao(): OfflineTaskDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisLocalDatabase? = null

        fun getInstance(context: Context): JarvisLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisLocalDatabase::class.java,
                    "jarvis_offline_core.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
