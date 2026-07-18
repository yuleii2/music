package com.k2.music.song

import com.k2.music.ChordRepository
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max

data class ResolvedChord(
    val displaySymbol: String,
    val normalizedSymbol: String,
)

fun interface SongChordResolver {
    fun resolve(rawToken: String): ResolvedChord?
}

class RepositorySongChordResolver(private val repository: ChordRepository) : SongChordResolver {
    override fun resolve(rawToken: String): ResolvedChord? {
        val parsed = repository.nameParser.parse(rawToken)
        if (!parsed.recognized || !repository.find(rawToken).recognized) return null
        return ResolvedChord(parsed.displaySymbol, parsed.normalizedSymbol)
    }
}

enum class SongParseLineRole { AUTO, CHORDS, LYRICS, SECTION_TITLE, TEXT }

data class SongParseWarning(
    val code: String,
    val message: String,
    val lineNumber: Int?,
)

data class SongSheetParseResult(
    val originalText: String,
    val detectedTitle: String?,
    val sections: List<SongSection>,
    val validChords: List<String>,
    val unrecognizedTokens: List<String>,
    val warnings: List<SongParseWarning>,
    val confidence: Double,
    val timingState: SongTimingState,
    val parserVersion: Int,
) {
    val chordEventCount: Int = sections.sumOf { section -> section.rows.sumOf { it.chordEvents.size } }
    val canStartPractice: Boolean get() = chordEventCount >= 2
}

class SongParseException(message: String) : IllegalArgumentException(message)

class SongSheetParser(private val resolver: SongChordResolver) {
    fun parse(
        originalText: String,
        timeSignature: String = "4/4",
        lineOverrides: Map<Int, SongParseLineRole> = emptyMap(),
    ): SongSheetParseResult {
        if (originalText.length > SongLimits.MAX_ORIGINAL_TEXT_CHARS) {
            throw SongParseException("曲谱原文超过 ${SongLimits.MAX_ORIGINAL_TEXT_CHARS} 个字符，请拆分后再导入。")
        }
        requireTimeSignature(timeSignature)
        val lines = originalText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.size > SongLimits.MAX_ROWS_PER_PROJECT * 2) {
            throw SongParseException("曲谱原文行数超过 ${SongLimits.MAX_ROWS_PER_PROJECT * 2} 行。")
        }
        if (originalText.isBlank()) {
            return SongSheetParseResult(
                originalText,
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(SongParseWarning("EMPTY_TEXT", "请先粘贴曲谱原文。", null)),
                0.0,
                SongTimingState.UNTYPED,
                PARSER_VERSION,
            )
        }

        val warnings = mutableListOf<SongParseWarning>()
        val unrecognized = linkedSetOf<String>()
        val valid = linkedSetOf<String>()
        val sections = mutableListOf<MutableSection>()
        var current: MutableSection? = null
        var detectedTitle: String? = null
        var usedMeasures = false
        var allEventsTimed = true
        var contentLines = 0
        var confidentLines = 0

        fun ensureSection(lineIndex: Int): MutableSection {
            current?.let { return it }
            return MutableSection(
                stableId("section", "default|$lineIndex"),
                "未分段",
                SongSectionType.CUSTOM,
                sections.size,
            ).also {
                sections += it
                current = it
            }
        }

        fun beginSection(name: String, type: SongSectionType, lineIndex: Int) {
            if (sections.size >= SongLimits.MAX_SECTIONS) {
                throw SongParseException("曲谱段落超过 ${SongLimits.MAX_SECTIONS} 个。")
            }
            current = MutableSection(
                stableId("section", "$lineIndex|$name"),
                name.trim(),
                type,
                sections.size,
            ).also(sections::add)
        }

