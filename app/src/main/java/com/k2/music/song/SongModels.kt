package com.k2.music.song

import com.k2.music.MusicTheoryUtils
import java.util.UUID

object SongLimits {
    const val PROJECT_SCHEMA_VERSION = 2
    const val PARSER_VERSION = 1
    const val MAX_PROJECTS = 1_000
    const val MAX_ORIGINAL_TEXT_CHARS = 200_000
    const val MAX_SECTIONS = 200
    const val MAX_ROWS_PER_PROJECT = 5_000
    const val MAX_CHORD_EVENTS_PER_PROJECT = 20_000
    const val MAX_PRACTICE_RUNS = 100_000
    const val MAX_DIFFICULTIES = 100_000
}

enum class SongTimingState { UNTYPED, SIMPLE_MEASURES, EXPLICIT_BEATS }

enum class SongSectionType {
    INTRO,
    VERSE,
    PRE_CHORUS,
    CHORUS,
    BRIDGE,
    INTERLUDE,
    SOLO,
    OUTRO,
    CUSTOM,
}

enum class SongPracticeMode { GUIDED_TRANSITION, PERFORMANCE }

data class SongChordEvent(
    val id: String,
    val chordSymbol: String,
    val normalizedChordSymbol: String,
    val characterPosition: Int?,
    val durationBeats: Double?,
    val selectedVoicingId: String?,
    val measureIndex: Int?,
    val order: Int,
) {
    init {
        requireId(id, "和弦事件 ID")
        requireText(chordSymbol, "和弦", 64)
        requireText(normalizedChordSymbol, "规范和弦", 64)
        require(characterPosition == null || characterPosition >= 0) { "和弦字符位置不能为负数。" }
        require(durationBeats == null || (durationBeats.isFinite() && durationBeats > 0.0 && durationBeats <= 64.0)) {
            "和弦持续拍数必须大于 0 且不超过 64。"
        }
        optionalText(selectedVoicingId, "固定指法", 512)
        require(measureIndex == null || measureIndex >= 0) { "小节索引不能为负数。" }
        require(order >= 0) { "和弦事件顺序不能为负数。" }
    }

    companion object {
        fun create(
            chordSymbol: String,
            normalizedChordSymbol: String,
            characterPosition: Int? = null,
            durationBeats: Double? = null,
            selectedVoicingId: String? = null,
            measureIndex: Int? = null,
            order: Int = 0,
        ) = SongChordEvent(
            UUID.randomUUID().toString(),
            chordSymbol,
            normalizedChordSymbol,
            characterPosition,
            durationBeats,
            selectedVoicingId,
            measureIndex,
            order,
        )
    }
}

data class SongRow(
    val id: String,
    val lyricText: String,
    val rawChordText: String,
    val chordEvents: List<SongChordEvent>,
    val order: Int,
) {
    init {
        requireId(id, "曲谱行 ID")
        optionalText(lyricText, "歌词", 10_000)
        optionalText(rawChordText, "和弦原文", 10_000)
        require(chordEvents.size <= 1_000) { "单行和弦事件超过 1000 个。" }
        requireUniqueIds(chordEvents.map { it.id }, "和弦事件")
        requireUniqueOrders(chordEvents.map { it.order }, "和弦事件")
        require(order >= 0) { "曲谱行顺序不能为负数。" }
    }

    companion object {
        fun create(
            lyricText: String = "",
            rawChordText: String = "",
            chordEvents: List<SongChordEvent> = emptyList(),
            order: Int = 0,
        ) = SongRow(UUID.randomUUID().toString(), lyricText, rawChordText, chordEvents, order)
    }
}

data class SongSection(
    val id: String,
    val name: String,
    val type: SongSectionType,
    val order: Int,
    val repeatCount: Int,
    val rows: List<SongRow>,
) {
    init {
        requireId(id, "段落 ID")
        requireText(name, "段落名称", 120)
        require(order >= 0) { "段落顺序不能为负数。" }
        require(repeatCount in 1..99) { "段落重复次数必须在 1 到 99 之间。" }
        require(rows.size <= SongLimits.MAX_ROWS_PER_PROJECT) { "段落行数超过限制。" }
        requireUniqueIds(rows.map { it.id }, "曲谱行")
        requireUniqueOrders(rows.map { it.order }, "曲谱行")
    }

    companion object {
        fun create(
            name: String,
            type: SongSectionType = SongSectionType.CUSTOM,
            order: Int = 0,
            repeatCount: Int = 1,
            rows: List<SongRow> = emptyList(),
        ) = SongSection(UUID.randomUUID().toString(), name, type, order, repeatCount, rows)
    }
}

