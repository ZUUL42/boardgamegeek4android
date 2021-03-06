package com.boardgamegeek.extensions

import android.content.Context
import android.graphics.Color
import android.support.annotation.ArrayRes
import android.support.annotation.ColorInt
import android.support.annotation.StringRes
import com.boardgamegeek.R
import java.text.DecimalFormat
import kotlin.math.roundToInt

fun Double.asRating(context: Context?): String {
    return asScore(context, R.string.unrated)
}

fun Double.asScore(context: Context?, @StringRes defaultResId: Int = 0, format: DecimalFormat = DecimalFormat("#0.0#")): String {
    return when {
        this > 0.0 -> format.format(this)
        defaultResId != 0 && context != null -> context.getString(defaultResId)
        else -> ""
    }
}

fun Double.toDescription(context: Context, @ArrayRes arrayResId: Int, @StringRes zeroStringResId: Int = R.string.unknown): CharSequence {
    if (this == 0.0) return context.getString(zeroStringResId)
    val array = context.resources.getStringArray(arrayResId)
    val index = this.roundToInt() - 1
    return array.getOrElse(index) { context.getString(zeroStringResId) }
}

@ColorInt
fun Double.toColor(colors: IntArray): Int {
    return if (this < 1 || this > colors.size) Color.TRANSPARENT
    else {
        val index = this.toInt()
        val low = colors.getOrNull(index - 1) ?: Color.TRANSPARENT
        val high = colors.getOrNull(index) ?: Color.TRANSPARENT
        low.blendWith(high, index + 1 - this)
    }
}
