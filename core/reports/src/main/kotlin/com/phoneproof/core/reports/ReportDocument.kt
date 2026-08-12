package com.phoneproof.core.reports

import com.phoneproof.core.model.CheckOutcome

/** How a line is drawn. The writer owns the sizes; this only says what kind of line it is. */
enum class LineStyle {
    SHOP_NAME,
    SHOP_CONTACT,
    TITLE,
    SUBTITLE,
    SECTION,
    VERDICT,
    BODY,
    MEASURE,
    FOOTNOTE,
    SPACER,
}

/** One line of the printed report. */
data class DocLine(
    val text: String,
    val style: LineStyle,
    /** Set on [LineStyle.VERDICT] so the writer can colour it. Null everywhere else. */
    val outcome: CheckOutcome? = null,
)

/** One page. Pagination is decided here rather than by the writer, so it can be tested. */
data class DocPage(val lines: List<DocLine>)

/**
 * Turns a saved report into pages of lines.
 *
 * Pure, and separate from the code that draws the PDF, because pagination and wrapping are where a
 * document generator actually goes wrong — text running off the bottom of a page, or a check split
 * across a page break with its verdict orphaned. Those are logic bugs, and keeping them here means
 * they can be tested without an emulator. The writer that follows is deliberately dumb: it takes
 * lines and draws them.
 *
 * Wrapping is by character budget rather than measured text width. The report uses one font at three
 * sizes, so a conservative budget per style is predictable and testable, where real font metrics
 * would drag Android's Paint into this module and make every pagination test unrunnable here. The
 * budgets are set short enough that proportional text cannot overflow the margin.
 */
object ReportDocument {

    /** Lines that fit on one page at the writer's line heights. Verified against the writer's geometry. */
    const val LINES_PER_PAGE: Int = 46

    private const val BODY_BUDGET = 82
    private const val MEASURE_BUDGET = 74
    private const val TITLE_BUDGET = 46

    fun build(
        report: SavedReport,
        dateLabel: String,
        branding: ShopBranding = ShopBranding.None,
    ): List<DocPage> {
        val lines = buildList {
            if (branding.hasAnything) {
                branding.name?.takeIf { it.isNotBlank() }?.let {
                    add(DocLine(it.trim(), LineStyle.SHOP_NAME))
                }
                branding.contact?.takeIf { it.isNotBlank() }?.let {
                    add(DocLine(it.trim(), LineStyle.SHOP_CONTACT))
                }
                add(DocLine("", LineStyle.SPACER))
            }

            add(DocLine("Phone inspection report", LineStyle.TITLE))
            add(DocLine(report.deviceLabel, LineStyle.SUBTITLE))
            add(DocLine("${report.androidLabel} · $dateLabel", LineStyle.SUBTITLE))
            add(DocLine("", LineStyle.SPACER))
            add(DocLine(report.summaryLine(), LineStyle.BODY))
            add(DocLine("", LineStyle.SPACER))

            report.results.forEach { result ->
                add(DocLine(result.title, LineStyle.SECTION))
                add(DocLine(result.outcome.printed, LineStyle.VERDICT, result.outcome))
                wrap(result.headline, BODY_BUDGET).forEach { add(DocLine(it, LineStyle.BODY)) }
                result.consequence?.let { text ->
                    wrap(text, BODY_BUDGET).forEach { add(DocLine(it, LineStyle.BODY)) }
                }
                result.action?.let { text ->
                    wrap("What to do: $text", BODY_BUDGET).forEach { add(DocLine(it, LineStyle.BODY)) }
                }
                result.measurements.forEach { m ->
                    wrap("${m.label}: ${m.display}", MEASURE_BUDGET)
                        .forEach { add(DocLine(it, LineStyle.MEASURE)) }
                }
                // Printed on paper as well as on screen. A report handed over without its caveats
                // becomes a document someone waves as proof, and every negative reading here has an
                // honest way of being wrong.
                result.falsePositiveCauses.forEach { cause ->
                    wrap("Could be wrong because: $cause", MEASURE_BUDGET)
                        .forEach { add(DocLine(it, LineStyle.FOOTNOTE)) }
                }
                add(DocLine("", LineStyle.SPACER))
            }

            // Always last, and never suppressed by branding. A shop may put its name at the top; it
            // does not get to remove where the numbers came from.
            add(DocLine("", LineStyle.SPACER))
            add(
                DocLine(
                    "Measured on the device by PhoneProof. Nothing was uploaded. " +
                        "Readings describe the phone at the time and date shown.",
                    LineStyle.FOOTNOTE,
                ),
            )
        }

        return paginate(lines)
    }

    /**
     * Splits into pages, keeping a check's heading with the start of its body.
     *
     * A SECTION landing as the last line of a page, with its verdict overleaf, is the one break that
     * makes a printed report actively misleading — a reader could pair the heading with whatever
     * verdict happened to follow it.
     */
    internal fun paginate(lines: List<DocLine>): List<DocPage> {
        if (lines.isEmpty()) return emptyList()

        val pages = mutableListOf<DocPage>()
        var current = mutableListOf<DocLine>()

        lines.forEachIndexed { index, line ->
            val roomLeft = LINES_PER_PAGE - current.size
            val startsGroup = line.style == LineStyle.SECTION
            // A heading needs itself plus its verdict plus one body line to be worth starting.
            val needed = if (startsGroup) 3 else 1

            if (roomLeft < needed && current.isNotEmpty()) {
                pages += DocPage(current.dropLastWhile { it.style == LineStyle.SPACER })
                current = mutableListOf()
            }
            current += line

            if (index == lines.lastIndex && current.isNotEmpty()) {
                pages += DocPage(current.dropLastWhile { it.style == LineStyle.SPACER })
            }
        }

        return pages.filter { it.lines.isNotEmpty() }
    }

    /**
     * Greedy word wrap to a character budget.
     *
     * A word longer than the budget is emitted on its own line rather than split. Breaking mid-word
     * would mangle a model name or a URL, which are exactly the strings a reader needs intact.
     */
    internal fun wrap(text: String, budget: Int): List<String> {
        require(budget > 0) { "budget must be positive" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= budget) return listOf(trimmed)

        val out = mutableListOf<String>()
        val line = StringBuilder()
        trimmed.split(' ').filter { it.isNotEmpty() }.forEach { word ->
            when {
                line.isEmpty() -> line.append(word)
                line.length + 1 + word.length <= budget -> line.append(' ').append(word)
                else -> {
                    out += line.toString()
                    line.setLength(0)
                    line.append(word)
                }
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }
}

/** Word markers, matching the shared text. A printed colour cannot survive a photocopier. */
private val CheckOutcome.printed: String
    get() = when (this) {
        CheckOutcome.PASS -> "PASS"
        CheckOutcome.CAUTION -> "NEEDS CHECKING"
        CheckOutcome.FAIL -> "FAIL"
        CheckOutcome.UNKNOWN -> "COULD NOT TELL"
    }
