package com.hdtv.live

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(
    fill: Int,
    radiusPx: Float,
    strokeColor: Int = Color.TRANSPARENT,
    strokePx: Int = 0
): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, strokeColor)
}

object Store {
    var all: List<Channel> = emptyList()
    var list: List<Channel> = emptyList()
    var index: Int = 0
}
