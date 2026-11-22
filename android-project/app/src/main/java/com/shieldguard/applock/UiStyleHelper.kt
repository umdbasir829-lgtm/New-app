package com.shieldguard.applock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object UiStyleHelper {
    val BG_CANVAS = Color.parseColor("#090E1A")
    val BG_CARD = Color.parseColor("#131C31")
    val BG_ELEVATED = Color.parseColor("#1B2640")
    val BORDER_SUBTLE = Color.parseColor("#263554")
    val ACCENT_BLUE = Color.parseColor("#3B82F6")
    val ACCENT_GREEN = Color.parseColor("#10B981")
    val ACCENT_AMBER = Color.parseColor("#F59E0B")
    val ACCENT_ROSE = Color.parseColor("#F43F5E")
    val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    val TEXT_SECONDARY = Color.parseColor("#94A3B8")

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
}
