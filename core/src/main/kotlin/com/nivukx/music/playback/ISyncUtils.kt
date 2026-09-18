package com.nivukx.music.playback

import com.nivukx.music.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
