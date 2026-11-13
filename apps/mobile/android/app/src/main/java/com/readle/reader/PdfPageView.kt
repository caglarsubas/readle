package com.readle.reader

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

data class Glyph(val text: String, val bounds: RectF)

data class PdfHighlight(val id: String, val quote: String, val bounds: List<RectF>)

class PositionedText : PDFTextStripper() {
    val glyphs = mutableListOf<Glyph>()

    override fun processTextPosition(text: TextPosition) {
        glyphs.add(
            Glyph(
                text.unicode,
                RectF(
                    text.xDirAdj,
                    text.yDirAdj - text.heightDir,
                    text.xDirAdj + text.widthDirAdj,
                    text.yDirAdj,
                ),
            )
        )
        super.processTextPosition(text)
    }
}

/** The bitmap remains the original publication. Selection geometry is in PDF page points. */
class PdfPageView(
    context: Context,
    private val onSelection: (JSONObject, String) -> Unit,
    private val onAnnotationTap: (List<String>) -> Unit,
    private val onClearSelection: () -> Unit,
) : View(context) {
    private var bitmap: Bitmap? = null
    private var glyphs: List<Glyph> = emptyList()
    private var pageWidth = 1f
    private var pageHeight = 1f
    var pageIndex = 0
    private var first = -1
    private var last = -1
    private var draggingStart = false
    private var dragging = false
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private var highlights = listOf<PdfHighlight>()
    private val detector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (first >= 0 && !selectionContains(e.x, e.y)) {
                        clearSelection()
                        onClearSelection()
                    }
                    val ids = highlightedObjectsAt(e.x, e.y)
                    if (ids.isNotEmpty()) onAnnotationTap(ids)
                    performClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val index = nearest(e.x, e.y)
                    if (index < 0) return
                    first = index
                    last = index
                    while (first > 0 && joins(glyphs[first - 1], glyphs[first])) first--
                    while (last + 1 < glyphs.size && joins(glyphs[last], glyphs[last + 1])) last++
                    parent.requestDisallowInterceptTouchEvent(true)
                    report()
                }
            },
        )

    fun display(
        image: Bitmap,
        width: Float,
        height: Float,
        text: List<Glyph>,
        annotations: List<PdfHighlight>,
    ) {
        bitmap?.recycle()
        bitmap = image
        pageWidth = width
        pageHeight = height
        glyphs = text
        highlights = annotations
        first = -1
        last = -1
        contentDescription =
            "PDF page ${pageIndex + 1}. Tap a highlight for actions. Long press text to select."
        isFocusable = true
        requestLayout()
        invalidate()
    }

    fun restoreSelection(anchor: JSONObject?) {
        if (anchor?.optInt("pageIndex", -1) == pageIndex) {
            first = anchor.optInt("startGlyph", -1)
            last = anchor.optInt("endGlyph", -1)
            if (first !in glyphs.indices || last !in glyphs.indices) {
                first = -1
                last = -1
            }
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth =
            layoutParams?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val desiredHeight = (measuredWidth * pageHeight / pageWidth).toInt()
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val image = bitmap ?: return
        ink.alpha = 255
        ink.isFilterBitmap = true
        canvas.drawBitmap(image, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), ink)
        val sx = width / pageWidth
        val sy = height / pageHeight
        canvas.save()
        canvas.scale(sx, sy)
        ink.color = Color.argb(90, 226, 192, 79)
        highlights.forEach { highlight -> highlight.bounds.forEach { canvas.drawRect(it, ink) } }
        if (first >= 0 && last >= 0) {
            ink.color = Color.argb(100, 100, 155, 180)
            for (i in min(first, last)..max(first, last)) canvas.drawRect(glyphs[i].bounds, ink)
            ink.color = Color.rgb(53, 87, 66)
            canvas.drawCircle(glyphs[first].bounds.left, glyphs[first].bounds.bottom, 8f / sx, ink)
            canvas.drawCircle(glyphs[last].bounds.right, glyphs[last].bounds.bottom, 8f / sx, ink)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && first >= 0) {
            val x = event.x / width * pageWidth
            draggingStart = abs(x - glyphs[first].bounds.left) < abs(x - glyphs[last].bounds.right)
        }
        if (event.action == MotionEvent.ACTION_DOWN && first >= 0) {
            val sx = width / pageWidth
            val sy = height / pageHeight
            val a = glyphs[first].bounds
            val b = glyphs[last].bounds
            val radius = 28 * resources.displayMetrics.density
            dragging =
                kotlin.math.hypot(event.x - a.left * sx, event.y - a.bottom * sy) < radius ||
                    kotlin.math.hypot(event.x - b.right * sx, event.y - b.bottom * sy) < radius
        }
        detector.onTouchEvent(event)
        if (first >= 0 && dragging && event.action == MotionEvent.ACTION_MOVE) {
            parent.requestDisallowInterceptTouchEvent(true)
            val index = nearest(event.x, event.y)
            if (index >= 0) {
                if (draggingStart) first = index else last = index
                invalidate()
            }
        }
        if (event.action == MotionEvent.ACTION_UP && first >= 0 && dragging) {
            report()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun highlightedObjectsAt(x: Float, y: Float): List<String> {
        val px = x / width.coerceAtLeast(1) * pageWidth
        val py = y / height.coerceAtLeast(1) * pageHeight
        val tolerance = 8 * resources.displayMetrics.density * pageWidth / width.coerceAtLeast(1)
        return highlights
            .filter { highlight ->
                highlight.bounds.any { bounds ->
                    px >= bounds.left - tolerance &&
                        px <= bounds.right + tolerance &&
                        py >= bounds.top - tolerance &&
                        py <= bounds.bottom + tolerance
                }
            }
            .map { it.id }
            .distinct()
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon =
        PointerIcon.getSystemIcon(
            context,
            if (highlightedObjectsAt(event.x, event.y).isEmpty()) PointerIcon.TYPE_TEXT
            else PointerIcon.TYPE_HAND,
        )

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        highlights.forEachIndexed { index, highlight ->
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    ANNOTATION_ACTION_BASE + index,
                    "Highlight ${highlight.quote.take(60)}",
                )
            )
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val index = action - ANNOTATION_ACTION_BASE
        if (index in highlights.indices) {
            onAnnotationTap(listOf(highlights[index].id))
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun nearest(x: Float, y: Float): Int {
        if (glyphs.isEmpty()) return -1
        val px = x / width * pageWidth
        val py = y / height * pageHeight
        return glyphs.indices.minByOrNull { i ->
            val b = glyphs[i].bounds
            val dx = max(b.left - px, max(0f, px - b.right))
            val dy = max(b.top - py, max(0f, py - b.bottom))
            dx * dx + dy * dy * 4
        } ?: -1
    }

    private fun joins(a: Glyph, b: Glyph) =
        abs(a.bounds.bottom - b.bounds.bottom) < 3 &&
            b.bounds.left - a.bounds.right < 2 &&
            !a.text.isBlank() &&
            !b.text.isBlank()

    private fun report() {
        val start = min(first, last)
        val end = max(first, last)
        val selected = glyphs.subList(start, end + 1)
        val text = StringBuilder()
        selected.forEachIndexed { index, glyph ->
            if (index > 0 && !joins(selected[index - 1], glyph)) text.append(' ')
            text.append(glyph.text)
        }
        val quads = JSONArray()
        selected.forEach { g ->
            quads.put(
                JSONArray(listOf(g.bounds.left, g.bounds.top, g.bounds.right, g.bounds.bottom))
            )
        }
        onSelection(
            JSONObject()
                .put("pageIndex", pageIndex)
                .put("startGlyph", start)
                .put("endGlyph", end)
                .put("quads", quads)
                .put("quote", text.toString()),
            text.toString(),
        )
        invalidate()
    }

    private fun selectionContains(x: Float, y: Float): Boolean {
        if (first < 0 || last < 0) return false
        val px = x / width.coerceAtLeast(1) * pageWidth
        val py = y / height.coerceAtLeast(1) * pageHeight
        return (min(first, last)..max(first, last)).any { glyphs[it].bounds.contains(px, py) }
    }

    fun clearSelection() {
        first = -1
        last = -1
        dragging = false
        invalidate()
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
    }

    companion object {
        private const val ANNOTATION_ACTION_BASE = 0x02000000
    }
}
