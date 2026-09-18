

package com.nivukx.music.models

import com.music.innertube.models.YTItem
import com.nivukx.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
