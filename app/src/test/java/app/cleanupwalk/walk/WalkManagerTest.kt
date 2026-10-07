package app.cleanupwalk.walk

import app.cleanupwalk.data.CleanupDao
import app.cleanupwalk.data.CleanupRepository
import app.cleanupwalk.data.IssueEntity
import app.cleanupwalk.data.WalkEntity
import app.cleanupwalk.domain.CleanupGeo
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WalkManagerTest {

    private val start = GeoPoint(5.9549, 80.5550)

    @Test
    fun fullWalkProducesTheSummaryNumbers() = runBlocking {
        val dao = FakeDao()
        var now = Instant.parse("2026-10-07T10:00:00Z")
        val manager = WalkManager(CleanupRepository(dao)) { now }

        manager.start(start, Duration.ofMinutes(30))
        val route = manager.state.value!!.progress!!.route
        assertTrue(route.stops.isNotEmpty())

        // Walk 120 m east in 10 m steps, 8 seconds apart
        var here = start
        manager.onLocation(here, 5f)
        repeat(12) {
            now = now.plusSeconds(8)
            here = CleanupGeo.destination(here, 90.0, 10.0)
            manager.onLocation(here, 5f)
        }
        assertEquals(120.0, manager.state.value!!.session.distanceMeters, 1.0)

        manager.addLitter()
        manager.addLitter()
        manager.logIssue(CleanupIssueType.Litter, here, 3, 0.8f, cleanedUp = true)
        manager.logIssue(CleanupIssueType.OverflowingBin, here, 1, null, cleanedUp = false)

        val finished = manager.finish()!!
        assertEquals(5, finished.litterRemoved)
        assertEquals(1, finished.issuesReported)
        assertEquals(1, finished.issuesResolved)
        assertEquals(now, finished.endTime)
        assertEquals(120.0, finished.distanceMeters, 1.0)
        assertNull(manager.state.value)

        // Saved: one finished walk, the bin is still open for future routes
        assertEquals(1, dao.walks.size)
        assertFalse(dao.walks.values.first().endTime == null)
        assertEquals(listOf(CleanupIssueType.OverflowingBin.id), dao.getOpenIssues().map { it.type })
    }

    @Test
    fun knownIssuesAreRoutedAndResolvedAtTheStop() = runBlocking {
        val dao = FakeDao()
        val repository = CleanupRepository(dao)
        val manager = WalkManager(repository)
        val binLocation = CleanupGeo.destination(start, 0.0, 150.0)
        manager.logIssue(CleanupIssueType.OverflowingBin, binLocation, 1, null, cleanedUp = false)

        manager.start(start, Duration.ofMinutes(15))
        val progress = manager.state.value!!.progress!!
        assertEquals(1, progress.route.issueStops.size)

        // Visit stops in order until the bin is next
        while (progress.nextStop?.issue == null) {
            manager.skipNextStop()
        }
        manager.onLocation(binLocation, 5f)
        assertEquals(CleanupIssueType.OverflowingBin, manager.state.value!!.arrivedAt?.issue?.type)

        manager.completeNextStop()
        assertTrue(dao.getOpenIssues().isEmpty())
        assertEquals(1, manager.finish()!!.issuesResolved)
    }

    @Test
    fun walkIsRestoredAfterTheAppCloses() = runBlocking {
        val dao = FakeDao()
        WalkManager(CleanupRepository(dao)).apply {
            start(start, Duration.ofMinutes(30))
            addLitter()
        }

        // A new process: the walk comes back from the database
        val restored = WalkManager(CleanupRepository(dao))
        assertTrue(restored.restore(null))
        assertEquals(1, restored.state.value!!.session.litterRemoved)
        assertNull(restored.state.value!!.progress)

        // The route is re-planned once a location is known
        restored.restore(start)
        assertTrue(restored.state.value!!.progress != null)
    }

    @Test
    fun loggingOutsideAWalkStillSavesTheIssue() = runBlocking {
        val dao = FakeDao()
        val manager = WalkManager(CleanupRepository(dao))
        manager.logIssue(CleanupIssueType.FallenBranch, start, 1, 0.9f, cleanedUp = false)
        assertEquals(1, dao.issues.size)
        assertFalse(manager.restore(null))
    }

    private class FakeDao : CleanupDao {
        val issues = linkedMapOf<Long, IssueEntity>()
        val walks = linkedMapOf<Long, WalkEntity>()
        private var nextId = 1L

        override fun observeIssues(): Flow<List<IssueEntity>> = MutableStateFlow(issues.values.toList())
        override suspend fun getOpenIssues() = issues.values.filter { it.resolvedOn == null }
        override suspend fun upsertIssue(issue: IssueEntity): Long {
            if (issue.id != 0L && issues.containsKey(issue.id)) {
                issues[issue.id] = issue
                return -1
            }
            val id = nextId++
            issues[id] = issue.copy(id = id)
            return id
        }

        override suspend fun deleteIssue(issue: IssueEntity) {
            issues.remove(issue.id)
        }

        override suspend fun deleteResolvedBefore(minEpochMillis: Long) {
            issues.values.removeAll { it.resolvedOn != null && it.resolvedOn < minEpochMillis }
        }

        override fun observeWalks(): Flow<List<WalkEntity>> = MutableStateFlow(walks.values.toList())
        override suspend fun getActiveWalk() = walks.values.filter { it.endTime == null }.maxByOrNull { it.startTime }
        override suspend fun upsertWalk(walk: WalkEntity): Long {
            if (walk.id != 0L && walks.containsKey(walk.id)) {
                walks[walk.id] = walk
                return -1
            }
            val id = nextId++
            walks[id] = walk.copy(id = id)
            return id
        }
    }
}
