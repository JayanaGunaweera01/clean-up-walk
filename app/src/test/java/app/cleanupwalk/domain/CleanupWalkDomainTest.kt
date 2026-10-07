package app.cleanupwalk.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

internal class CleanupWalkDomainTest {

    private val start = GeoPoint(5.9549, 80.5550)
    private val t0 = Instant.parse("2026-10-07T10:00:00Z")

    @Test
    fun geoDistanceAndDestinationAreConsistent() {
        val destination = CleanupGeo.destination(start, 45.0, 1000.0)
        assertEquals(1000.0, CleanupGeo.distance(start, destination), 0.5)
        assertEquals(45.0, CleanupGeo.bearing(start, destination), 0.1)
    }

    @Test
    fun distanceTrackerAccumulatesWalking() {
        val tracker = WalkDistanceTracker()
        var time = t0
        var location = start
        tracker.update(location, 5f, time)
        repeat(10) {
            time = time.plusSeconds(10)
            location = CleanupGeo.destination(location, 90.0, 12.0)
            tracker.update(location, 5f, time)
        }
        assertEquals(120.0, tracker.distanceMeters, 0.5)
    }

    @Test
    fun distanceTrackerIgnoresJitterInaccuracyAndJumps() {
        val tracker = WalkDistanceTracker()
        tracker.update(start, 5f, t0)
        // Jitter while standing still
        tracker.update(CleanupGeo.destination(start, 10.0, 2.0), 5f, t0.plusSeconds(5))
        // Inaccurate fix far away
        tracker.update(CleanupGeo.destination(start, 0.0, 300.0), 80f, t0.plusSeconds(10))
        // Jump of 500 m in 10 seconds
        val jumped = CleanupGeo.destination(start, 0.0, 500.0)
        tracker.update(jumped, 5f, t0.plusSeconds(20))
        assertEquals(0.0, tracker.distanceMeters, 0.001)
        // Walking continues from the new anchor
        tracker.update(CleanupGeo.destination(jumped, 0.0, 10.0), 5f, t0.plusSeconds(30))
        assertEquals(10.0, tracker.distanceMeters, 0.5)
    }



    @Test
    fun progressPassesExplorationStopsAndStopsAtIssues() {
        val issueLocation = CleanupGeo.destination(start, 0.0, 100.0)
        val issue = CleanupIssue(1, CleanupIssueType.OverflowingBin, issueLocation, t0)
        val route = CleanupRoute(
            start,
            listOf(
                CleanupStop(CleanupGeo.destination(start, 90.0, 100.0)),
                CleanupStop(issueLocation, issue)
            ),
            400.0,
            Duration.ofMinutes(6)
        )
        val progress = CleanupWalkProgress(route)

        assertNull(progress.update(start))
        assertEquals(0, progress.visitedCount)

        assertNull(progress.update(CleanupGeo.destination(start, 90.0, 95.0)))
        assertEquals(1, progress.visitedCount)

        assertEquals(issue, progress.update(CleanupGeo.destination(start, 0.0, 90.0))?.issue)
        // Arriving doesn't complete an issue stop, the walker does
        assertEquals(1, progress.visitedCount)
        progress.completeNext()
        assertTrue(progress.isComplete)
        assertEquals(180.0, progress.bearingToNext(issueLocation), 0.1)
    }

    @Test
    fun totalsOnlyCountFinishedWalks() {
        val sessions = listOf(
            CleanupWalkSession(1, t0, t0.plusSeconds(1800), Duration.ofMinutes(30), 1800.0, 14, 2, 1),
            CleanupWalkSession(2, t0, t0.plusSeconds(600), Duration.ofMinutes(15), 500.0, 3, 0, 2),
            CleanupWalkSession(3, t0, null, Duration.ofMinutes(30), 100.0, 1, 0, 0)
        )
        val totals = CleanupWalkTotals.from(sessions)
        assertEquals(2, totals.walks)
        assertEquals(2300.0, totals.distanceMeters, 0.001)
        assertEquals(17, totals.litterRemoved)
        assertEquals(2, totals.issuesReported)
        assertEquals(3, totals.issuesResolved)
        assertEquals(Duration.ofMinutes(40), totals.time)
    }

    @Test
    fun sessionRemainingTime() {
        val session = CleanupWalkSession(1, t0, null, Duration.ofMinutes(30))
        assertEquals(Duration.ofMinutes(20), session.remaining(t0.plusSeconds(600)))
        assertEquals(Duration.ZERO, session.remaining(t0.plusSeconds(3600)))
    }
}
