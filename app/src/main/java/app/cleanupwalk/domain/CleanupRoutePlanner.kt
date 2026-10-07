package app.cleanupwalk.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Plans a time-boxed walking loop that prioritizes known problem spots, then fills any remaining
 * time with exploration waypoints so new problems can be found.
 *
 * This is an orienteering problem (maximize collected priority within a time budget), solved with
 * a greedy ratio insertion heuristic followed by 2-opt improvement. Routes are a few km at most, so
 * straight-line distances scaled by a detour factor are a good stand-in for street distances.
 *
 * @param walkingSpeed the walking speed in meters per second
 * @param detourFactor the ratio of the real walking distance to the straight line distance
 */
class CleanupRoutePlanner(
    private val walkingSpeed: Double = DEFAULT_WALKING_SPEED,
    private val detourFactor: Double = DEFAULT_DETOUR_FACTOR,
    private val freshnessHalfLife: Duration = Duration.ofDays(14),
    private val explorationScore: Double = 0.15,
    private val maxIssueCandidates: Int = 60
) {

    private class Node(
        val stop: CleanupStop,
        val score: Double,
        val dwellSeconds: Double
    )

    /**
     * @param start where the walk starts and ends
     * @param duration the time the walker wants to spend
     * @param issues the known issues (resolved issues are ignored)
     * @param explorationHeading the heading (degrees) for the exploration loop when there is no
     * better direction to explore
     */
    fun plan(
        start: GeoPoint,
        duration: Duration,
        issues: List<CleanupIssue>,
        now: Instant = Instant.now(),
        explorationHeading: Double = 0.0
    ): CleanupRoute {
        val budget = duration.seconds.toDouble()
        if (budget <= 0) {
            return CleanupRoute(start, emptyList(), 0.0, Duration.ZERO)
        }

        val issueNodes = getIssueCandidates(start, budget, issues, now)
        val tour = mutableListOf<Node>()

        insertGreedily(start, tour, issueNodes, budget)

        // Fill the remaining time with an exploration loop, heading away from the known issues
        val leftover = budget - cost(start, tour)
        if (leftover > budget * MIN_EXPLORATION_FRACTION) {
            val heading = if (tour.isEmpty()) {
                explorationHeading
            } else {
                (CleanupGeo.bearing(start, centroid(tour.map { it.stop.location })) + 180.0) % 360.0
            }
            val explorationNodes = getExplorationLoop(start, leftover, heading)
            insertGreedily(start, tour, explorationNodes, budget)
        }

        improve(start, tour)

        return CleanupRoute(
            start,
            tour.map { it.stop },
            walkingDistance(start, tour),
            Duration.ofSeconds(cost(start, tour).toLong())
        )
    }

    /**
     * The estimated walking time between two points in seconds
     */
    fun walkingSeconds(a: GeoPoint, b: GeoPoint): Double {
        return CleanupGeo.distance(a, b) * detourFactor / walkingSpeed
    }

    internal fun score(issue: CleanupIssue, now: Instant): Double {
        val ageDays = max(0.0, Duration.between(issue.reportedOn, now).seconds / 86400.0)
        val halfLifeDays = freshnessHalfLife.seconds / 86400.0
        val freshness = max(MIN_FRESHNESS, 0.5.pow(ageDays / halfLifeDays))
        val quantity = 1.0 + ln(max(1, issue.count).toDouble())
        return issue.type.priority * quantity * freshness
    }

    private fun getIssueCandidates(
        start: GeoPoint,
        budget: Double,
        issues: List<CleanupIssue>,
        now: Instant
    ): List<Node> {
        return issues
            .asSequence()
            .filter { !it.isResolved }
            .map { Node(CleanupStop(it.location, it), score(it, now), it.type.dwellSeconds.toDouble()) }
            // Only issues that can be reached and returned from within the budget
            .filter { 2 * walkingSeconds(start, it.stop.location) + it.dwellSeconds <= budget }
            // Keep the planner fast in areas with lots of reports
            .sortedByDescending { it.score / (1.0 + walkingSeconds(start, it.stop.location)) }
            .take(maxIssueCandidates)
            .toList()
    }

    /**
     * Creates a ring of waypoints that passes through the start and takes roughly the given time
     */
    private fun getExplorationLoop(start: GeoPoint, seconds: Double, heading: Double): List<Node> {
        val straightLineMeters = seconds * walkingSpeed / detourFactor
        // The perimeter of a regular polygon with n sides inscribed in a circle of radius r
        val perimeterPerRadius = 2 * EXPLORATION_POLYGON_SIDES * sin(Math.PI / EXPLORATION_POLYGON_SIDES)
        val radius = straightLineMeters / perimeterPerRadius
        if (radius < MIN_EXPLORATION_RADIUS) {
            return emptyList()
        }
        val center = CleanupGeo.destination(start, heading, radius)
        val startAngle = (heading + 180.0) % 360.0
        val step = 360.0 / EXPLORATION_POLYGON_SIDES
        return (1 until EXPLORATION_POLYGON_SIDES).map {
            val location = CleanupGeo.destination(center, startAngle + step * it, radius)
            Node(CleanupStop(location), explorationScore, 0.0)
        }
    }

    private fun insertGreedily(
        start: GeoPoint,
        tour: MutableList<Node>,
        candidates: List<Node>,
        budget: Double
    ) {
        val remaining = candidates.toMutableList()
        var currentCost = cost(start, tour)
        while (remaining.isNotEmpty()) {
            var bestCandidate = -1
            var bestPosition = -1
            var bestRatio = Double.NEGATIVE_INFINITY
            var bestAdded = 0.0
            for (c in remaining.indices) {
                val candidate = remaining[c]
                for (p in 0..tour.size) {
                    val previous = if (p == 0) start else tour[p - 1].stop.location
                    val next = if (p == tour.size) start else tour[p].stop.location
                    val added = walkingSeconds(previous, candidate.stop.location) +
                            walkingSeconds(candidate.stop.location, next) -
                            walkingSeconds(previous, next) +
                            candidate.dwellSeconds
                    if (currentCost + added > budget) {
                        continue
                    }
                    val ratio = candidate.score / max(added, 1.0)
                    if (ratio > bestRatio) {
                        bestRatio = ratio
                        bestCandidate = c
                        bestPosition = p
                        bestAdded = added
                    }
                }
            }

            if (bestCandidate == -1) {
                break
            }

            tour.add(bestPosition, remaining.removeAt(bestCandidate))
            currentCost += bestAdded
            if (improve(start, tour)) {
                currentCost = cost(start, tour)
            }
        }
    }

    /**
     * 2-opt: reverse segments of the loop while it makes the loop shorter
     * @return true if the tour changed
     */
    private fun improve(start: GeoPoint, tour: MutableList<Node>): Boolean {
        if (tour.size < 3) {
            return false
        }
        var changed = false
        var improved = true
        var iterations = 0
        while (improved && iterations < MAX_2OPT_ITERATIONS) {
            improved = false
            iterations++
            for (i in 0 until tour.size - 1) {
                for (j in i + 1 until tour.size) {
                    val a = if (i == 0) start else tour[i - 1].stop.location
                    val b = tour[i].stop.location
                    val c = tour[j].stop.location
                    val d = if (j == tour.size - 1) start else tour[j + 1].stop.location
                    val delta = walkingSeconds(a, c) + walkingSeconds(b, d) -
                            walkingSeconds(a, b) - walkingSeconds(c, d)
                    if (delta < -EPSILON) {
                        tour.subList(i, j + 1).reverse()
                        improved = true
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun cost(start: GeoPoint, tour: List<Node>): Double {
        return walkingDistance(start, tour) / walkingSpeed + tour.sumOf { it.dwellSeconds }
    }

    /**
     * The estimated walking distance of the loop in meters (detour factor applied)
     */
    private fun walkingDistance(start: GeoPoint, tour: List<Node>): Double {
        if (tour.isEmpty()) {
            return 0.0
        }
        var total = 0.0
        var previous = start
        for (node in tour) {
            total += CleanupGeo.distance(previous, node.stop.location)
            previous = node.stop.location
        }
        total += CleanupGeo.distance(previous, start)
        return total * detourFactor
    }

    private fun centroid(points: List<GeoPoint>): GeoPoint {
        return GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
    }

    companion object {
        const val DEFAULT_WALKING_SPEED = 1.25
        const val DEFAULT_DETOUR_FACTOR = 1.3
        private const val MIN_FRESHNESS = 0.25
        private const val MIN_EXPLORATION_FRACTION = 0.15
        private const val MIN_EXPLORATION_RADIUS = 25.0
        private const val EXPLORATION_POLYGON_SIDES = 8
        private const val MAX_2OPT_ITERATIONS = 50
        private const val EPSILON = 1e-6
    }
}
