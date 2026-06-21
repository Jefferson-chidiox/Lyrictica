package com.oss.euphoriae.explore

enum class ExploreCategory {
    GENRES,
    MOODS,
    CHARTS
}

data class ExploreOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ExploreCategory,
    val audiusGenres: List<String> = emptyList(),
    val ncsGenres: List<String> = emptyList(),
    val chartKey: String? = null
)

data class ExploreSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val options: List<ExploreOption>
)

object ExploreCatalog {
    val sections: List<ExploreSection> = listOf(
        ExploreSection(
            id = "genres",
            title = "Genres",
            subtitle = "Jump into focused lanes built from Spinamp discovery and NCS creator-safe tracks.",
            options = listOf(
                ExploreOption(
                    id = "electronic",
                    title = "Electronic",
                    subtitle = "Wide-screen synths, club energy, and futuristic textures.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Electronic"),
                    ncsGenres = listOf("Electronic", "Electro House")
                ),
                ExploreOption(
                    id = "house",
                    title = "House",
                    subtitle = "Groove-led selections for bright dance floors and night drives.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Electronic"),
                    ncsGenres = listOf("House", "Deep House", "Future House")
                ),
                ExploreOption(
                    id = "pop",
                    title = "Pop",
                    subtitle = "Hooks first, polished vocals, and easy replay value.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Pop"),
                    ncsGenres = listOf("Pop", "Dance-Pop")
                ),
                ExploreOption(
                    id = "bass",
                    title = "Bass",
                    subtitle = "Punchy drops, heavier drums, and high-energy motion.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Electronic"),
                    ncsGenres = listOf("Future Bass", "Drum & Bass", "Dubstep")
                ),
                ExploreOption(
                    id = "hiphop",
                    title = "Hip-Hop",
                    subtitle = "Rhythmic cuts, darker swagger, and vocal-forward picks.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Hip-Hop/Rap"),
                    ncsGenres = listOf("Alternative Hip-Hop", "Trap")
                ),
                ExploreOption(
                    id = "chill",
                    title = "Chill",
                    subtitle = "Softer textures for study sessions, resets, and slow builds.",
                    category = ExploreCategory.GENRES,
                    audiusGenres = listOf("Electronic", "Pop"),
                    ncsGenres = listOf("Chill", "Lofi Hip-Hop")
                )
            )
        ),
        ExploreSection(
            id = "moods",
            title = "Moods",
            subtitle = "Curated blends that feel like scenes instead of just genres.",
            options = listOf(
                ExploreOption(
                    id = "focus",
                    title = "Focus Mode",
                    subtitle = "Low-friction tracks for concentration and calm momentum.",
                    category = ExploreCategory.MOODS,
                    audiusGenres = listOf("Electronic", "Pop"),
                    ncsGenres = listOf("Lofi Hip-Hop", "Chill")
                ),
                ExploreOption(
                    id = "energy",
                    title = "Energy Boost",
                    subtitle = "Fast lifts, clean drops, and more edge per minute.",
                    category = ExploreCategory.MOODS,
                    audiusGenres = listOf("Electronic"),
                    ncsGenres = listOf("Future Bass", "Drum & Bass")
                ),
                ExploreOption(
                    id = "night",
                    title = "Night Drive",
                    subtitle = "Shimmering movement for city lights and after-hours sessions.",
                    category = ExploreCategory.MOODS,
                    audiusGenres = listOf("Electronic"),
                    ncsGenres = listOf("House", "Deep House", "Garage")
                ),
                ExploreOption(
                    id = "dark",
                    title = "Dark Pulse",
                    subtitle = "Moodier tones with more tension and bass weight.",
                    category = ExploreCategory.MOODS,
                    audiusGenres = listOf("Hip-Hop/Rap", "Electronic"),
                    ncsGenres = listOf("Phonk", "Dubstep", "Trap")
                ),
                ExploreOption(
                    id = "uplifted",
                    title = "Uplifted",
                    subtitle = "Brighter melodies and optimistic release energy.",
                    category = ExploreCategory.MOODS,
                    audiusGenres = listOf("Pop", "Electronic"),
                    ncsGenres = listOf("Future Bass", "Dance-Pop", "Melodic House")
                )
            )
        ),
        ExploreSection(
            id = "charts",
            title = "Charts",
            subtitle = "Quick access to live remote lanes with both sources represented.",
            options = listOf(
                ExploreOption(
                    id = "momentum",
                    title = "Mixed Momentum",
                    subtitle = "Spinamp movers plus the freshest NCS rotations.",
                    category = ExploreCategory.CHARTS,
                    chartKey = "momentum"
                ),
                ExploreOption(
                    id = "fresh",
                    title = "Fresh Releases",
                    subtitle = "Recently surfaced online picks worth checking first.",
                    category = ExploreCategory.CHARTS,
                    chartKey = "fresh"
                ),
                ExploreOption(
                    id = "indie",
                    title = "Indie Signal",
                    subtitle = "Underground Spinamp energy backed by NCS creator-safe depth.",
                    category = ExploreCategory.CHARTS,
                    chartKey = "indie"
                ),
                ExploreOption(
                    id = "creator_safe",
                    title = "Creator Safe",
                    subtitle = "NCS-led picks paired with compatible Spinamp discovery.",
                    category = ExploreCategory.CHARTS,
                    chartKey = "creator_safe"
                )
            )
        )
    )

    fun findSection(sectionId: String): ExploreSection? =
        sections.firstOrNull { it.id == sectionId }

    fun findOption(sectionId: String, optionId: String): ExploreOption? =
        findSection(sectionId)?.options?.firstOrNull { it.id == optionId }
}
