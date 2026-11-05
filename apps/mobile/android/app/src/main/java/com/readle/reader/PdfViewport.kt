package com.readle.reader

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.HorizontalScrollView

/** Keeps native scrolling and previews a pinch before committing a sharper page render. */
class PdfViewport(
    context: Context,
    private val onZoom: (Double, Float, Float) -> Unit,
    private val onDoubleTapZoom: (Float, Float) -> Unit,
) : HorizontalScrollView(context) {
    private var handlingPinch = false
    private var scale = 1f
    private val pinch =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    scale = 1f
                    getChildAt(0)?.apply {
                        pivotX = detector.focusX + this@PdfViewport.scrollX
                        pivotY = detector.focusY
                    }
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scale = (scale * detector.scaleFactor).coerceIn(0.25f, 4f)
                    getChildAt(0)?.apply {
                        scaleX = scale
                        scaleY = scale
                    }
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    val factor = scale.toDouble()
                    val x = detector.focusX
                    val y = detector.focusY
                    getChildAt(0)?.apply {
                        scaleX = 1f
                        scaleY = 1f
                    }
                    if (factor != 1.0) post { onZoom(factor, x, y) }
                }
            },
        )
    private val taps =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    val x = event.x
                    val y = event.y
                    post { onDoubleTapZoom(x, y) }
                    return true
                }
            },
        )

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        if (!handlingPinch && event.pointerCount > 1) {
            handlingPinch = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            taps.onTouchEvent(cancel)
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (handlingPinch) {
            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
            )
                handlingPinch = false
            return true
        }
        taps.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
}
