package com.k2.music.ui.model

/** Product-facing families. Every bundled chord formula belongs to exactly one family. */
enum class ChordFamily(
    val id: String,
    val label: String,
    val qualityIds: List<String>,
) {
    TRIAD("triad", "三和弦", listOf("maj", "m", "dim", "aug", "5")),
    SIXTH("sixth", "六和弦", listOf("6", "m6")),
    SEVENTH("seventh", "七和弦", listOf("7", "maj7", "m7", "mMaj7", "dim7", "m7b5", "maj7#5")),
    NINTH("ninth", "九和弦", listOf("9", "maj9", "m9", "mMaj9", "add9", "madd9", "7b9", "7#9", "maj7#9", "m7b9")),
    SUSPENDED("suspended", "挂留和弦", listOf("sus2", "sus4", "7sus2", "7sus4", "9sus4")),
    EXTENDED("extended", "延伸和弦", listOf("11", "maj11", "m11", "13", "maj13", "m13", "add11", "add13")),
    ALTERED("altered", "变化和弦", listOf("b5", "b9", "sharp9", "sharp11", "b13", "maj7#11", "7b5", "7#5", "7#11", "7b13", "7alt")),
    SLASH("slash", "斜杠和弦", emptyList());

    fun contains(qualityId: String): Boolean = qualityId in qualityIds

    companion object {
        fun fromId(id: String): ChordFamily? = entries.firstOrNull { it.id == id }

        fun fromQuality(qualityId: String): ChordFamily =
            entries.firstOrNull { it.contains(qualityId) }
                ?: error("未分类的和弦性质：$qualityId")
    }
}

enum class AccidentalPreference(val label: String) {
    SHARPS("♯"),
    FLATS("♭"),
}

val chromaticRoots: List<String> =
    listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private val sharpPitchNames = chromaticRoots
private val flatPitchNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val pitchClasses = buildMap {
    sharpPitchNames.forEachIndexed { index, note -> put(note, index) }
    flatPitchNames.forEachIndexed { index, note -> put(note, index) }
}

fun displayPitch(note: String, preference: AccidentalPreference): String {
    val normalized = note.replace("♯", "#").replace("♭", "b")
    val pitchClass = pitchClasses[normalized] ?: return note
    val value = if (preference == AccidentalPreference.FLATS) flatPitchNames[pitchClass] else sharpPitchNames[pitchClass]
    return value.replace("#", "♯").replace("b", "♭")
}

fun rootChoiceLabel(root: String): String = when (root) {
    "C#" -> "C♯/D♭"
    "D#" -> "D♯/E♭"
    "F#" -> "F♯/G♭"
    "G#" -> "G♯/A♭"
    "A#" -> "A♯/B♭"
    else -> root
}

fun ChordUiModel.displaySymbol(preference: AccidentalPreference): String {
    val mainSymbol = symbol.substringBefore('/')
    val suffix = mainSymbol.removePrefix(root)
    val bass = bassNote.takeIf { it.isNotBlank() }
    return buildString {
        append(displayPitch(root, preference))
        append(suffix)
        if (bass != null) {
            append('/')
            append(displayPitch(bass, preference))
        }
    }
}

val ChordUiModel.family: ChordFamily
    get() = if (bassNote.isNotBlank()) ChordFamily.SLASH else ChordFamily.fromQuality(qualityId)
