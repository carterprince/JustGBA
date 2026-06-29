package com.justgba.data

data class RecentRomEntry(
    val displayName: String,
    val uri: String,
    val lastPlayed: Long,
    val cachePath: String = "",
)