data class SongProject(
    val schemaVersion: Int = SongLimits.PROJECT_SCHEMA_VERSION,
    val parserVersion: Int = SongLimits.PARSER_VERSION,
    val id: String,
    val title: String,
    val artist: String,
    val originalText: String,
    val originalKey: String,
    val transposeSemitones: Int,
    val capoFret: Int,
    val bpm: Int,
    val timeSignature: String,
    val timingState: SongTimingState,
    val sections: List<SongSection>,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accidentalPreference: MusicTheoryUtils.AccidentalPreference = MusicTheoryUtils.AccidentalPreference.AUTO,
) {
    init {
        require(schemaVersion == SongLimits.PROJECT_SCHEMA_VERSION) { "不支持的曲谱 schema：$schemaVersion。" }
        require(parserVersion >= 1) { "解析器版本必须为正数。" }
        requireId(id, "曲谱 ID")
        requireText(title, "曲名", 200)
        optionalText(artist, "作者", 200)
        optionalText(originalText, "曲谱原文", SongLimits.MAX_ORIGINAL_TEXT_CHARS)
        optionalText(originalKey, "原调", 32)
        require(transposeSemitones in -11..11) { "移调必须在 -11 到 +11 半音之间。" }
        require(capoFret in 0..12) { "变调夹必须在 0 到 12 品之间。" }
        require(bpm in 40..240) { "BPM 必须在 40 到 240 之间。" }
        requireTimeSignature(timeSignature)
        require(sections.size <= SongLimits.MAX_SECTIONS) { "曲谱段落超过 ${SongLimits.MAX_SECTIONS} 个。" }
        requireUniqueIds(sections.map { it.id }, "曲谱段落")
        requireUniqueOrders(sections.map { it.order }, "曲谱段落")
        val rows = sections.sumOf { it.rows.size }
        val events = sections.sumOf { section -> section.rows.sumOf { it.chordEvents.size } }
        require(rows <= SongLimits.MAX_ROWS_PER_PROJECT) { "曲谱行数超过 ${SongLimits.MAX_ROWS_PER_PROJECT} 行。" }
        require(events <= SongLimits.MAX_CHORD_EVENTS_PER_PROJECT) {
            "和弦事件超过 ${SongLimits.MAX_CHORD_EVENTS_PER_PROJECT} 个。"
        }
        requireUniqueIds(sections.flatMap { it.rows }.map { it.id }, "曲谱行")
        requireUniqueIds(sections.flatMap { it.rows }.flatMap { it.chordEvents }.map { it.id }, "和弦事件")
        optionalText(notes, "曲谱备注", 20_000)
        require(createdAt >= 0 && updatedAt >= createdAt) { "曲谱时间戳无效。" }
        if (timingState == SongTimingState.EXPLICIT_BEATS) {
            require(events == 0 || sections.all { section ->
                section.rows.all { row -> row.chordEvents.all { it.durationBeats != null } }
            }) { "明确拍数模式要求每个和弦事件都有持续拍数。" }
        }
    }

    val chordEventCount: Int get() = sections.sumOf { section -> section.rows.sumOf { it.chordEvents.size } }
    val canUsePrecisePlayback: Boolean get() =
        timingState != SongTimingState.UNTYPED && chordEventCount > 0 &&
            sections.all { section -> section.rows.all { row -> row.chordEvents.all { it.durationBeats != null } } }

    companion object {
        fun create(
            title: String,
            artist: String = "",
            originalText: String = "",
            originalKey: String = "",
            transposeSemitones: Int = 0,
            capoFret: Int = 0,
            bpm: Int = 80,
            timeSignature: String = "4/4",
            timingState: SongTimingState = SongTimingState.UNTYPED,
            sections: List<SongSection> = emptyList(),
            notes: String = "",
            parserVersion: Int = SongLimits.PARSER_VERSION,
            accidentalPreference: MusicTheoryUtils.AccidentalPreference = MusicTheoryUtils.AccidentalPreference.AUTO,
            now: Long = System.currentTimeMillis(),
        ) = SongProject(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            originalText = originalText,
            originalKey = originalKey,
            transposeSemitones = transposeSemitones,
            capoFret = capoFret,
            bpm = bpm,
            timeSignature = timeSignature,
            timingState = timingState,
            sections = sections,
            notes = notes,
            parserVersion = parserVersion,
            createdAt = now,
            updatedAt = now,
            accidentalPreference = accidentalPreference,
        )
    }
}

data class SongTransition(val fromChord: String, val toChord: String) {
    init {
        requireText(fromChord, "来源和弦", 64)
        requireText(toChord, "目标和弦", 64)
    }
}

