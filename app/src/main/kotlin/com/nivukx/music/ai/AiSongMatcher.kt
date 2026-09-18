package com.nivukx.music.ai

import com.music.innertube.models.SongItem

object AiSongMatcher {
    data class Request(
        val title: String,
        val artist: String,
    )

    fun bestMatch(
        request: Request,
        candidates: List<Any>,
    ): SongItem? {
        val songs = candidates.filterIsInstance<SongItem>()
        if (songs.isEmpty()) return null

        val targetTitle = normalize(request.title)
        val targetArtist = normalize(request.artist)
        if (targetTitle.isBlank()) return null

        return songs
            .map { song ->
                val candidateTitle = normalize(song.title)
                val candidateArtist = normalize(song.artists.joinToString(" "))
                var score = 0

                score += titleScore(targetTitle, candidateTitle)
                if (targetArtist.isNotBlank()) {
                    score += artistScore(targetArtist, candidateArtist)
                }

                song to score
            }
            .filter { (_, score) -> score >= MIN_ACCEPT_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun titleScore(target: String, candidate: String): Int = when {
        target == candidate -> 100
        candidate.contains(target) || target.contains(candidate) -> 70
        tokenOverlap(target, candidate) >= 0.8f -> 60
        tokenOverlap(target, candidate) >= 0.5f -> 35
        else -> 0
    }

    private fun artistScore(target: String, candidate: String): Int = when {
        target == candidate -> 70
        candidate.contains(target) || target.contains(candidate) -> 45
        tokenOverlap(target, candidate) >= 0.7f -> 30
        tokenOverlap(target, candidate) >= 0.4f -> 15
        else -> 0
    }

    private fun tokenOverlap(a: String, b: String): Float {
        val left = a.split(' ').filter(String::isNotBlank).toSet()
        val right = b.split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / maxOf(left.size, right.size)
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private const val MIN_ACCEPT_SCORE = 100
}
