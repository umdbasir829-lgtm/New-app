package com.shieldguard.applock

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

object UiStyleHelper {
    val BG_CANVAS = Color.parseColor("#060A13")
    val BG_CANVAS_TOP = Color.parseColor("#0C1528")
    val BG_SURFACE = Color.parseColor("#0B1221")
    val BG_CARD = Color.parseColor("#131E34")
    val BG_CARD_TOP = Color.parseColor("#192744")
    val BG_ELEVATED = Color.parseColor("#1C2A47")
    val BG_PRESSED = Color.parseColor("#283B63")
    val BORDER_SUBTLE = Color.parseColor("#273A5E")
    val BORDER_GLOW = Color.parseColor("#3B5998")
    var ACCENT_BLUE = Color.parseColor("#3B82F6")
    val ACCENT_BLUE_DEEP = Color.parseColor("#2563EB")
    val ACCENT_BLUE_SOFT = Color.parseColor("#162A56")
    var ACCENT_GREEN = Color.parseColor("#10B981")
    val ACCENT_GREEN_SOFT = Color.parseColor("#064E3B")
    val ACCENT_AMBER = Color.parseColor("#F59E0B")
    val ACCENT_AMBER_SOFT = Color.parseColor("#451A03")
    val ACCENT_ROSE = Color.parseColor("#F43F5E")
    val ACCENT_ROSE_SOFT = Color.parseColor("#4C0519")
    val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    val TEXT_SECONDARY = Color.parseColor("#94A3B8")
    val TEXT_MUTED = Color.parseColor("#64748B")