data class SongPracticeRun(
    val id: String,
    val songId: String,
    val sectionId: String?,
    val mode: SongPracticeMode,
    val bpm: Int,
    val transposeSemitones: Int,
    val capoFret: Int,
    val startedAt: Long,
    val endedAt: Long,
    val actualDurationSeconds: Int,
    val completed: Boolean,
    val reportedDifficultTransitions: List<SongTransition>,
    val loopEnabled: Boolean = true,
    val showFretboard: Boolean = true,
    val selectedVoicingIds: Map<String, String> = emptyMap(),
) {
    init {
        requireId(id, "练习记录 ID")
        requireId(songId, "曲谱 ID")
        optionalText(sectionId, "段落 ID", 128)
        require(bpm in 40..240) { "练习 BPM 必须在 40 到 240 之间。" }
        require(transposeSemitones in -11..11) { "练习移调超出范围。" }
        require(capoFret in 0..12) { "练习变调夹超出范围。" }
        require(startedAt >= 0 && endedAt >= startedAt) { "练习时间戳无效。" }
        require(actualDurationSeconds in 0..86_400) { "练习实际时长超出范围。" }
        require(reportedDifficultTransitions.size <= 1_000) { "单次练习困难切换过多。" }
        require(selectedVoicingIds.size <= SongLimits.MAX_CHORD_EVENTS_PER_PROJECT) { "练习固定指法快照数量过多。" }
        selectedVoicingIds.forEach { (eventId, voicingId) ->
            requireId(eventId, "和弦事件 ID")
            requireText(voicingId, "固定指法 ID", 512)
        }
    }

    companion object {
        fun create(
            songId: String,
            sectionId: String?,
            mode: SongPracticeMode,
            bpm: Int,
            transposeSemitones: Int,
            capoFret: Int,
            startedAt: Long,
            endedAt: Long,
            actualDurationSeconds: Int,
            completed: Boolean,
            reportedDifficultTransitions: List<SongTransition> = emptyList(),
            loopEnabled: Boolean = true,
            showFretboard: Boolean = true,
            selectedVoicingIds: Map<String, String> = emptyMap(),
        ) = SongPracticeRun(
            UUID.randomUUID().toString(), songId, sectionId, mode, bpm, transposeSemitones, capoFret,
            startedAt, endedAt, actualDurationSeconds, completed, reportedDifficultTransitions,
            loopEnabled, showFretboard,
            selectedVoicingIds,
        )
    }
}

data class UserReportedDifficulty(
    val id: String,
    val songId: String,
    val sectionId: String?,
    val fromChord: String,
    val toChord: String,
    val reportedAt: Long,
    val resolved: Boolean,
    val note: String,
) {
    init {
        requireId(id, "困难标记 ID")
        requireId(songId, "曲谱 ID")
        optionalText(sectionId, "段落 ID", 128)
        requireText(fromChord, "来源和弦", 64)
        requireText(toChord, "目标和弦", 64)
        require(reportedAt >= 0) { "困难标记时间不能为负数。" }
        optionalText(note, "困难标记备注", 2_000)
    }

    companion object {
        fun create(
            songId: String,
            sectionId: String?,
            fromChord: String,
            toChord: String,
            reportedAt: Long = System.currentTimeMillis(),
            note: String = "",
        ) = UserReportedDifficulty(
            UUID.randomUUID().toString(), songId, sectionId, fromChord, toChord,
            reportedAt, false, note,
        )
    }
}

internal fun requireId(value: String, label: String) = requireText(value, label, 128)

internal fun requireText(value: String?, label: String, maxLength: Int): String {
    val normalized = value?.trim().orEmpty()
    require(normalized.isNotEmpty()) { "$label 不能为空。" }
    require(normalized.length <= maxLength) { "$label 超过长度限制。" }
    return normalized
}

internal fun optionalText(value: String?, label: String, maxLength: Int): String {
    val normalized = value?.trim().orEmpty()
    require(normalized.length <= maxLength) { "$label 超过长度限制。" }
    return normalized
}

internal fun requireUniqueIds(values: List<String>, label: String) {
    require(values.size == values.toSet().size) { "$label ID 重复。" }
}

internal fun requireUniqueOrders(values: List<Int>, label: String) {
    require(values.size == values.toSet().size) { "${label}顺序重复。" }
}

internal fun requireTimeSignature(value: String) {
    val parts = value.trim().split('/')
    val numerator = parts.getOrNull(0)?.toIntOrNull()
    val denominator = parts.getOrNull(1)?.toIntOrNull()
    require(parts.size == 2 && numerator in 1..32 && denominator in setOf(2, 4, 8, 16)) { "拍号无效。" }
}
