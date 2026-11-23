package com.shieldguard.applock

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    val BG_CANVAS = Color.parseColor("#070B14")
    val BG_SURFACE = Color.parseColor("#0E1525")
    val BG_CARD = Color.parseColor("#131D31")
    val BG_ELEVATED = Color.parseColor("#1A2742")
    val BG_PRESSED = Color.parseColor("#24365B")
    val BORDER_SUBTLE = Color.parseColor("#243452")
    val ACCENT_BLUE = Color.parseColor("#3B82F6")
    val ACCENT_BLUE_PRESSED = Color.parseColor("#2563EB")
    val ACCENT_BLUE_SOFT = Color.parseColor("#172554")
    val ACCENT_GREEN = Color.parseColor("#10B981")
    val ACCENT_GREEN_SOFT = Color.parseColor("#064E3B")
    val ACCENT_AMBER = Color.parseColor("#F59E0B")
    val ACCENT_AMBER_SOFT = Color.parseColor("#451A03")
    val ACCENT_ROSE = Color.parseColor("#F43F5E")
    val ACCENT_ROSE_SOFT = Color.parseColor("#4C0519")
    val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    val TEXT_SECONDARY = Color.parseColor("#94A3B8")
    val TEXT_MUTED = Color.parseColor("#64748B")

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
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
        pressedColor: Int = ACCENT_BLUE_PRESSED,
        textColor: Int = TEXT_PRIMARY,
        strokeColor: Int? = null,
        heightDp: Int = 48,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 14f
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
        textColor: Int
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            background = roundedBox(context, bgColor, 8)
            setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3))
        }
    }

    fun shieldBadgeView(context: Context, active: Boolean, sizeDp: Int = 42): View {
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
            background = roundedBox(context, BG_CARD, 24, BORDER_SUBTLE, 1)
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
