package com.k2.music.song

/** Extracts directional hand-shape transitions in the exact practiced order. */
class SongTransitionExtractor(private val resolver: SongChordResolver) {
    fun orderedChords(
        project: SongProject,
        arrangement: SongArrangement,
        sectionId: String? = null,
    ): List<SongRenderedChord> {
        val renderedByEvent = arrangement.renderedChords.associateBy { it.eventId }
        val selectedSections = project.sections.sortedBy { it.order }.filter { sectionId == null || it.id == sectionId }
        return selectedSections.flatMap { section ->
            val onePass = section.rows.sortedBy { it.order }.flatMap { row ->
                row.chordEvents.sortedBy { it.order }.mapNotNull { renderedByEvent[it.id] }
            }
            buildList {
                repeat(section.repeatCount) { addAll(onePass) }
            }
        }
    }

    fun extract(
        project: SongProject,
        arrangement: SongArrangement,
        sectionId: String? = null,
        includeLoopBoundary: Boolean = false,
        unique: Boolean = false,
    ): List<SongTransition> {
        val chords = orderedChords(project, arrangement, sectionId)
        if (chords.size < 2) return emptyList()
        val pairs = chords.zipWithNext().toMutableList()
        if (includeLoopBoundary) pairs += chords.last() to chords.first()
        val transitions = pairs.mapNotNull { (from, to) ->
            val normalizedFrom = resolver.resolve(from.shapeChord)?.normalizedSymbol ?: return@mapNotNull null
            val normalizedTo = resolver.resolve(to.shapeChord)?.normalizedSymbol ?: return@mapNotNull null
            if (normalizedFrom == normalizedTo) null else SongTransition(from.shapeChord, to.shapeChord)
        }
        return if (unique) transitions.distinctBy { transition ->
            val from = resolver.resolve(transition.fromChord)?.normalizedSymbol ?: transition.fromChord
            val target = resolver.resolve(transition.toChord)?.normalizedSymbol ?: transition.toChord
            from to target
        } else transitions
    }

    fun equivalent(first: String, second: String): Boolean {
        val left = resolver.resolve(first)?.normalizedSymbol ?: return false
        val right = resolver.resolve(second)?.normalizedSymbol ?: return false
        return left == right
    }
}
