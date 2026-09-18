

package com.nivukx.music.models

import com.nivukx.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
