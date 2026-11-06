package org.readium.r2.navigator

import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import org.readium.r2.navigator.preferences.ReadingProgression

/** Separates vertical reading swipes from the horizontal pager and native handle gestures. */
internal class ReadlePageGesture(
    private val view: R2BasicWebView,
    private val atVerticalEdge: (Int) -> Boolean,
) {
    var tracking = false
        private set

    private var startX = 0f
    private var startY = 0f
    private var top = false
    private var bottom = false
    private var scroll = false
    private val minimum = ViewConfiguration.get(view.context).scaledTouchSlop * 4

    fun track(event: MotionEvent): Boolean? {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                startX = event.x
                startY = event.y
                scroll = view.scrollMode
                top = atVerticalEdge(-1)
                bottom = atVerticalEdge(1)
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_CANCEL -> tracking = false
            MotionEvent.ACTION_UP -> {
                if (!tracking) return null
                tracking = false
                if (scroll != view.scrollMode) return null
                val dx = event.x - startX
                val dy = event.y - startY
                if (scroll && view.listener?.verticalText != true) {
                    if (abs(dy) < minimum || abs(dy) < abs(dx) * 1.5f) return null
                    if (dy < 0 && bottom) return true
                    if (dy > 0 && top) return false
                } else if (!scroll && abs(dx) >= minimum && abs(dx) > abs(dy) * 1.5f) {
                    val rtl = view.listener?.readingProgression == ReadingProgression.RTL
                    return (dx < 0) != rtl
                }
            }
        }
        return null
    }
}
