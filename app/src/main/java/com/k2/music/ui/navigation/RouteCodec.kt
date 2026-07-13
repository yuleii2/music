package com.k2.music.ui.navigation

import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.ExportScopeUi
import java.io.ByteArrayOutputStream

const val CHORD_DETAIL_PATTERN = "chord-detail?symbol={symbol}"

fun chordDetailRoute(symbol: String): String = "chord-detail?symbol=${encodeRouteValue(symbol)}"

const val PROGRESSION_EDITOR_PATTERN = "progression-editor?id={id}&seed={seed}"

fun progressionEditorRoute(seed: String): String =
    "progression-editor?id=&seed=${encodeRouteValue(seed)}"

fun progressionEditorByIdRoute(id: String): String =
    "progression-editor?id=${encodeRouteValue(id)}&seed="

const val PRACTICE_SETUP_PATTERN =
    "practice-setup?mode={mode}&symbols={symbols}&duration={duration}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}"
const val PRACTICE_SESSION_PATTERN =
    "practice-session?mode={mode}&symbols={symbols}&duration={duration}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}"

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
        "&maxFret=${config.maxFret}"

const val PRACTICE_RESULT_PATTERN =
    "practice-result?seconds={seconds}&count={count}&streak={streak}&symbols={symbols}&previous={previous}" +
        "&mode={mode}&goal={goal}&bpm={bpm}&signature={signature}&switch={switch}&accent={accent}&barre={barre}&maxFret={maxFret}"

fun practiceResultRoute(
    result: com.k2.music.ui.gateway.PracticeResultUi,
    config: PracticeConfigUi,
): String = "practice-result?seconds=${result.actualSeconds}&count=${result.completionCount}&streak=${result.bestStreak}" +
    "&symbols=${encodeRouteValue(result.symbols.joinToString(" "))}&previous=${result.previousCompletionCount ?: -1}" +
    "&mode=${config.mode.name}&goal=${config.durationSeconds}&bpm=${config.bpm}" +
    "&signature=${encodeRouteValue(config.timeSignature)}&switch=${config.switchMode.name}" +
    "&accent=${config.accentFirstBeat}&barre=${config.allowBarre}&maxFret=${config.maxFret}"

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
