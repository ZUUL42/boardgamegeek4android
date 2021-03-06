package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.text.format.DateUtils
import com.boardgamegeek.R
import com.boardgamegeek.extensions.getApiTime
import com.boardgamegeek.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.*

abstract class CollectionDateSorter(context: Context) : CollectionSorter(context) {
    override val isSortDescending: Boolean
        get() = true

    public override fun getHeaderText(cursor: Cursor): String {
        val time = cursor.getApiTime(sortColumn)
        return if (time == DateTimeUtils.UNKNOWN_DATE) defaultValue else DISPLAY_FORMAT.format(time)
    }

    override fun getDisplayInfo(cursor: Cursor): String {
        val time = cursor.getApiTime(sortColumn)
        return if (time == DateTimeUtils.UNKNOWN_DATE) defaultValue
        else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    }

    protected open val defaultValue: String
        get() = context.getString(R.string.text_unknown)

    companion object {
        private val DISPLAY_FORMAT = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    }
}
