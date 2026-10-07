package app.cleanupwalk.data

import app.cleanupwalk.domain.CleanupIssue
import app.cleanupwalk.domain.CleanupWalkSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

class CleanupRepository(private val dao: CleanupDao) {

    val issues: Flow<List<CleanupIssue>> = dao.observeIssues().map { list -> list.map { it.toIssue() } }

    val walks: Flow<List<CleanupWalkSession>> = dao.observeWalks().map { list -> list.map { it.toSession() } }

    suspend fun getOpenIssues(): List<CleanupIssue> = dao.getOpenIssues().map { it.toIssue() }

    suspend fun saveIssue(issue: CleanupIssue): Long {
        val id = dao.upsertIssue(IssueEntity.from(issue))
        // Upsert returns -1 when it updated an existing row
        return if (id == -1L) issue.id else id
    }

    suspend fun deleteIssue(issue: CleanupIssue) = dao.deleteIssue(IssueEntity.from(issue))

    suspend fun getActiveWalk(): CleanupWalkSession? = dao.getActiveWalk()?.toSession()

    suspend fun saveWalk(walk: CleanupWalkSession): Long {
        val id = dao.upsertWalk(WalkEntity.from(walk))
        return if (id == -1L) walk.id else id
    }

    /**
     * Resolved issues are only kept for a while so the history stays small
     */
    suspend fun clean() {
        dao.deleteResolvedBefore(Instant.now().minus(Duration.ofDays(90)).toEpochMilli())
    }
}
