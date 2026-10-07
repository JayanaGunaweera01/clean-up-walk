package app.cleanupwalk.domain

/**
 * The prompt Gemma gets with each scan photo, and the parser for its reply.
 *
 * Gemma answers in a fixed line format so the result can be parsed without a JSON grammar:
 *
 *     litter: 3
 *     overflowing_bin: 0
 *     damaged_infrastructure: 1
 *     fallen_branch: 0
 *     main: litter
 *     confidence: high
 */
object GemmaScan {

    val systemInstruction = """
        You help volunteers clean up their neighborhood. You look at one photo taken outdoors and
        count problems a walker could clean up or report.
        Categories:
        - litter: rubbish on the ground or in plants, such as bottles, cans, wrappers, bags, cups, cigarette butts. Count each item.
        - overflowing_bin: a public bin that is full, overflowing, or has rubbish piled around it.
        - damaged_infrastructure: broken or damaged public things, such as benches, signs, railings, fences, lights, pavement, potholes.
        - fallen_branch: branches or trees that have fallen onto a path, road, or grass.
        Only count what you can clearly see. If something is not there, give 0.
        Reply with exactly these six lines and nothing else:
        litter: <number>
        overflowing_bin: <number>
        damaged_infrastructure: <number>
        fallen_branch: <number>
        main: <litter|overflowing_bin|damaged_infrastructure|fallen_branch|none>
        confidence: <high|medium|low>
    """.trimIndent()

    const val USER_PROMPT = "What cleanup problems are in this photo?"

    private val keys = mapOf(
        "litter" to CleanupIssueType.Litter,
        "overflowing_bin" to CleanupIssueType.OverflowingBin,
        "damaged_infrastructure" to CleanupIssueType.DamagedInfrastructure,
        "fallen_branch" to CleanupIssueType.FallenBranch
    )

    private val confidenceValues = mapOf("high" to 0.9f, "medium" to 0.7f, "low" to 0.5f)

    /**
     * Turns Gemma's reply into a scan result. Tolerates extra text, markdown, different casing,
     * spaces instead of underscores, and missing lines.
     */
    fun parse(response: String): CleanupScanResult {
        val counts = mutableMapOf<CleanupIssueType, Int>()
        var main: CleanupIssueType? = null
        var confidence: Float? = null

        for (rawLine in response.lines()) {
            val line = rawLine.trim().trimStart('-', '*', '•', ' ').replace("**", "").lowercase()
            val separator = line.indexOf(':')
            if (separator <= 0) {
                continue
            }
            val key = line.substring(0, separator).trim().replace(' ', '_').replace('-', '_')
            val value = line.substring(separator + 1).trim()

            when {
                key in keys -> {
                    val number = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: 0
                    if (number > 0) {
                        counts[keys.getValue(key)] = number.coerceAtMost(MAX_COUNT)
                    }
                }

                key == "main" -> {
                    val normalized = value.replace(' ', '_').replace('-', '_').trim('.', '"', '\'', '<', '>')
                    main = keys[normalized]
                }

                key == "confidence" -> {
                    confidence = confidenceValues.entries.firstOrNull { value.startsWith(it.key) }?.value
                }
            }
        }

        if (counts.isEmpty()) {
            return CleanupScanResult.empty
        }

        // Trust Gemma's "main" if it counted something of that type, otherwise pick the most important
        val primary = main?.takeIf { counts.containsKey(it) }
            ?: counts.keys.maxBy { it.priority * counts.getValue(it) }

        return CleanupScanResult(counts, primary, confidence ?: DEFAULT_CONFIDENCE)
    }

    private const val MAX_COUNT = 50
    private const val DEFAULT_CONFIDENCE = 0.7f
}