        var index = 0
        while (index < lines.size) {
            val rawLine = lines[index]
            val trimmed = rawLine.trim()
            val lineNumber = index + 1
            val override = lineOverrides[lineNumber] ?: SongParseLineRole.AUTO
            if (trimmed.isEmpty()) {
                index++
                continue
            }

            if (override == SongParseLineRole.SECTION_TITLE) {
                beginSection(stripHeadingBrackets(trimmed), SongSectionType.CUSTOM, index)
                confidentLines++
                contentLines++
                index++
                continue
            }
            if (override == SongParseLineRole.LYRICS || override == SongParseLineRole.TEXT) {
                val target = ensureSection(index)
                target.rows += SongRow(
                    stableId("row", "$index|text|$rawLine"),
                    rawLine,
                    "",
                    emptyList(),
                    target.rows.size,
                )
                contentLines++
                confidentLines++
                index++
                continue
            }

            val title = TITLE_PATTERN.matchEntire(trimmed)?.groupValues?.get(2)?.trim()
            if (title != null && override == SongParseLineRole.AUTO) {
                detectedTitle = title.takeIf { it.isNotBlank() }
                index++
                continue
            }

            val heading = if (override == SongParseLineRole.AUTO) detectSectionHeading(trimmed) else null
            if (heading != null) {
                beginSection(heading.first, heading.second, index)
                contentLines++
                confidentLines++
                index++
                continue
            }

            val inline = if (override == SongParseLineRole.AUTO) parseInlineChordLine(rawLine, index) else null
            if (inline != null) {
                val target = ensureSection(index)
                target.rows += inline.row.copy(order = target.rows.size)
                inline.valid.forEach { valid += it }
                inline.unrecognized.forEach { token ->
                    unrecognized += token
                    warnings += SongParseWarning(
                        "UNRECOGNIZED_CHORD",
                        "无法识别和弦“$token”，请在预览中修正。",
                        lineNumber,
                    )
                }
                allEventsTimed = false
                contentLines++
                if (inline.unrecognized.isEmpty()) confidentLines++
                index++
                continue
            }

            if (override == SongParseLineRole.CHORDS || trimmed.contains('|')) {
                val parsedMeasures = parseMeasureOrChordLine(rawLine, index, timeSignature, force = true)
                if (parsedMeasures != null) {
                    val target = ensureSection(index)
                    target.rows += parsedMeasures.row.copy(order = target.rows.size)
                    parsedMeasures.valid.forEach { valid += it }
                    parsedMeasures.unrecognized.forEach { token ->
                        unrecognized += token
                        warnings += SongParseWarning(
                            "UNRECOGNIZED_CHORD",
                            "无法识别和弦“$token”，请在预览中修正。",
                            lineNumber,
                        )
                    }
                    usedMeasures = usedMeasures || parsedMeasures.usedMeasures
                    allEventsTimed = allEventsTimed && parsedMeasures.row.chordEvents.all { it.durationBeats != null }
                    contentLines++
                    if (!parsedMeasures.lowConfidence && parsedMeasures.unrecognized.isEmpty()) confidentLines++
                    if (parsedMeasures.lowConfidence) {
                        warnings += SongParseWarning(
                            "LOW_CONFIDENCE_CHORD_LINE",
                            "第 $lineNumber 行可能是和弦，也可能是普通文字，请确认。",
                            lineNumber,
                        )
                    }
                    index++
                    continue
                }
            }

            val chordLine = classifyChordLine(rawLine)
            if (chordLine.kind != ChordLineKind.NONE) {
                val parsed = parseMeasureOrChordLine(rawLine, index, timeSignature, force = false)
                if (parsed != null) {
                    var lyric = ""
                    val nextIndex = index + 1
                    if (nextIndex < lines.size && lines[nextIndex].isNotBlank() &&
                        detectSectionHeading(lines[nextIndex].trim()) == null &&
                        parseInlineChordLine(lines[nextIndex], nextIndex) == null &&
                        classifyChordLine(lines[nextIndex]).kind == ChordLineKind.NONE
                    ) {
                        lyric = lines[nextIndex]
                        index++
                    }
                    val target = ensureSection(index)
                    target.rows += parsed.row.copy(
                        lyricText = lyric,
                        order = target.rows.size,
                    )
                    parsed.valid.forEach { valid += it }
                    parsed.unrecognized.forEach { token ->
                        unrecognized += token
                        warnings += SongParseWarning(
                            "UNRECOGNIZED_CHORD",
                            "无法识别和弦“$token”，请在预览中修正。",
                            lineNumber,
                        )
                    }
                    allEventsTimed = false
                    contentLines++
                    if (chordLine.kind == ChordLineKind.HIGH && parsed.unrecognized.isEmpty()) confidentLines++
                    if (chordLine.kind == ChordLineKind.LOW) {
                        warnings += SongParseWarning(
                            "LOW_CONFIDENCE_CHORD_LINE",
                            "第 $lineNumber 行的和弦识别置信度较低，请确认。",
                            lineNumber,
                        )
                    }
                    index++
                    continue
                }
            }

            val bracketUnknowns = BRACKET_PATTERN.findAll(rawLine)
                .map { it.groupValues[1].ifBlank { it.groupValues[2] }.trim() }
                .filter(::looksChordLike)
                .filter { resolver.resolve(it) == null }
                .toList()
            bracketUnknowns.forEach { token ->
                unrecognized += token
                warnings += SongParseWarning(
                    "UNRECOGNIZED_CHORD",
                    "无法识别和弦“$token”，本行暂按普通文字保留。",
                    lineNumber,
                )
            }
            val target = ensureSection(index)
            target.rows += SongRow(
                stableId("row", "$index|lyric|$rawLine"),
                rawLine,
                "",
                emptyList(),
                target.rows.size,
            )
            contentLines++
            if (bracketUnknowns.isEmpty()) confidentLines++
            index++
        }

