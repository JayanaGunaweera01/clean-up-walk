package app.cleanupwalk.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

internal class CleanupRoutePlannerTest {

    private val start = GeoPoint(5.9549, 80.5550)
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private val planner = CleanupRoutePlanner()

    @Test
    fun noIssuesCreatesExplorationLoopThatFillsTheTime() {
        val route = planner.plan(start, Duration.ofMinutes(30), emptyList(), now)

        assertTrue(route.stops.isNotEmpty())
        assertTrue(route.stops.all { it.isExploration })
        assertTrue(route.duration <= Duration.ofMinutes(30))
        assertTrue("Used ${route.duration}", route.duration >= Duration.ofMinutes(20))
        // 30 minutes at 1.25 m/s is 2.25 km of walking
        assertTrue("Distance ${route.distanceMeters}", route.distanceMeters in 1500.0..2250.0)
    }

    @Test
    fun zeroDurationIsAnEmptyRoute() {
        val route = planner.plan(start, Duration.ZERO, listOf(issue(1, 0.0, 100.0)), now)
        assertTrue(route.stops.isEmpty())
        assertEquals(0.0, route.distanceMeters, 0.0)
    }

    @Test
    fun visitsNearbyIssuesAndReturnsToStart() {
        val issues = listOf(
            issue(1, 0.0, 200.0),
            issue(2, 90.0, 250.0),
            issue(3, 180.0, 150.0)
        )
        val route = planner.plan(start, Duration.ofMinutes(30), issues, now)
        assertEquals(setOf(1L, 2L, 3L), route.issueStops.map { it.issue!!.id }.toSet())
        assertTrue(route.duration <= Duration.ofMinutes(30))
    }

    @Test
    fun ignoresResolvedAndUnreachableIssues() {
        val issues = listOf(
            issue(1, 0.0, 200.0).copy(resolvedOn = now),
            issue(2, 90.0, 20_000.0),
            issue(3, 180.0, 150.0)
        )
        val route = planner.plan(start, Duration.ofMinutes(30), issues, now)
        assertEquals(listOf(3L), route.issueStops.map { it.issue!!.id })
    }

    @Test
    fun prefersHigherPriorityWhenTimeIsShort() {
        // Two equally far issues in opposite directions; only one fits in 10 minutes
        val issues = listOf(
            issue(1, 0.0, 220.0, CleanupIssueType.Litter),
            issue(2, 180.0, 220.0, CleanupIssueType.DamagedInfrastructure)
        )
        val route = planner.plan(start, Duration.ofMinutes(10), issues, now)
        assertEquals(listOf(2L), route.issueStops.map { it.issue!!.id })
    }

    @Test
    fun olderReportsScoreLower() {
        val fresh = issue(1, 0.0, 100.0)
        val old = fresh.copy(reportedOn = now.minus(Duration.ofDays(28)))
        assertTrue(planner.score(fresh, now) > planner.score(old, now))
        // Never fully forgotten
        assertTrue(planner.score(old.copy(reportedOn = now.minus(Duration.ofDays(3650))), now) > 0.0)
    }

    @Test
    fun moreLitterScoresHigher() {
        val one = issue(1, 0.0, 100.0)
        val many = one.copy(count = 12)
        assertTrue(planner.score(many, now) > planner.score(one, now))
    }

    @Test
    fun neverExceedsTheTimeBudget() {
        val random = Random(42)
        repeat(30) {
            val issues = (1..random.nextInt(0, 40)).map { id ->
                issue(
                    id.toLong(),
                    random.nextDouble(0.0, 360.0),
                    random.nextDouble(10.0, 3000.0),
                    CleanupIssueType.entries[random.nextInt(CleanupIssueType.entries.size)]
                )
            }
            val minutes = random.nextLong(5, 90)
            val route = planner.plan(start, Duration.ofMinutes(minutes), issues, now, random.nextDouble(0.0, 360.0))
            assertTrue("${route.duration} > $minutes min", route.duration <= Duration.ofMinutes(minutes))
            assertEquals(route.stops.size, route.stops.distinct().size)
            assertFalse(route.issueStops.any { it.issue!!.isResolved })
        }
    }

    private fun issue(
        id: Long,
        bearing: Double,
        distance: Double,
        type: CleanupIssueType = CleanupIssueType.Litter
    ): CleanupIssue {
        return CleanupIssue(id, type, CleanupGeo.destination(start, bearing, distance), now)
    }
}
