package com.shieldguard.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView(context: Context) : View(context) {

    interface Listener {
        fun onPatternComplete(pattern: String)
    }

    var listener: Listener? = null

    private val dotCount = 3
    private val selectedDots = mutableListOf<Int>()
    private var currentTouchX = -1f
    private var currentTouchY = -1f
    private var isDrawing = false
    private var isErrorState = false

    private val dotCentersX = FloatArray(9)
    private val dotCentersY = FloatArray(9)
    private var hitRadius = 0f

    private val normalDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyleHelper.BORDER_SUBTLE
        style = Paint.Style.FILL
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyleHelper.ACCENT_BLUE
        style = Paint.Style.FILL
    }
    private val activeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x443B82F6
        style = Paint.Style.FILL
    }
    private val errorDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyleHelper.ACCENT_ROSE
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyleHelper.ACCENT_BLUE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val rawW = MeasureSpec.getSize(widthMeasureSpec)
        val defaultSize = UiStyleHelper.dp(context, 260)
        val size = if (mode == MeasureSpec.UNSPECIFIED || rawW == 0) {
            defaultSize
        } else {
            minOf(rawW, UiStyleHelper.dp(context, 280))
        }
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cellW = w / 3f
        val cellH = h / 3f
        hitRadius = minOf(cellW, cellH) * 0.34f
        linePaint.strokeWidth = UiStyleHelper.dp(context, 4).toFloat()

        for (r in 0 until dotCount) {
            for (c in 0 until dotCount) {
                val idx = r * dotCount + c
                dotCentersX[idx] = cellW * (c + 0.5f)
                dotCentersY[idx] = cellH * (r + 0.5f)
            }
        }
    }

    fun showErrorAndReset() {
        isErrorState = true
        invalidate()
        postDelayed({
            selectedDots.clear()
            isErrorState = false
            invalidate()
        }, 650)
    }

    fun reset() {
        selectedDots.clear()
        isErrorState = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (selectedDots.isNotEmpty()) {
            linePaint.color = if (isErrorState) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.ACCENT_BLUE
            val path = Path()
            val first = selectedDots[0]
            path.moveTo(dotCentersX[first], dotCentersY[first])
            for (i in 1 until selectedDots.size) {
                val idx = selectedDots[i]
                path.lineTo(dotCentersX[idx], dotCentersY[idx])
            }
            if (isDrawing && currentTouchX >= 0 && currentTouchY >= 0) {
                path.lineTo(currentTouchX, currentTouchY)
            }
            canvas.drawPath(path, linePaint)
        }

        val dotRadius = UiStyleHelper.dp(context, 9).toFloat()
        val haloRadius = UiStyleHelper.dp(context, 20).toFloat()

        for (i in 0 until 9) {
            val cx = dotCentersX[i]
            val cy = dotCentersY[i]
            val selected = selectedDots.contains(i)
            if (selected) {
                activeHaloPaint.color = if (isErrorState) 0x44F43F5E else 0x443B82F6
                canvas.drawCircle(cx, cy, haloRadius, activeHaloPaint)
                canvas.drawCircle(
                    cx,
                    cy,
                    dotRadius * 1.15f,
                    if (isErrorState) errorDotPaint else activeDotPaint
                )
            } else {
                canvas.drawCircle(cx, cy, dotRadius, normalDotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedDots.clear()
                isErrorState = false
                isDrawing = true
                currentTouchX = event.x
                currentTouchY = event.y
                checkDotHit(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentTouchX = event.x
                currentTouchY = event.y
                checkDotHit(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                currentTouchX = -1f
                currentTouchY = -1f
                if (selectedDots.size >= 4) {
                    val code = selectedDots.joinToString("-")
                    listener?.onPatternComplete(code)
                } else if (selectedDots.isNotEmpty()) {
                    showErrorAndReset()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkDotHit(x: Float, y: Float) {
        for (i in 0 until 9) {
            if (selectedDots.contains(i)) continue
            val dist = hypot((x - dotCentersX[i]).toDouble(), (y - dotCentersY[i]).toDouble())
            if (dist <= hitRadius) {
                selectedDots.add(i)
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}