        val immutableSections = sections.map { it.toSongSection() }
        val eventCount = immutableSections.sumOf { section -> section.rows.sumOf { it.chordEvents.size } }
        if (eventCount > SongLimits.MAX_CHORD_EVENTS_PER_PROJECT) {
            throw SongParseException("和弦事件超过 ${SongLimits.MAX_CHORD_EVENTS_PER_PROJECT} 个。")
        }
        if (eventCount == 0) {
            warnings += SongParseWarning("NO_CHORDS", "没有识别到有效和弦，原文仍会保留。", null)
        }
        val timing = if (usedMeasures && allEventsTimed && eventCount > 0) {
            SongTimingState.SIMPLE_MEASURES
        } else {
            SongTimingState.UNTYPED
        }
        val tokenConfidence = if (valid.isEmpty() && unrecognized.isEmpty()) 0.0 else
            valid.size.toDouble() / (valid.size + unrecognized.size)
        val lineConfidence = if (contentLines == 0) 0.0 else confidentLines.toDouble() / contentLines
        val confidence = when {
            eventCount == 0 -> 0.0
            else -> (0.65 * tokenConfidence + 0.35 * lineConfidence).coerceIn(0.0, 1.0)
        }
        return SongSheetParseResult(
            originalText,
            detectedTitle,
            immutableSections,
            valid.toList(),
            unrecognized.toList(),
            warnings,
            confidence,
            timing,
            PARSER_VERSION,
        )
    }

    private fun parseInlineChordLine(rawLine: String, lineIndex: Int): ParsedLine? {
        val matches = BRACKET_PATTERN.findAll(rawLine).toList()
        if (matches.isEmpty()) return null
        val hasChordCandidate = matches.any { match ->
            val token = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
            resolver.resolve(token) != null || looksChordLike(token)
        }
        if (!hasChordCandidate) return null
        val events = mutableListOf<SongChordEvent>()
        val valid = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        matches.forEach { match ->
            val token = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
            val resolved = resolver.resolve(token)
            when {
                resolved != null -> {
                    valid += resolved.displaySymbol
                    events += SongChordEvent(
                        stableId("event", "$lineIndex|${match.range.first}|${resolved.normalizedSymbol}"),
                        resolved.displaySymbol,
                        resolved.normalizedSymbol,
                        match.range.first,
                        null,
                        null,
                        null,
                        events.size,
                    )
                }
                looksChordLike(token) -> unknown += token
            }
        }
        if (events.isEmpty() && unknown.isEmpty()) return null
        val lyric = BRACKET_PATTERN.replace(rawLine, "").trim()
        return ParsedLine(
            SongRow(stableId("row", "$lineIndex|inline|$rawLine"), lyric, rawLine, events, 0),
            valid,
            unknown,
            usedMeasures = false,
            lowConfidence = unknown.isNotEmpty(),
        )
    }

    private fun parseMeasureOrChordLine(
        rawLine: String,
        lineIndex: Int,
        timeSignature: String,
        force: Boolean,
    ): ParsedLine? {
        val hasBars = rawLine.contains('|')
        val classification = classifyChordLine(rawLine)
        if (!force && classification.kind == ChordLineKind.NONE) return null
        val measureSegments = if (hasBars) {
            rawLine.split('|').map { it.trim() }.filter { it.isNotEmpty() && it != ":" }
        } else {
            listOf(rawLine.trim())
        }
        val events = mutableListOf<SongChordEvent>()
        val valid = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        measureSegments.forEachIndexed { measureIndex, segment ->
            val rawTokens = TOKEN_PATTERN.findAll(segment)
                .map { it.value.trim().trim(',', ';', '(', ')') }
                .filter { it.isNotEmpty() && it !in setOf(":", "||", "|:", ":|") }
                .toList()
            val durations = if (hasBars) SongTimingRules.inferMeasureDurations(rawTokens.size, timeSignature) else emptyList()
            rawTokens.forEachIndexed { tokenIndex, token ->
                val resolved = resolver.resolve(token)
                if (resolved != null) {
                    valid += resolved.displaySymbol
                    events += SongChordEvent(
                        stableId("event", "$lineIndex|$measureIndex|$tokenIndex|${resolved.normalizedSymbol}"),
                        resolved.displaySymbol,
                        resolved.normalizedSymbol,
                        rawLine.indexOf(token).takeIf { it >= 0 },
                        durations.getOrNull(tokenIndex),
                        null,
                        measureIndex.takeIf { hasBars },
                        events.size,
                    )
                } else if (looksChordLike(token)) {
                    unknown += token
                }
            }
        }
        if (events.isEmpty() && unknown.isEmpty()) return null
        return ParsedLine(
            SongRow(stableId("row", "$lineIndex|chords|$rawLine"), "", rawLine, events, 0),
            valid,
            unknown,
            usedMeasures = hasBars,
            lowConfidence = classification.kind == ChordLineKind.LOW || unknown.isNotEmpty(),
        )
    }

    private fun classifyChordLine(rawLine: String): ChordLineClassification {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return ChordLineClassification(ChordLineKind.NONE, 0, 0)
        val tokens = TOKEN_PATTERN.findAll(trimmed)
            .map { it.value.trim().trim(',', ';', '(', ')') }
            .filter { it.isNotEmpty() && it !in setOf(":", "||", "|:", ":|") }
            .toList()
        if (tokens.isEmpty() || tokens.size > 64) return ChordLineClassification(ChordLineKind.NONE, 0, tokens.size)
        val validCount = tokens.count { resolver.resolve(it) != null }
        val chordLikeCount = tokens.count(::looksChordLike)
        val naturalWords = tokens.count { NATURAL_WORD_PATTERN.matches(it) && resolver.resolve(it) == null }
        val ratio = validCount.toDouble() / tokens.size
        val hasMeasureSyntax = trimmed.contains('|') || trimmed.contains(":|") || trimmed.contains("|:")
        val hasAlignedSpaces = Regex("\\s{2,}").containsMatchIn(rawLine)
        val high = validCount >= 1 && ratio >= 0.8 && naturalWords == 0 &&
            (tokens.size >= 2 || hasMeasureSyntax || hasAlignedSpaces)
        val low = validCount >= 2 && ratio >= 0.5 && chordLikeCount == tokens.size
        return ChordLineClassification(
            when {
                high -> ChordLineKind.HIGH
                low -> ChordLineKind.LOW
                else -> ChordLineKind.NONE
            },
            validCount,
            tokens.size,
        )
    }

    companion object {
        const val PARSER_VERSION = 1
        private val BRACKET_PATTERN = Regex("\\[([^]]+)]|【([^】]+)】")
        private val TOKEN_PATTERN = Regex("[^\\s|]+")
        private val TITLE_PATTERN = Regex("(?i)^(title|song|曲名)\\s*[:：]\\s*(.+)$")
        private val NATURAL_WORD_PATTERN = Regex("[A-Za-z]{2,}")
        private val CHORD_LIKE_PATTERN = Regex("(?i)^[A-H](?:#|b|♯|♭)?[^\\s]{0,18}(?:/[A-G](?:#|b|♯|♭)?)?$")
    }

    private data class ParsedLine(
        val row: SongRow,
        val valid: List<String>,
        val unrecognized: List<String>,
        val usedMeasures: Boolean,
        val lowConfidence: Boolean,
    )

    private data class MutableSection(
        val id: String,
        val name: String,
        val type: SongSectionType,
        val order: Int,
        val rows: MutableList<SongRow> = mutableListOf(),
    ) {
        fun toSongSection() = SongSection(id, name, type, order, 1, rows.toList())
    }

    private enum class ChordLineKind { NONE, LOW, HIGH }
    private data class ChordLineClassification(val kind: ChordLineKind, val valid: Int, val total: Int)
}

