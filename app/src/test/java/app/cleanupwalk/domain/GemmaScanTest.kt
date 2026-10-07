package app.cleanupwalk.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaScanTest {

    @Test
    fun parsesTheExpectedFormat() {
        val result = GemmaScan.parse(
            """
            litter: 3
            overflowing_bin: 0
            damaged_infrastructure: 1
            fallen_branch: 0
            main: litter
            confidence: high
            """.trimIndent()
        )
        assertEquals(
            mapOf(CleanupIssueType.Litter to 3, CleanupIssueType.DamagedInfrastructure to 1),
            result.counts
        )
        assertEquals(CleanupIssueType.Litter, result.primaryType)
        assertEquals(0.9f, result.confidence!!, 0.0001f)
    }

    @Test
    fun toleratesMarkdownCasingAndExtraText() {
        val result = GemmaScan.parse(
            """
            Sure! Here is what I see:
            - **Litter**: 5 (bottles and wrappers)
            - Overflowing bin: 1
            - Damaged infrastructure: 0
            - Fallen-branch: none
            **Main:** Overflowing bin.
            **Confidence:** Medium
            """.trimIndent()
        )
        assertEquals(
            mapOf(CleanupIssueType.Litter to 5, CleanupIssueType.OverflowingBin to 1),
            result.counts
        )
        assertEquals(CleanupIssueType.OverflowingBin, result.primaryType)
        assertEquals(0.7f, result.confidence!!, 0.0001f)
    }

    @Test
    fun nothingFoundIsEmpty() {
        val result = GemmaScan.parse(
            """
            litter: 0
            overflowing_bin: 0
            damaged_infrastructure: 0
            fallen_branch: 0
            main: none
            confidence: high
            """.trimIndent()
        )
        assertTrue(result.isEmpty)
        assertTrue(GemmaScan.parse("I can't tell what this is.").isEmpty)
        assertTrue(GemmaScan.parse("").isEmpty)
    }

    @Test
    fun picksTheMostImportantWhenMainIsMissingOrWrong() {
        // "main" names something that wasn't counted, so fall back to priority x count
        val result = GemmaScan.parse(
            """
            litter: 1
            damaged_infrastructure: 1
            main: fallen_branch
            """.trimIndent()
        )
        assertEquals(CleanupIssueType.DamagedInfrastructure, result.primaryType)
        // No confidence line -> a moderate default
        assertEquals(0.7f, result.confidence!!, 0.0001f)
    }

    @Test
    fun capsAbsurdCounts() {
        val result = GemmaScan.parse("litter: 9999\nmain: litter")
        assertEquals(50, result.counts[CleanupIssueType.Litter])
    }

    @Test
    fun promptAsksForEveryCategory() {
        for (key in listOf("litter", "overflowing_bin", "damaged_infrastructure", "fallen_branch", "main", "confidence")) {
            assertTrue(key, GemmaScan.systemInstruction.contains("$key:"))
        }
    }
}