    fun applyRuntimeTheme(prefs: PrefsManager) {
        try {
            val blue = prefs.getRemoteAccentBlue()
            if (blue.startsWith("#")) ACCENT_BLUE = Color.parseColor(blue)
        } catch (_: Exception) {}
        try {
            val green = prefs.getRemoteAccentGreen()
            if (green.startsWith("#")) ACCENT_GREEN = Color.parseColor(green)
        } catch (_: Exception) {}
    }

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    fun canvasGradient(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(BG_CANVAS_TOP, BG_CANVAS, BG_CANVAS)
        )
    }

    fun cardGradientBox(
        context: Context,
        radiusDp: Int = 20,
        strokeColor: Int = BORDER_SUBTLE,
        topColor: Int = BG_CARD_TOP,
        bottomColor: Int = BG_CARD
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setStroke(dp(context, 1), strokeColor)
        }
    }

    fun roundedBox(
        context: Context,
        fillColor: Int,
        radiusDp: Int = 16,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fillColor)
            if (strokeColor != null) {
                setStroke(dp(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun touchableRoundedBox(
        context: Context,
        normalColor: Int,
        pressedColor: Int,
        radiusDp: Int = 14,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                roundedBox(context, pressedColor, radiusDp, strokeColor, strokeWidthDp)
            )
            addState(
                intArrayOf(),
                roundedBox(context, normalColor, radiusDp, strokeColor, strokeWidthDp)
            )
        }
    }

    fun circleBox(
        context: Context,
        fillColor: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            if (strokeColor != null) {
                setStroke(dp(context, strokeWidthDp), strokeColor)
            }
        }
    }

    fun touchableCircleBox(
        context: Context,
        normalColor: Int,
        pressedColor: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                circleBox(context, pressedColor, strokeColor, strokeWidthDp)
            )
            addState(
                intArrayOf(),
                circleBox(context, normalColor, strokeColor, strokeWidthDp)
            )
        }
    }

    fun actionButton(
        context: Context,
        label: String,
        bgColor: Int = ACCENT_BLUE,
        pressedColor: Int = ACCENT_BLUE_DEEP,
        textColor: Int = TEXT_PRIMARY,
        strokeColor: Int? = null,
        heightDp: Int = 46,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = touchableRoundedBox(context, bgColor, pressedColor, 14, strokeColor, 1)
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, heightDp)
            )
            setOnClickListener { onClick() }
        }
    }

    fun chipBadge(
        context: Context,
        label: String,
        bgColor: Int,
        textColor: Int,
        strokeColor: Int? = null
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11f
            setSingleLine(true)
            maxLines = 1
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            background = roundedBox(context, bgColor, 9, strokeColor, 1)
            setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4))
        }
    }

    fun shieldBadgeView(context: Context, active: Boolean, sizeDp: Int = 40): View {
        return object : View(context) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (active) ACCENT_BLUE_SOFT else ACCENT_AMBER_SOFT
                style = Paint.Style.FILL
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (active) ACCENT_BLUE else ACCENT_AMBER
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                strokeJoin = Paint.Join.ROUND
            }
            private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (active) ACCENT_GREEN else ACCENT_AMBER
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat() + 1f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val px = dp(context, sizeDp)
                setMeasuredDimension(px, px)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.08f)
                    lineTo(w * 0.86f, h * 0.22f)
                    lineTo(w * 0.86f, h * 0.50f)
                    quadTo(w * 0.84f, h * 0.78f, w * 0.50f, h * 0.92f)
                    quadTo(w * 0.16f, h * 0.78f, w * 0.14f, h * 0.50f)
                    lineTo(w * 0.14f, h * 0.22f)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)

                if (active) {
                    val mark = Path().apply {
                        moveTo(w * 0.34f, h * 0.52f)
                        lineTo(w * 0.45f, h * 0.63f)
                        lineTo(w * 0.67f, h * 0.39f)
                    }
                    canvas.drawPath(mark, checkPaint)
                } else {
                    canvas.drawLine(w * 0.50f, h * 0.34f, w * 0.50f, h * 0.54f, checkPaint)
                    canvas.drawCircle(w * 0.50f, h * 0.65f, dp(context, 1).toFloat() + 1f, checkPaint)
                }
            }
        }
    }

    fun fingerprintIconView(context: Context, sizeDp: Int = 26, tintColor: Int = ACCENT_BLUE): View {
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tintColor
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                strokeCap = Paint.Cap.ROUND
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val px = dp(context, sizeDp)
                setMeasuredDimension(px, px)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val cx = w * 0.5f
                val cy = h * 0.53f

                // Outer fingerprint ridge arcs
                canvas.drawArc(RectF(w * 0.14f, h * 0.14f, w * 0.86f, h * 0.88f), 205f, 130f, false, paint)
                canvas.drawArc(RectF(w * 0.24f, h * 0.24f, w * 0.76f, h * 0.84f), 190f, 165f, false, paint)
                canvas.drawArc(RectF(w * 0.34f, h * 0.34f, w * 0.66f, h * 0.80f), 185f, 175f, false, paint)
                canvas.drawLine(cx, cy - h * 0.05f, cx, h * 0.82f, paint)
                canvas.drawArc(RectF(w * 0.42f, h * 0.43f, w * 0.58f, h * 0.74f), 200f, 140f, false, paint)
            }
        }
    }

    fun navIconView(context: Context, tabIndex: Int, active: Boolean): View {
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (active) ACCENT_BLUE else TEXT_SECONDARY
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val px = dp(context, 20)
                setMeasuredDimension(px, px)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                when (tabIndex) {
                    0 -> {
                        val r = dp(context, 2).toFloat()
                        canvas.drawRoundRect(RectF(w * 0.12f, h * 0.12f, w * 0.44f, h * 0.44f), r, r, paint)
                        canvas.drawRoundRect(RectF(w * 0.56f, h * 0.12f, w * 0.88f, h * 0.44f), r, r, paint)
                        canvas.drawRoundRect(RectF(w * 0.12f, h * 0.56f, w * 0.44f, h * 0.88f), r, r, paint)
                        canvas.drawRoundRect(RectF(w * 0.56f, h * 0.56f, w * 0.88f, h * 0.88f), r, r, paint)
                    }
                    1 -> {
                        val path = Path().apply {
                            moveTo(w * 0.50f, h * 0.10f)
                            lineTo(w * 0.84f, h * 0.24f)
                            lineTo(w * 0.84f, h * 0.52f)
                            quadTo(w * 0.82f, h * 0.78f, w * 0.50f, h * 0.90f)
                            quadTo(w * 0.18f, h * 0.78f, w * 0.16f, h * 0.52f)
                            lineTo(w * 0.16f, h * 0.24f)
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                    else -> {
                        canvas.drawArc(RectF(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.85f), -40f, 230f, false, paint)
                        canvas.drawLine(w * 0.50f, h * 0.24f, w * 0.50f, h * 0.66f, paint)
                        canvas.drawLine(w * 0.34f, h * 0.50f, w * 0.50f, h * 0.66f, paint)
                        canvas.drawLine(w * 0.66f, h * 0.50f, w * 0.50f, h * 0.66f, paint)
                    }
                }
            }
        }
    }

    fun slidersIconView(context: Context, sizeDp: Int = 18, tintColor: Int = TEXT_SECONDARY): View {
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tintColor
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2).toFloat()
                strokeCap = Paint.Cap.ROUND
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val px = dp(context, sizeDp)
                setMeasuredDimension(px, px)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                canvas.drawLine(w * 0.15f, h * 0.30f, w * 0.85f, h * 0.30f, paint)
                canvas.drawCircle(w * 0.65f, h * 0.30f, h * 0.10f, paint)
                canvas.drawLine(w * 0.15f, h * 0.70f, w * 0.85f, h * 0.70f, paint)
                canvas.drawCircle(w * 0.35f, h * 0.70f, h * 0.10f, paint)
            }
        }
    }

    fun showDarkDialog(
        context: Context,
        title: String,
        subtitle: String? = null,
        buildContent: (Dialog, LinearLayout) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardGradientBox(context, 24, BORDER_GLOW)
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 18))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(context, if (subtitle != null) 4 else 14))
        }
        val titleTv = TextView(context).apply {
            text = title
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeTv = TextView(context).apply {
            text = "✕"
            textSize = 15f
            setTextColor(TEXT_SECONDARY)
            gravity = Gravity.CENTER
            background = touchableCircleBox(context, BG_ELEVATED, BG_PRESSED, BORDER_SUBTLE, 1)
            layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32))
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(titleTv)
        headerRow.addView(closeTv)
        card.addView(headerRow)

        if (!subtitle.isNullOrBlank()) {
            val subTv = TextView(context).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 0, 0, dp(context, 14))
            }
            card.addView(subTv)
        }

        val contentHolder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        buildContent(dialog, contentHolder)
        card.addView(contentHolder)

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            addView(card)
        }

        dialog.setContentView(outer)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }
}
