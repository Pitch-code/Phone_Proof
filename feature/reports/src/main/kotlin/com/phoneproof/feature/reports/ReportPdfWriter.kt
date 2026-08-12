package com.phoneproof.feature.reports

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.reports.DocLine
import com.phoneproof.core.reports.DocPage
import com.phoneproof.core.reports.LineStyle
import com.phoneproof.core.reports.ReportDocument
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.ShopBranding
import java.io.File

/**
 * Draws a report to a PDF file.
 *
 * Deliberately dumb. Every decision that could be wrong — what goes on the page, in what order,
 * where the page breaks fall, how text wraps — is made by [ReportDocument], which is pure Kotlin and
 * tested. This class takes a list of lines and puts ink on paper. That split is the whole point:
 * pagination bugs are logic bugs, and there is no emulator here to catch them in a renderer.
 *
 * A4 at 72 points per inch, which is what `PdfDocument` works in. Not Letter: this app is aimed at
 * India, where A4 is what a shop's printer holds.
 */
class ReportPdfWriter(private val context: Context) {

    /**
     * Writes [report] to a PDF and returns the file.
     *
     * Written into `cacheDir/shared`, which is the directory the FileProvider exposes. Cache rather
     * than files: a shared PDF is a copy made for one send, and leaving copies in permanent storage
     * would quietly grow forever. The saved report itself remains the original.
     */
    fun write(
        report: SavedReport,
        dateLabel: String,
        branding: ShopBranding = ShopBranding.None,
    ): File {
        val pages = ReportDocument.build(report, dateLabel, branding)
        val document = PdfDocument()

        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val pdfPage = document.startPage(info)
                drawPage(pdfPage.canvas, page, branding, index + 1, pages.size)
                document.finishPage(pdfPage)
            }

            val directory = File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
            val file = File(directory, fileNameFor(report))
            file.outputStream().use { document.writeTo(it) }
            return file
        } finally {
            // close() releases native pages. Skipping it on the error path leaks them, and the error
            // path is exactly where a half-built document is most likely.
            document.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        page: DocPage,
        branding: ShopBranding,
        pageNumber: Int,
        pageCount: Int,
    ) {
        var y = MARGIN.toFloat()
        var logoDrawn = false

        page.lines.forEach { line ->
            // The logo sits beside the shop name, so it only appears on a page that has one — which
            // is the first page.
            if (!logoDrawn && line.style == LineStyle.SHOP_NAME) {
                logoDrawn = drawLogo(canvas, branding, y)
            }

            val paint = paintFor(line)
            y += leadingFor(line.style)
            if (line.text.isNotEmpty()) {
                val x = if (logoDrawn && line.style.isHeader) {
                    MARGIN + LOGO_SIZE + LOGO_GAP
                } else {
                    MARGIN
                }
                canvas.drawText(line.text, x.toFloat(), y, paint)
            }
        }

        canvas.drawText(
            "Page $pageNumber of $pageCount",
            (PAGE_WIDTH - MARGIN - 90).toFloat(),
            (PAGE_HEIGHT - 24).toFloat(),
            footerPaint,
        )
    }

    /** @return true when a logo was actually drawn, so the header can be indented past it. */
    private fun drawLogo(canvas: Canvas, branding: ShopBranding, top: Float): Boolean {
        val path = branding.logoPath ?: return false
        val file = File(path)
        if (!file.exists()) return false

        // A shop's logo is an arbitrary file the user picked. It may be huge, corrupt, or not an
        // image at all, and none of those may take down the export of a report they need now.
        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return false

        val destination = Rect(MARGIN, top.toInt(), MARGIN + LOGO_SIZE, top.toInt() + LOGO_SIZE)
        canvas.drawBitmap(bitmap, null, destination, null)
        bitmap.recycle()
        return true
    }

    private fun paintFor(line: DocLine): Paint = when (line.style) {
        LineStyle.SHOP_NAME -> paint(15f, bold = true)
        LineStyle.SHOP_CONTACT -> paint(10f, colour = GREY)
        LineStyle.TITLE -> paint(18f, bold = true)
        LineStyle.SUBTITLE -> paint(11f, colour = GREY)
        LineStyle.SECTION -> paint(13f, bold = true)
        LineStyle.VERDICT -> paint(11f, bold = true, colour = colourFor(line.outcome))
        LineStyle.BODY -> paint(10.5f)
        LineStyle.MEASURE -> paint(9.5f, colour = GREY)
        LineStyle.FOOTNOTE -> paint(8.5f, colour = LIGHT_GREY)
        LineStyle.SPACER -> paint(10f)
    }

    private fun leadingFor(style: LineStyle): Float = when (style) {
        LineStyle.TITLE -> 24f
        LineStyle.SHOP_NAME -> 19f
        LineStyle.SECTION -> 20f
        LineStyle.VERDICT -> 14f
        LineStyle.SPACER -> 7f
        LineStyle.FOOTNOTE -> 11f
        else -> 14f
    }

    /**
     * Print colours, chosen to survive a black and white printer.
     *
     * A verdict is a word first — PASS, FAIL — with colour as reinforcement only, which is why
     * [ReportDocument] emits text rather than relying on this. Amber prints as a mid grey that would
     * be unreadable as the sole signal.
     */
    private fun colourFor(outcome: CheckOutcome?): Int = when (outcome) {
        CheckOutcome.PASS -> Color.rgb(21, 128, 61)
        CheckOutcome.CAUTION -> Color.rgb(180, 83, 9)
        CheckOutcome.FAIL -> Color.rgb(190, 24, 40)
        else -> Color.rgb(82, 82, 91)
    }

    private fun paint(size: Float, bold: Boolean = false, colour: Int = Color.BLACK) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = colour
        typeface = if (bold) BOLD_TYPEFACE else REGULAR_TYPEFACE
    }

    private val footerPaint = paint(8.5f, colour = LIGHT_GREY)

    private fun fileNameFor(report: SavedReport): String {
        val device = report.deviceLabel
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(40)
            .ifBlank { "phone" }
        return "PhoneProof-$device-${report.id}.pdf"
    }

    private companion object {
        // A4 at 72 points per inch.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40
        const val LOGO_SIZE = 46
        const val LOGO_GAP = 10

        const val SHARED_DIRECTORY = "shared"

        val GREY: Int = Color.rgb(90, 90, 96)
        val LIGHT_GREY: Int = Color.rgb(130, 130, 138)

        val REGULAR_TYPEFACE: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val BOLD_TYPEFACE: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
}

/** Header lines sit beside the logo; everything else starts at the margin. */
private val LineStyle.isHeader: Boolean
    get() = this == LineStyle.SHOP_NAME || this == LineStyle.SHOP_CONTACT
