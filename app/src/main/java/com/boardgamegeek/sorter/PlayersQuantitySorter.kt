package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.support.annotation.StringRes

import com.boardgamegeek.R
import com.boardgamegeek.extensions.getInt
import com.boardgamegeek.provider.BggContract.Plays

class PlayersQuantitySorter(context: Context) : PlayersSorter(context) {

    override val descriptionId: Int
        @StringRes
        get() = R.string.menu_sort_quantity

    override val type: Int
        get() = PlayersSorterFactory.TYPE_QUANTITY

    override val sortColumn: String
        get() = Plays.SUM_QUANTITY

    override val isSortDescending: Boolean
        get() = true

    public override fun getHeaderText(cursor: Cursor): String {
        val quantity = cursor.getInt(Plays.SUM_QUANTITY)
        val prefix = quantity.toString().substring(0, 1)
        val suffix = when {
            quantity >= 10000 -> "0000+"
            quantity >= 1000 -> "000+"
            quantity >= 100 -> "00+"
            quantity >= 10 -> "0+"
            else -> ""
        }
        return prefix + suffix
    }

    override fun getDisplayInfo(cursor: Cursor): String {
        val playCount = cursor.getInt(Plays.SUM_QUANTITY)
        return context.resources.getQuantityString(R.plurals.plays_suffix, playCount, playCount)
    }
}
