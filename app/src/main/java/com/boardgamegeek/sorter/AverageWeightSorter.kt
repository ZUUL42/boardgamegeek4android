package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.support.annotation.StringRes

import com.boardgamegeek.R
import com.boardgamegeek.extensions.getDoubleAsString
import com.boardgamegeek.provider.BggContract.Collection

import java.text.DecimalFormat

abstract class AverageWeightSorter(context: Context) : CollectionSorter(context) {
    private val displayFormat = DecimalFormat("0.000")
    private val defaultValue = context.getString(R.string.text_unknown)

    override val descriptionId: Int
        @StringRes
        get() = R.string.collection_sort_average_weight

    override val sortColumn: String
        get() = Collection.STATS_AVERAGE_WEIGHT

    override fun getDisplayInfo(cursor: Cursor): String {
        val info = cursor.getDoubleAsString(Collection.STATS_AVERAGE_WEIGHT, defaultValue, format = displayFormat)
        return "${context.getString(R.string.weight)} $info"
    }

    public override fun getHeaderText(cursor: Cursor): String {
        return cursor.getDoubleAsString(Collection.STATS_AVERAGE_WEIGHT, defaultValue)
    }
}
