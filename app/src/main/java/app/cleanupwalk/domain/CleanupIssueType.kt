package app.cleanupwalk.domain

/**
 * The kinds of problems the cleanup walk looks for.
 * @param id the persisted ID (never reuse or change)
 * @param modelClassIndex the class index produced by the on-device detection model
 * @param priority how strongly the route planner prefers visiting this kind of issue
 * @param dwellSeconds the expected time spent at the issue (picking up, photographing, reporting)
 */
enum class CleanupIssueType(
    val id: Int,
    val modelClassIndex: Int,
    val priority: Float,
    val dwellSeconds: Long
) {
    Litter(1, 0, 1.0f, 90),
    OverflowingBin(2, 1, 1.4f, 45),
    DamagedInfrastructure(3, 2, 1.8f, 60),
    FallenBranch(4, 3, 1.2f, 90);

    /**
     * True if the walker can usually fix this issue themselves (vs. only report it)
     */
    val isRemovable: Boolean
        get() = this == Litter || this == FallenBranch

    companion object {
        fun fromId(id: Int): CleanupIssueType? {
            return entries.firstOrNull { it.id == id }
        }

        fun fromModelClass(index: Int): CleanupIssueType? {
            return entries.firstOrNull { it.modelClassIndex == index }
        }
    }
}
