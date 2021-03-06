package com.boardgamegeek.sorter

import android.content.Context
import android.support.annotation.StringRes

import com.boardgamegeek.R
import com.boardgamegeek.provider.BggContract.Collection

import java.text.DecimalFormat

class GeekRatingSorter(context: Context) : RatingSorter(context) {
    private val format = DecimalFormat("0.000")

    override val descriptionId: Int
        @StringRes
        get() = R.string.collection_sort_geek_rating

    public override val typeResource: Int
        @StringRes
        get() = R.string.collection_sort_type_geek_rating

    override val sortColumn: String
        get() = Collection.STATS_BAYES_AVERAGE

    override val displayFormat: DecimalFormat
        get() = format
}
