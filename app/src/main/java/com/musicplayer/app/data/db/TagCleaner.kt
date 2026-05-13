package com.musicplayer.app.data.db

import android.util.Log

object TagCleaner {

    private val junkSuffixes = listOf(
        "(official audio)", "(official video)", "(official music video)",
        "(lyrics)", "(lyric video)", "(audio)", "(video)", "(hd)", "(4k)",
        "(visualizer)", "(official)", "[hd]", "[official]", "[lyrics]",
        "[audio]", "[video]", "(with lyrics)"
    )

    private val featuringPatterns = listOf(
        Regex("""\s*[\(\[]?ft\.?\s+[^\)\]]+[\)\]]?""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[]?feat\.?\s+[^\)\]]+[\)\]]?""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[]?featuring\s+[^\)\]]+[\)\]]?""", RegexOption.IGNORE_CASE)
    )

    private val trackNumberPattern = Regex("""^\d{1,3}[\.\s]*[-\s]+""")
    // Download site watermarks commonly found in Tamil MP3s
    private val watermarkPatterns = listOf(
        Regex("""\s*-?\s*MassTamilan\.dev""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*MassTamilan\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*Masstamilan""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*TamilWire\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*StarMusiq\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*Isaimini\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*Kuttyweb\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*TamilGun\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*Tamildada\.com""", RegexOption.IGNORE_CASE),
        Regex("""\s*-?\s*SenSongsMp3\.co""", RegexOption.IGNORE_CASE)
    )

    // Matches raw URI paths like "raw:/storage/emulated/0/Download/She & Him"
    private val rawUriPattern = Regex("""^raw:.*[/\\]""", RegexOption.IGNORE_CASE)

    data class CleanedTags(
        val title: String,
        val artist: String,
        val wasModified: Boolean
    )

    fun clean(rawTitle: String, rawArtist: String): CleanedTags {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()
        val originalTitle = title
        val originalArtist = artist

        // Step 0: Strip raw URI paths from title and artist
        // e.g. "raw:/storage/emulated/0/Download/She & Him" → "She & Him"
        if (title.startsWith("raw:", ignoreCase = true)) {
            title = rawUriPattern.replace(title, "")
                .removeSuffix(".mp3")
                .removeSuffix(".m4a")
                .removeSuffix(".aac")
                .removeSuffix(".flac")
                .removeSuffix(".ogg")
                .trim()
            Log.d("TagCleaner", "Stripped raw URI from title: $title")
        }
        if (artist.startsWith("raw:", ignoreCase = true)) {
            artist = rawUriPattern.replace(artist, "")
                .removeSuffix(".mp3")
                .removeSuffix(".m4a")
                .removeSuffix(".aac")
                .removeSuffix(".flac")
                .removeSuffix(".ogg")
                .trim()
            Log.d("TagCleaner", "Stripped raw URI from artist: $artist")
        }
        // Step 0.5: Strip download site watermarks from title and artist
        for (pattern in watermarkPatterns) {
            title = pattern.replace(title, "").trim()
            artist = pattern.replace(artist, "").trim()
        }
        // Step 1: If artist is unknown/empty, try to extract from title
        val isUnknownArtist = artist.isBlank()
                || artist.equals("unknown artist", ignoreCase = true)
                || artist.equals("unknown", ignoreCase = true)

        if (isUnknownArtist && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].length < 50) {
                artist = parts[0].trim()
                title = parts[1].trim()
                Log.d("TagCleaner", "Extracted artist '$artist' from title")
            }
        }

        // Step 2: Strip artist name prefix from title
        if (artist.isNotBlank() && title.startsWith("$artist - ", ignoreCase = true)) {
            title = title.removePrefix("$artist - ").trim()
            Log.d("TagCleaner", "Stripped artist prefix from title")
        }

        // Step 3: Strip leading track numbers
        title = trackNumberPattern.replace(title, "").trim()

        // Step 4: Strip featuring tags
        for (pattern in featuringPatterns) {
            title = pattern.replace(title, "").trim()
        }

        // Step 5: Strip junk suffixes
        for (suffix in junkSuffixes) {
            if (title.endsWith(suffix, ignoreCase = true)) {
                title = title.dropLast(suffix.length).trim()
            }
        }

        title = title.trimEnd('-', ' ', '.')

        val wasModified = title != originalTitle || artist != originalArtist
        if (wasModified) {
            Log.d("TagCleaner", "Cleaned: '$originalTitle'/'$originalArtist' → '$title'/'$artist'")
        }

        return CleanedTags(title = title, artist = artist, wasModified = wasModified)
    }
}