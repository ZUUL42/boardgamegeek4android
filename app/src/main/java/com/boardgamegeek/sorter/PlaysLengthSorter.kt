package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.support.annotation.StringRes

import com.boardgamegeek.R
import com.boardgamegeek.extensions.getInt
import com.boardgamegeek.provider.BggContract.Plays
import java.text.NumberFormat

class PlaysLengthSorter(context: Context) : PlaysSorter(context) {
    private val numberFormat = NumberFormat.getNumberInstance()
    private val noLength = context.getString(R.string.no_length)
    private val hoursSuffix = context.getString(R.string.hours_abbr)
    private val minutesSuffix = context.getString(R.string.minutes_abbr)

    override val descriptionId: Int
        @StringRes
        get() = R.string.menu_plays_sort_length

    override val type: Int
        get() = PlaysSorterFactory.TYPE_PLAY_LENGTH

    override val sortColumn: String
        get() = Plays.LENGTH

    override val isSortDescending: Boolean
        get() = true

    public override fun getHeaderText(cursor: Cursor): String {
        val minutes = cursor.getInt(Plays.LENGTH)
        return when {
            minutes == 0 -> noLength
            minutes >= 120 -> numberFormat.format(minutes / 60) + AND_MORE_SUFFIX + hoursSuffix
            minutes >= 60 -> numberFormat.format(minutes / 10 * 10) + AND_MORE_SUFFIX + minutesSuffix
            minutes >= 30 -> numberFormat.format(minutes / 5 * 5) + AND_MORE_SUFFIX + minutesSuffix
            else -> numberFormat.format(minutes) + minutesSuffix
        }
    }

    companion object {
        private const val AND_MORE_SUFFIX = "+ "
    }
}
