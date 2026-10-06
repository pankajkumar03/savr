package com.savr.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: DownloadEntity)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id") suspend fun setProgress(id: String, status: String, progress: Float)
    @Query("UPDATE downloads SET status = :status, progress = :progress, mediaUri = :uri WHERE id = :id") suspend fun finish(id: String, status: String, progress: Float, uri: String)
    @Query("UPDATE downloads SET caption = :caption WHERE id = :id") suspend fun setCaption(id: String, caption: String)
    @Query("UPDATE downloads SET status = 'PAUSED' WHERE status IN ('RUNNING','QUEUED')") suspend fun pauseStale()
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun dao(): DownloadDao }