object SongTimingRules {
    fun inferMeasureDurations(chordCount: Int, timeSignature: String): List<Double> {
        if (chordCount <= 0) return emptyList()
        requireTimeSignature(timeSignature)
        val numerator = timeSignature.substringBefore('/').toInt()
        val duration = numerator.toDouble() / chordCount
        return List(chordCount) { duration }
    }

    fun withExplicitDuration(project: SongProject, eventId: String, durationBeats: Double, now: Long): SongProject {
        require(durationBeats.isFinite() && durationBeats > 0.0 && durationBeats <= 64.0) { "持续拍数无效。" }
        var found = false
        val sections = project.sections.map { section ->
            section.copy(rows = section.rows.map { row ->
                row.copy(chordEvents = row.chordEvents.map { event ->
                    if (event.id == eventId) {
                        found = true
                        event.copy(durationBeats = durationBeats)
                    } else event
                })
            })
        }
        require(found) { "找不到要修改的和弦事件。" }
        val allTimed = sections.all { section -> section.rows.all { row -> row.chordEvents.all { it.durationBeats != null } } }
        return project.copy(
            sections = sections,
            timingState = if (allTimed) SongTimingState.EXPLICIT_BEATS else project.timingState,
            updatedAt = max(project.updatedAt, now),
        )
    }
}

