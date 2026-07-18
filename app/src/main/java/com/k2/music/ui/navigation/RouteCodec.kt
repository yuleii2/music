package com.k2.music.ui.navigation

import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.ExportScopeUi
import java.io.ByteArrayOutputStream

const val CHORD_DETAIL_PATTERN = "chord-detail?symbol={symbol}"

fun chordDetailRoute(symbol: String): String = "chord-detail?symbol=${encodeRouteValue(symbol)}"

const val SONG_DETAIL_PATTERN = "song-detail?id={id}"
const val SONG_EDITOR_PATTERN = "song-editor?id={id}"
const val SONG_PRACTICE_PATTERN =
    "song-practice?id={id}&sectionId={sectionId}&restoreBpm={restoreBpm}&restoreTranspose={restoreTranspose}&restoreCapo={restoreCapo}&restoreLoop={restoreLoop}&restoreFretboard={restoreFretboard}"

fun songDetailRoute(id: String): String = "song-detail?id=${encodeRouteValue(id)}"

fun songEditorRoute(id: String): String = "song-editor?id=${encodeRouteValue(id)}"

fun songPracticeRoute(
    id: String,
    sectionId: String?,
    bpm: Int? = null,
    transposeSemitones: Int? = null,
    capoFret: Int? = null,
    loopEnabled: Boolean? = null,
    showFretboard: Boolean? = null,
): String = "song-practice?id=${encodeRouteValue(id)}" +
    "&sectionId=${encodeRouteValue(sectionId.orEmpty())}" +
    "&restoreBpm=${bpm ?: 0}" +
    "&restoreTranspose=${transposeSemitones ?: 99}" +
    "&restoreCapo=${capoFret ?: -1}" +
    "&restoreLoop=${loopEnabled?.let { if (it) 1 else 0 } ?: -1}" +
    "&restoreFretboard=${showFretboard?.let { if (it) 1 else 0 } ?: -1}"

const val PROGRESSION_EDITOR_PATTERN = "progression-editor?id={id}&seed={seed}"

fun progressionEditorRoute(seed: String): String =
    "progression-editor?id=&seed=${encodeRouteValue(seed)}"

fun progressionEditorByIdRoute(id: String): String =
    "progression-editor?id=${encodeRouteValue(id)}&seed="

const val PRACTICE_SETUP_PATTERN =
    "practice-setup?mode={mode}&symbols={symbols}&duration={duration}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}&progressionId={progressionId}&progressionRhythm={progressionRhythm}&songId={songId}&songSectionId={songSectionId}&songFrom={songFrom}&songTo={songTo}"
const val PRACTICE_SESSION_PATTERN =
    "practice-session?mode={mode}&symbols={symbols}&duration={duration}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}&progressionId={progressionId}&progressionRhythm={progressionRhythm}&songId={songId}&songSectionId={songSectionId}&songFrom={songFrom}&songTo={songTo}"

fun practiceSetupRoute(config: PracticeConfigUi): String = practiceRoute("practice-setup", config)

fun practiceSessionRoute(config: PracticeConfigUi): String = practiceRoute("practice-session", config)

private fun practiceRoute(base: String, config: PracticeConfigUi): String =
    "$base?mode=${config.mode.name}" +
        "&symbols=${encodeRouteValue(config.symbols)}" +
        "&duration=${config.durationSeconds}" +
        "&bpm=${config.bpm}" +
        "&signature=${encodeRouteValue(config.timeSignature)}" +
        "&switch=${config.switchMode.name}" +
        "&accent=${config.accentFirstBeat}" +
        "&barre=${config.allowBarre}" +
        "&maxFret=${config.maxFret}" +
        "&progressionId=${encodeRouteValue(config.sourceProgressionId)}" +
        "&progressionRhythm=${config.useProgressionRhythm}" +
        "&songId=${encodeRouteValue(config.songId)}" +
        "&songSectionId=${encodeRouteValue(config.songSectionId)}" +
        "&songFrom=${encodeRouteValue(config.songTransitionFrom)}" +
        "&songTo=${encodeRouteValue(config.songTransitionTo)}"

const val PRACTICE_RESULT_PATTERN =
    "practice-result?seconds={seconds}&attempts={attempts}&successes={successes}&failures={failures}&streak={streak}" +
        "&symbols={symbols}&previousRate={previousRate}&hardest={hardest}&suggestedBpm={suggestedBpm}&suggestion={suggestion}" +
        "&mode={mode}&goal={goal}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}" +
        "&progressionId={progressionId}&progressionRhythm={progressionRhythm}" +
        "&songId={songId}&songSectionId={songSectionId}&songFrom={songFrom}&songTo={songTo}"

fun practiceResultRoute(
    result: com.k2.music.ui.gateway.PracticeResultUi,
    config: PracticeConfigUi,
): String = "practice-result?seconds=${result.actualSeconds}&attempts=${result.attemptCount}" +
    "&successes=${result.successCount}&failures=${result.failureCount}&streak=${result.bestStreak}" +
    "&symbols=${encodeRouteValue(result.symbols.joinToString(" "))}" +
    "&previousRate=${result.previousSuccessRate?.let { (it * 10_000).toInt() } ?: -1}" +
    "&hardest=${encodeRouteValue(result.hardestTransition.orEmpty())}" +
    "&suggestedBpm=${result.difficultySuggestion.suggestedBpm}" +
    "&suggestion=${encodeRouteValue(result.difficultySuggestion.reason)}" +
    "&mode=${config.mode.name}&goal=${config.durationSeconds}&bpm=${config.bpm}" +
    "&signature=${encodeRouteValue(config.timeSignature)}&switch=${config.switchMode.name}" +
    "&accent=${config.accentFirstBeat}&barre=${config.allowBarre}&maxFret=${config.maxFret}" +
    "&progressionId=${encodeRouteValue(config.sourceProgressionId)}&progressionRhythm=${config.useProgressionRhythm}" +
    "&songId=${encodeRouteValue(config.songId)}&songSectionId=${encodeRouteValue(config.songSectionId)}" +
    "&songFrom=${encodeRouteValue(config.songTransitionFrom)}&songTo=${encodeRouteValue(config.songTransitionTo)}"

const val AI_ASSISTANT_PATTERN = "ai-assistant?mode={mode}&symbol={symbol}"

fun aiAssistantRoute(mode: String = "", symbol: String = ""): String =
    "ai-assistant?mode=${encodeRouteValue(mode)}&symbol=${encodeRouteValue(symbol)}"

const val EXPORT_PATTERN = "export?scope={scope}&symbols={symbols}&index={index}"

fun exportRoute(scope: ExportScopeUi, symbols: Collection<String> = emptyList(), index: Int = 0): String =
    "export?scope=${scope.name}&symbols=${encodeRouteValue(symbols.joinToString("\n"))}&index=$index"

fun encodeRouteValue(value: String): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.' || character == '~') {
            append(character)
        } else {
            append('%')
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }
}

fun decodeRouteValue(value: String): String {
    if ('%' !in value) return value
    val output = ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val high = value[index + 1].digitToIntOrNull(16)
            val low = value[index + 2].digitToIntOrNull(16)
            if (high != null && low != null) {
                output.write((high shl 4) or low)
                index += 3
                continue
            }
        }
        output.write(character.code)
        index++
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}

private const val HEX = "0123456789ABCDEF"
