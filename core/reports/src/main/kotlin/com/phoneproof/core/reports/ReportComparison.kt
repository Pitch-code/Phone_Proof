package com.phoneproof.core.reports

import com.phoneproof.core.model.CheckOutcome

/** One check, as it came out on each of two phones. */
data class ComparisonRow(
    val checkId: String,
    val title: String,
    val left: CheckOutcome?,
    val right: CheckOutcome?,
    /** The headline reading for each side, so a difference can be read without opening both. */
    val leftDetail: String?,
    val rightDetail: String?,
) {
    /**
     * Which side did better, or null when there is nothing to choose between them.
     *
     * A check missing from one report is not a win for the other. It means one phone was not tested
     * for it, and treating absence as a pass would make a half-finished scan look like the better
     * phone — which is exactly backwards.
     */
    val better: ComparisonSide?
        get() {
            val l = left ?: return null
            val r = right ?: return null
            val lRank = l.rank
            val rRank = r.rank
            return when {
                lRank == rRank -> null
                lRank > rRank -> ComparisonSide.LEFT
                else -> ComparisonSide.RIGHT
            }
        }

    val differs: Boolean get() = left != right
}

enum class ComparisonSide { LEFT, RIGHT }

/** The outcome of comparing two saved reports. */
data class Comparison(
    val left: SavedReport,
    val right: SavedReport,
    val rows: List<ComparisonRow>,
) {
    val differingRows: List<ComparisonRow> get() = rows.filter { it.differs }

    /**
     * A recommendation, or null when the app should not offer one.
     *
     * Deliberately conservative. It only speaks when one phone is better on at least one check and
     * worse on none — a phone that wins three checks and loses two is a judgement call about which
     * faults the buyer cares about, and the app does not know that. Guessing would be the kind of
     * confident nonsense this project exists to avoid.
     */
    val clearlyBetter: ComparisonSide?
        get() {
            val wins = rows.count { it.better == ComparisonSide.LEFT }
            val losses = rows.count { it.better == ComparisonSide.RIGHT }
            return when {
                wins > 0 && losses == 0 -> ComparisonSide.LEFT
                losses > 0 && wins == 0 -> ComparisonSide.RIGHT
                else -> null
            }
        }
}

/**
 * Puts two saved reports side by side.
 *
 * Rows are keyed by check id, not by position, because the two phones may have been scanned by
 * different app versions or may simply support different checks. Matching by index would silently
 * compare a battery reading against a storage reading.
 */
fun compareReports(left: SavedReport, right: SavedReport): Comparison {
    val byIdLeft = left.results.associateBy { it.id }
    val byIdRight = right.results.associateBy { it.id }

    // Left's order first, so the report the buyer opened stays recognisable, then anything only the
    // right-hand phone was tested for.
    val ids = LinkedHashSet<String>().apply {
        addAll(left.results.map { it.id })
        addAll(right.results.map { it.id })
    }

    val rows = ids.map { id ->
        val l = byIdLeft[id]
        val r = byIdRight[id]
        ComparisonRow(
            checkId = id,
            // Either side can supply the title; they agree unless a version renamed a check.
            title = l?.title ?: r?.title ?: id,
            left = l?.outcome,
            right = r?.outcome,
            leftDetail = l?.headline,
            rightDetail = r?.headline,
        )
    }

    return Comparison(left = left, right = right, rows = rows)
}

/** Higher is better. Used only to decide which of two outcomes is preferable. */
private val CheckOutcome.rank: Int
    get() = when (this) {
        CheckOutcome.PASS -> 3
        // Above CAUTION on purpose: "could not tell" is an absence of evidence, whereas CAUTION is
        // evidence of a problem. Ranking them the other way would make an untested phone look worse
        // than one with a known fault.
        CheckOutcome.UNKNOWN -> 2
        CheckOutcome.CAUTION -> 1
        CheckOutcome.FAIL -> 0
    }