private fun detectSectionHeading(line: String): Pair<String, SongSectionType>? {
    val trimmed = line.trim()
    val bracketed = (trimmed.startsWith('[') && trimmed.endsWith(']')) ||
        (trimmed.startsWith('【') && trimmed.endsWith('】'))
    val name = if (bracketed) stripHeadingBrackets(trimmed) else trimmed
    val normalized = name.lowercase().replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    val type = when {
        normalized in setOf("前奏", "intro") -> SongSectionType.INTRO
        normalized == "主歌" || Regex("^verse(?: \\d+)?$").matches(normalized) -> SongSectionType.VERSE
        normalized in setOf("前副歌", "预副歌") || Regex("^pre[ -]?chorus(?: \\d+)?$").matches(normalized) -> SongSectionType.PRE_CHORUS
        normalized == "副歌" || Regex("^chorus(?: \\d+)?$").matches(normalized) -> SongSectionType.CHORUS
        normalized in setOf("桥段", "桥") || Regex("^bridge(?: \\d+)?$").matches(normalized) -> SongSectionType.BRIDGE
        normalized == "间奏" || Regex("^interlude(?: \\d+)?$").matches(normalized) -> SongSectionType.INTERLUDE
        normalized == "solo" || normalized == "独奏" -> SongSectionType.SOLO
        normalized in setOf("尾奏", "outro") -> SongSectionType.OUTRO
        bracketed && !looksChordLike(name) -> SongSectionType.CUSTOM
        else -> return null
    }
    return name to type
}

private fun stripHeadingBrackets(value: String): String = value.trim()
    .removePrefix("[").removeSuffix("]")
    .removePrefix("【").removeSuffix("】")
    .trim()

private fun looksChordLike(token: String): Boolean =
    Regex("(?i)^[A-H](?:#|b|♯|♭)?[^\\s]{0,18}(?:/[A-G](?:#|b|♯|♭)?)?$").matches(token.trim())

private fun stableId(kind: String, seed: String): String = UUID.nameUUIDFromBytes(
    "song-parser-v${SongSheetParser.PARSER_VERSION}|$kind|$seed".toByteArray(StandardCharsets.UTF_8),
).toString()
