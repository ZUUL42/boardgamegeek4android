package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.support.annotation.StringRes

import com.boardgamegeek.R
import com.boardgamegeek.extensions.getFirstChar
import com.boardgamegeek.provider.BggContract.Plays

class LocationsNameSorter(context: Context) : LocationsSorter(context) {

    override val descriptionId: Int
        @StringRes
        get() = R.string.menu_sort_name

    override val type: Int
        get() = LocationsSorterFactory.TYPE_NAME

    override val columns: Array<String>
        get() = arrayOf(Plays.LOCATION)

    public override fun getHeaderText(cursor: Cursor): String {
        return cursor.getFirstChar(Plays.LOCATION)
    }
}
