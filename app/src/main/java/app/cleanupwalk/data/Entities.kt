package app.cleanupwalk.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.cleanupwalk.domain.CleanupIssue
import app.cleanupwalk.domain.CleanupIssueType
import app.cleanupwalk.domain.CleanupWalkSession
import app.cleanupwalk.domain.GeoPoint
import java.time.Duration
import java.time.Instant

@Entity(tableName = "issues", indices = [Index(value = ["resolved_on"])])
data class IssueEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "reported_on") val reportedOn: Long,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "resolved_on") val resolvedOn: Long?,
    @ColumnInfo(name = "confidence") val confidence: Float?,
    @ColumnInfo(name = "notes") val notes: String?
) {
    fun toIssue(): CleanupIssue = CleanupIssue(
        id = id,
        type = CleanupIssueType.fromId(type) ?: CleanupIssueType.Litter,
        location = GeoPoint(latitude, longitude),
        reportedOn = Instant.ofEpochMilli(reportedOn),
        count = count,
        resolvedOn = resolvedOn?.let { Instant.ofEpochMilli(it) },
        confidence = confidence,
        notes = notes
    )

    companion object {
        fun from(issue: CleanupIssue) = IssueEntity(
            id = issue.id,
            type = issue.type.id,
            latitude = issue.location.latitude,
            longitude = issue.location.longitude,
            reportedOn = issue.reportedOn.toEpochMilli(),
            count = issue.count,
            resolvedOn = issue.resolvedOn?.toEpochMilli(),
            confidence = issue.confidence,
            notes = issue.notes
        )
    }
}

@Entity(tableName = "walks", indices = [Index(value = ["end_time"])])
data class WalkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "planned_duration") val plannedDuration: Long,
    @ColumnInfo(name = "distance") val distance: Double,
    @ColumnInfo(name = "litter_removed") val litterRemoved: Int,
    @ColumnInfo(name = "issues_reported") val issuesReported: Int,
    @ColumnInfo(name = "issues_resolved") val issuesResolved: Int
) {
    fun toSession(): CleanupWalkSession = CleanupWalkSession(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let { Instant.ofEpochMilli(it) },
        plannedDuration = Duration.ofMillis(plannedDuration),
        distanceMeters = distance,
        litterRemoved = litterRemoved,
        issuesReported = issuesReported,
        issuesResolved = issuesResolved
    )

    companion object {
        fun from(session: CleanupWalkSession) = WalkEntity(
            id = session.id,
            startTime = session.startTime.toEpochMilli(),
            endTime = session.endTime?.toEpochMilli(),
            plannedDuration = session.plannedDuration.toMillis(),
            distance = session.distanceMeters,
            litterRemoved = session.litterRemoved,
            issuesReported = session.issuesReported,
            issuesResolved = session.issuesResolved
        )
    }
}
