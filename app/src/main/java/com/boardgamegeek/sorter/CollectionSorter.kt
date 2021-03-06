package com.boardgamegeek.sorter

import android.content.Context
import android.database.Cursor
import android.support.annotation.StringRes
import com.boardgamegeek.provider.BggContract.Collection

abstract class CollectionSorter(context: Context) : Sorter(context) {

    /**
     * {@inheritDoc}
     */
    override val description: String
        get() {
            var description = super.description
            if (subDescriptionId > 0) {
                description += " - " + context.getString(subDescriptionId)
            }
            return description
        }

    protected open val subDescriptionId: Int
        @StringRes
        get() = 0

    override val type: Int
        get() = context.getString(typeResource).toIntOrNull() ?: CollectionSorterFactory.TYPE_DEFAULT

    @get:StringRes
    protected abstract val typeResource: Int

    override val defaultSort: String
        get() = Collection.DEFAULT_SORT

    /**
     * Gets the detail text to display on each row.
     */
    open fun getDisplayInfo(cursor: Cursor) = getHeaderText(cursor)

    open fun getTimestamp(cursor: Cursor) = 0L
}
