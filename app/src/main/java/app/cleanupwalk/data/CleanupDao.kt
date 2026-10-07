package app.cleanupwalk.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupDao {

    @Query("SELECT * FROM issues ORDER BY reported_on DESC")
    fun observeIssues(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE resolved_on IS NULL")
    suspend fun getOpenIssues(): List<IssueEntity>

    @Upsert
    suspend fun upsertIssue(issue: IssueEntity): Long

    @Delete
    suspend fun deleteIssue(issue: IssueEntity)

    @Query("DELETE FROM issues WHERE resolved_on IS NOT NULL AND resolved_on < :minEpochMillis")
    suspend fun deleteResolvedBefore(minEpochMillis: Long)

    @Query("SELECT * FROM walks ORDER BY start_time DESC")
    fun observeWalks(): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walks WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveWalk(): WalkEntity?

    @Upsert
    suspend fun upsertWalk(walk: WalkEntity): Long
}
