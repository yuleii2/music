package com.k2.music

import com.k2.music.ui.learning.LearningGoal
import com.k2.music.ui.learning.LearningProfile
import com.k2.music.ui.learning.LearningProfileStore
import com.k2.music.ui.learning.SkillLevel
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.preferences.AppSettings
import com.k2.music.ui.preferences.ExperienceMode
import com.k2.music.ui.preferences.MotionLevel
import com.k2.music.ui.preferences.ThemeMode
import com.k2.music.song.SongChordEvent
import com.k2.music.song.SongLimits
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongPracticeRun
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.SongProject
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongRow
import com.k2.music.song.SongSection
import com.k2.music.song.SongSectionType
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongTransition
import com.k2.music.song.UserReportedDifficulty
import com.k2.music.song.UserReportedDifficultyStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.CancellationException

enum class RestoreMode { MERGE, OVERWRITE }

data class BackupPreview(
    val schemaVersion: Int,
    val appVersionName: String,
    val createdAt: Long,
    val favoriteCount: Int,
    val customVoicingCount: Int,
    val progressionCount: Int,
    val practiceSessionCount: Int,
    val transitionAttemptCount: Int,
    val songProjectCount: Int,
    val songPracticeRunCount: Int,
    val songDifficultyCount: Int,
    val incompatible: Boolean,
)

data class RestoreReport(
    val successfulItems: Int,
    val skippedItems: Int,
    val conflictItems: Int,
    val failedItems: Int,
    val messages: List<String>,
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
class BackupCancelledException : CancellationException("数据操作已取消并回滚。")

/** Versioned ZIP backup with checksums, bounded extraction and transactional restore. */
class FullBackupManager(
    private val appPreferences: AppPreferences,
    private val learningProfileStore: LearningProfileStore,
    private val userChordStore: UserChordStore,
    private val customVoicingStore: CustomVoicingStore,
    private val progressionStore: ProgressionStore,
    private val progressionDraftStore: ProgressionStore,
    private val practicePreferencesStore: PracticePreferencesStore,
    private val practiceRecordStore: PracticeRecordStore,
    private val transitionAttemptStore: TransitionAttemptStore,
    private val aiSettingsStore: AiSettingsStore,
    private val songProjectStore: SongProjectStore? = null,
    private val songPracticeRunStore: SongPracticeRunStore? = null,
    private val songDifficultyStore: UserReportedDifficultyStore? = null,
) {
    fun writeBackup(output: OutputStream, nowEpochMillis: Long = System.currentTimeMillis()): BackupPreview {
        val entries = linkedMapOf<String, ByteArray>()
        entries[SETTINGS] = json(settingsMap()).bytes()
        entries[LEARNING_PROFILE] = json(learningProfileMap(learningProfileStore.profile.value)).bytes()
        entries[FAVORITES] = json(userChordStore.favorites()).bytes()
        entries[HISTORY] = json(userChordStore.historyEntries().map(::historyMap)).bytes()
        entries[CUSTOM_VOICINGS] = json(customVoicingStore.all().map(::customVoicingMap)).bytes()
        entries[FAMILIAR_VOICINGS] = json(practicePreferencesStore.load().familiarVoicingIds.toList()).bytes()
        entries[PROGRESSIONS] = json(progressionStore.list().map(::progressionMap)).bytes()
        entries[PROGRESSION_DRAFTS] = json(progressionDraftStore.list().map(::progressionMap)).bytes()
        entries[PRACTICE_SESSIONS] = json(practiceRecordStore.list().map(::practiceSessionMap)).bytes()
        entries[TRANSITION_ATTEMPTS] = json(transitionAttemptStore.list().map(::attemptMap)).bytes()
        entries[SONG_PROJECTS] = json(songProjectStore?.list().orEmpty().map(::songProjectMap)).bytes()
        entries[SONG_PRACTICE_RUNS] = json(songPracticeRunStore?.list().orEmpty().map(::songPracticeRunMap)).bytes()
        entries[SONG_DIFFICULTIES] = json(songDifficultyStore?.list().orEmpty().map(::songDifficultyMap)).bytes()
        val checksums = entries.mapValues { sha256(it.value) }
        val manifest = linkedMapOf<String, Any?>(
            "schemaVersion" to SCHEMA_VERSION,
            "appVersionCode" to 6,
            "appVersionName" to "1.5",
            "createdAt" to nowEpochMillis,
            "sections" to entries.keys.toList(),
            "checksums" to checksums,
        )
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, MANIFEST, json(manifest).bytes())
            entries.forEach { (name, bytes) -> writeEntry(zip, name, bytes) }
            zip.finish()
        }
        return previewFrom(entries, SCHEMA_VERSION, "1.5", nowEpochMillis, false)
    }

    fun preview(input: InputStream): BackupPreview {
        val archive = readArchive(input)
        return previewFrom(
            archive.entries,
            archive.schemaVersion,
            archive.appVersionName,
            archive.createdAt,
            archive.schemaVersion > SCHEMA_VERSION,
        )
    }

    fun restore(
        input: InputStream,
        mode: RestoreMode,
        restoreSettingsInMerge: Boolean,
        isCancelled: () -> Boolean = { false },
    ): RestoreReport {
        val archive = readArchive(input)
        if (archive.schemaVersion > SCHEMA_VERSION) {
            throw BackupFormatException("备份版本 ${archive.schemaVersion} 高于当前支持版本 $SCHEMA_VERSION。")
        }
        val data = decode(archive.entries)
        val snapshot = snapshot()
        return try {
            checkCancelled(isCancelled)
            applyRestore(data, mode, restoreSettingsInMerge, isCancelled)
        } catch (cancelled: BackupCancelledException) {
            runCatching { restoreSnapshot(snapshot) }
            throw cancelled
        } catch (error: Throwable) {
            runCatching { restoreSnapshot(snapshot) }
            throw BackupFormatException("恢复失败，已回滚到恢复前数据。", error)
        }
    }

    private fun applyRestore(
        data: BackupData,
        mode: RestoreMode,
        restoreSettingsInMerge: Boolean,
        isCancelled: () -> Boolean,
    ): RestoreReport {
        var successful = 0
        var skipped = 0
        var conflicts = 0
        val messages = mutableListOf<String>()
        if (
            (data.songProjects.isNotEmpty() || data.songPracticeRuns.isNotEmpty() || data.songDifficulties.isNotEmpty()) &&
            (songProjectStore == null || songPracticeRunStore == null || songDifficultyStore == null)
        ) {
            throw IllegalStateException("当前环境未配置曲谱 Store，无法恢复曲谱章节。")
        }

        if (mode == RestoreMode.OVERWRITE) {
            userChordStore.replaceFavorites(data.favorites)
            userChordStore.replaceHistoryEntries(data.history)
            checkCancelled(isCancelled)
            customVoicingStore.replaceAll(data.customVoicings)
            checkCancelled(isCancelled)
            progressionStore.replaceAll(data.progressions)
            progressionDraftStore.replaceAll(data.progressionDrafts)
            checkCancelled(isCancelled)
            practiceRecordStore.replaceAll(data.practiceSessions)
            transitionAttemptStore.replaceAll(data.transitionAttempts)
            songProjectStore?.replaceAll(data.songProjects)
            songPracticeRunStore?.replaceAll(data.songPracticeRuns)
            songDifficultyStore?.replaceAll(data.songDifficulties)
            checkCancelled(isCancelled)
            successful += data.recordCount()
        } else {
            val favorites = (userChordStore.favorites() + data.favorites).distinct()
            successful += favorites.size - userChordStore.favorites().size
            skipped += data.favorites.size - (favorites.size - userChordStore.favorites().size)
            userChordStore.replaceFavorites(favorites)
            val localHistory = userChordStore.historyEntries()
            val localSymbols = localHistory.map { it.symbol }.toSet()
            successful += data.history.count { it.symbol !in localSymbols }
            skipped += data.history.count { it.symbol in localSymbols }
            val history = (localHistory + data.history)
                .groupBy { it.symbol }
                .map { (_, values) -> values.maxByOrNull { it.timestampEpochMillis }!! }
                .sortedWith(compareByDescending<UserChordStore.HistoryEntry> { it.timestampEpochMillis }.thenBy { it.symbol })
                .take(12)
            userChordStore.replaceHistoryEntries(history)

            val custom = customVoicingStore.all().associateBy { it.id }.toMutableMap()
            data.customVoicings.forEach { incoming ->
                checkCancelled(isCancelled)
                val current = custom[incoming.id]
                when {
                    current == null -> { custom[incoming.id] = incoming; successful++ }
                    sameCustomVoicing(current, incoming) -> skipped++
                    else -> {
                        val copy = CustomVoicing(
                            null, incoming.chordSymbol, incoming.name + "（恢复副本）", incoming.frets,
                            incoming.fingers, incoming.startFret, incoming.note, incoming.createdAt,
                        )
                        custom[copy.id] = copy
                        conflicts++; successful++
                    }
                }
            }
            customVoicingStore.replaceAll(custom.values.toList())

            val mergedProgressions = mergeProgressions(progressionStore.list(), data.progressions)
            progressionStore.replaceAll(mergedProgressions.values)
            successful += mergedProgressions.added
            skipped += mergedProgressions.skipped
            conflicts += mergedProgressions.conflicts
            val mergedDrafts = mergeProgressions(progressionDraftStore.list(), data.progressionDrafts)
            progressionDraftStore.replaceAll(mergedDrafts.values)
            successful += mergedDrafts.added
            skipped += mergedDrafts.skipped
            conflicts += mergedDrafts.conflicts

            val sessions = practiceRecordStore.list().associateBy { it.id }.toMutableMap()
            data.practiceSessions.forEach { incoming ->
                checkCancelled(isCancelled)
                val current = sessions[incoming.id]
                when {
                    current == null -> { sessions[incoming.id] = incoming; successful++ }
                    current == incoming -> skipped++
                    else -> { conflicts++; skipped++ }
                }
            }
            practiceRecordStore.replaceAll(sessions.values.toList())

            val attempts = transitionAttemptStore.list().associateBy { it.id }.toMutableMap()
            data.transitionAttempts.forEach { incoming ->
                checkCancelled(isCancelled)
                val current = attempts[incoming.id]
                when {
                    current == null -> { attempts[incoming.id] = incoming; successful++ }
                    current == incoming -> skipped++
                    else -> { conflicts++; skipped++ }
                }
            }
            transitionAttemptStore.replaceAll(attempts.values.toList())

            val projectMerge = mergeSongProjects(songProjectStore?.list().orEmpty(), data.songProjects)
            songProjectStore?.replaceAll(projectMerge.values)
            successful += projectMerge.added
            skipped += projectMerge.skipped
            conflicts += projectMerge.conflicts

            val runs = songPracticeRunStore?.list().orEmpty().associateBy { it.id }.toMutableMap()
            data.songPracticeRuns.forEach { incomingValue ->
                checkCancelled(isCancelled)
                val incoming = incomingValue.copy(
                    songId = projectMerge.restoredSongIds[incomingValue.songId] ?: incomingValue.songId,
                )
                val current = runs[incoming.id]
                when {
                    current == null -> { runs[incoming.id] = incoming; successful++ }
                    current == incoming -> skipped++
                    else -> { conflicts++; skipped++ }
                }
            }
            songPracticeRunStore?.replaceAll(runs.values.toList())

            val difficulties = songDifficultyStore?.list().orEmpty().associateBy { it.id }.toMutableMap()
            data.songDifficulties.forEach { incomingValue ->
                checkCancelled(isCancelled)
                val incoming = incomingValue.copy(
                    songId = projectMerge.restoredSongIds[incomingValue.songId] ?: incomingValue.songId,
                )
                val current = difficulties[incoming.id]
                when {
                    current == null -> { difficulties[incoming.id] = incoming; successful++ }
                    current == incoming -> skipped++
                    else -> { conflicts++; skipped++ }
                }
            }
            songDifficultyStore?.replaceAll(difficulties.values.toList())
            checkCancelled(isCancelled)
        }

        if (mode == RestoreMode.OVERWRITE || restoreSettingsInMerge) {
            checkCancelled(isCancelled)
            appPreferences.replaceSettings(data.appSettings)
            learningProfileStore.restore(data.learningProfile)
            practicePreferencesStore.save(data.practicePreferences)
            aiSettingsStore.save(data.aiSettings, null)
            successful += 4
        } else {
            messages += "合并模式未恢复设置。"
        }
        if (conflicts > 0) {
            messages += "发现 $conflicts 项 ID 冲突；自定义指法、进行和曲谱保留恢复副本，练习记录与困难标记保留本机版本。"
        }
        return RestoreReport(successful, skipped, conflicts, 0, messages)
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw BackupCancelledException()
    }

    private fun mergeProgressions(current: List<ChordProgression>, incoming: List<ChordProgression>): ProgressionMerge {
        val values = current.associateBy { it.id }.toMutableMap()
        var added = 0
        var skipped = 0
        var conflicts = 0
        incoming.forEach { progression ->
            val existing = values[progression.id]
            when {
                existing == null -> { values[progression.id] = progression; added++ }
                existing == progression -> skipped++
                else -> {
                    val copy = ChordProgression(
                        UUID.randomUUID().toString(), progression.name + "（恢复副本）", progression.keySignature,
                        progression.timeSignature, progression.bpm, progression.loop, progression.steps,
                        progression.createdAtEpochMillis, progression.updatedAtEpochMillis, progression.notes,
                    )
                    values[copy.id] = copy
                    added++; conflicts++
                }
            }
        }
        return ProgressionMerge(values.values.toList(), added, skipped, conflicts)
    }

    private fun mergeSongProjects(current: List<SongProject>, incoming: List<SongProject>): SongProjectMerge {
        val values = current.associateBy { it.id }.toMutableMap()
        val restoredSongIds = mutableMapOf<String, String>()
        var added = 0
        var skipped = 0
        var conflicts = 0
        incoming.forEach { project ->
            val existing = values[project.id]
            when {
                existing == null -> {
                    values[project.id] = project
                    restoredSongIds[project.id] = project.id
                    added++
                }
                existing == project -> {
                    restoredSongIds[project.id] = project.id
                    skipped++
                }
                else -> {
                    val restoredCopy = values.values.firstOrNull { candidate ->
                        candidate.id != project.id &&
                            candidate.title == project.title + "（恢复副本）" &&
                            candidate.copy(id = project.id, title = project.title) == project
                    }
                    if (restoredCopy != null) {
                        restoredSongIds[project.id] = restoredCopy.id
                        skipped++
                    } else {
                        val copy = project.copy(
                            id = UUID.randomUUID().toString(),
                            title = project.title + "（恢复副本）",
                        )
                        values[copy.id] = copy
                        restoredSongIds[project.id] = copy.id
                        added++
                        conflicts++
                    }
                }
            }
        }
        return SongProjectMerge(values.values.toList(), restoredSongIds, added, skipped, conflicts)
    }

    private fun snapshot() = BackupSnapshot(
        appPreferences.settings.value,
        learningProfileStore.profile.value,
        userChordStore.favorites(),
        userChordStore.historyEntries(),
        customVoicingStore.all(),
        progressionStore.list(),
        progressionDraftStore.list(),
        practicePreferencesStore.load(),
        practiceRecordStore.list(),
        transitionAttemptStore.list(),
        aiSettingsStore.load(),
        songProjectStore?.list().orEmpty(),
        songPracticeRunStore?.list().orEmpty(),
        songDifficultyStore?.list().orEmpty(),
    )

    private fun restoreSnapshot(value: BackupSnapshot) {
        appPreferences.replaceSettings(value.appSettings)
        learningProfileStore.restore(value.learningProfile)
        userChordStore.replaceFavorites(value.favorites)
        userChordStore.replaceHistoryEntries(value.history)
        customVoicingStore.replaceAll(value.customVoicings)
        progressionStore.replaceAll(value.progressions)
        progressionDraftStore.replaceAll(value.progressionDrafts)
        practicePreferencesStore.save(value.practicePreferences)
        practiceRecordStore.replaceAll(value.practiceSessions)
        transitionAttemptStore.replaceAll(value.transitionAttempts)
        aiSettingsStore.save(value.aiSettings, null)
        songProjectStore?.replaceAll(value.songProjects)
        songPracticeRunStore?.replaceAll(value.songPracticeRuns)
        songDifficultyStore?.replaceAll(value.songDifficulties)
    }

    private fun readArchive(input: InputStream): BackupArchive {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.contains('/') || name.contains('\\') || name.contains("..") || name.isBlank()) {
                        throw BackupFormatException("备份包含不安全或未知路径：$name")
                    }
                    if (entry.isDirectory || entries.containsKey(name)) throw BackupFormatException("备份包含重复或目录条目：$name")
                    if (entries.size >= MAX_FILES) throw BackupFormatException("备份文件数量超过限制。")
                    val bytes = readBounded(zip, MAX_ENTRY_BYTES)
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) throw BackupFormatException("备份解压后大小超过限制。")
                    entries[name] = bytes
                    zip.closeEntry()
                }
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: Throwable) {
            throw BackupFormatException("无法读取备份 ZIP。", error)
        }
        val manifestBytes = entries.remove(MANIFEST) ?: throw BackupFormatException("备份缺少 manifest.json。")
        val manifest = parseObject(manifestBytes.text(), MANIFEST)
        val schema = manifest.int("schemaVersion")
        if (schema < 1) throw BackupFormatException("备份 schema 版本无效：$schema。")
        val appVersion = manifest.string("appVersionName")
        val createdAt = manifest.long("createdAt")
        val sections = manifest.list("sections").map { it as? String ?: throw BackupFormatException("manifest sections 无效。") }
        if (sections.size != sections.toSet().size || entries.keys != sections.toSet()) {
            throw BackupFormatException("备份章节不完整或与 manifest 不一致。")
        }
        val required = if (schema <= 1) V1_SECTIONS else REQUIRED_SECTIONS
        if (!sections.toSet().containsAll(required) || (schema <= SCHEMA_VERSION && sections.toSet() != required)) {
            throw BackupFormatException("备份 schema $schema 的章节不完整或包含未知章节。")
        }
        val checksums = manifest.objectValue("checksums")
        if (checksums.keys != sections.toSet()) throw BackupFormatException("manifest 校验值章节不一致。")
        entries.forEach { (name, bytes) ->
            val expected = checksums[name] as? String ?: throw BackupFormatException("缺少 $name 的校验值。")
            if (!sha256(bytes).equals(expected, ignoreCase = true)) throw BackupFormatException("$name 校验失败，备份可能已损坏。")
        }
        return BackupArchive(entries, schema, appVersion, createdAt)
    }

    private fun decode(entries: Map<String, ByteArray>): BackupData {
        val familiar = parseStringList(entries.required(FAMILIAR_VOICINGS), FAMILIAR_VOICINGS).toSet()
        val settings = parseSettings(entries.required(SETTINGS), familiar)
        return BackupData(
            appSettings = settings.first,
            practicePreferences = settings.second,
            aiSettings = settings.third,
            learningProfile = parseLearningProfile(entries.required(LEARNING_PROFILE)),
            favorites = parseStringList(entries.required(FAVORITES), FAVORITES),
            history = parseHistory(entries.required(HISTORY)),
            customVoicings = parseList(entries.required(CUSTOM_VOICINGS), CUSTOM_VOICINGS).map { parseCustomVoicing(it.obj()) },
            progressions = parseList(entries.required(PROGRESSIONS), PROGRESSIONS).map { parseProgression(it.obj()) },
            progressionDrafts = parseList(entries.required(PROGRESSION_DRAFTS), PROGRESSION_DRAFTS).map { parseProgression(it.obj()) },
            practiceSessions = parseList(entries.required(PRACTICE_SESSIONS), PRACTICE_SESSIONS).map { parsePracticeSession(it.obj()) },
            transitionAttempts = parseList(entries.required(TRANSITION_ATTEMPTS), TRANSITION_ATTEMPTS).map { parseAttempt(it.obj()) },
            songProjects = entries[SONG_PROJECTS]?.let { parseList(it, SONG_PROJECTS).map { raw -> parseSongProject(raw.obj()) } }.orEmpty(),
            songPracticeRuns = entries[SONG_PRACTICE_RUNS]?.let { parseList(it, SONG_PRACTICE_RUNS).map { raw -> parseSongPracticeRun(raw.obj()) } }.orEmpty(),
            songDifficulties = entries[SONG_DIFFICULTIES]?.let { parseList(it, SONG_DIFFICULTIES).map { raw -> parseSongDifficulty(raw.obj()) } }.orEmpty(),
        )
    }

    private fun settingsMap(): Map<String, Any?> {
        val app = appPreferences.settings.value
        val practice = practicePreferencesStore.load()
        val ai = aiSettingsStore.load()
        return linkedMapOf(
            "app" to linkedMapOf(
                "themeMode" to app.themeMode.name,
                "motionLevel" to app.motionLevel.name,
                "experienceMode" to app.experienceMode.name,
                "dynamicColor" to app.dynamicColor,
                "recentToolId" to app.recentToolId,
            ),
            "practice" to linkedMapOf(
                "proficiency" to practice.proficiency.name,
                "allowBarre" to practice.allowBarre,
                "maxFret" to practice.maxFret,
                "defaultBpm" to practice.defaultBpm,
                "timeSignature" to practice.defaultTimeSignature.toString(),
                "playbackMode" to practice.defaultPlaybackMode.name,
                "accentFirstBeat" to practice.accentFirstBeat,
            ),
            "ai" to linkedMapOf(
                "enabled" to ai.enabled,
                "serviceName" to ai.serviceName,
                "baseUrl" to ai.baseUrl,
                "model" to ai.model,
                "temperature" to ai.temperature,
                "timeoutSeconds" to ai.timeoutSeconds,
            ),
        )
    }

    private fun historyMap(value: UserChordStore.HistoryEntry): Map<String, Any?> = linkedMapOf(
        "symbol" to value.symbol,
        "timestampEpochMillis" to value.timestampEpochMillis,
    )

    private fun parseHistory(bytes: ByteArray): List<UserChordStore.HistoryEntry> =
        parseList(bytes, HISTORY).mapIndexed { index, raw ->
            when (raw) {
                is String -> UserChordStore.HistoryEntry(raw, (Long.MAX_VALUE / 2) - index)
                else -> {
                    val value = raw.obj()
                    UserChordStore.HistoryEntry(value.string("symbol"), value.long("timestampEpochMillis"))
                }
            }
        }

    private fun parseSettings(bytes: ByteArray, familiar: Set<String>): Triple<AppSettings, PracticePreferences, AiSettings> {
        val root = parseObject(bytes.text(), SETTINGS)
        val app = root.objectValue("app")
        val practice = root.objectValue("practice")
        val ai = root.objectValue("ai")
        return Triple(
            AppSettings(
                enumValue(app.string("themeMode")),
                enumValue(app.string("motionLevel")),
                enumValue(app.string("experienceMode")),
                app.boolean("dynamicColor"),
                app["recentToolId"] as? String,
            ),
            PracticePreferences(
                enumValue(practice.string("proficiency")), practice.boolean("allowBarre"), practice.int("maxFret"),
                practice.int("defaultBpm"), TimeSignature.parse(practice.string("timeSignature")),
                enumValue(practice.string("playbackMode")), practice.boolean("accentFirstBeat"), familiar,
            ),
            AiSettings(
                ai.boolean("enabled"), ai.string("serviceName"), ai.string("baseUrl"), ai.string("model"),
                ai.number("temperature").toDouble(), ai.int("timeoutSeconds"),
            ),
        )
    }

    private fun learningProfileMap(value: LearningProfile) = linkedMapOf<String, Any?>(
        "version" to value.version,
        "onboardingCompleted" to value.onboardingCompleted,
        "skillLevel" to value.skillLevel.name,
        "goals" to value.goals.map { it.name },
        "dailyTargetMinutes" to value.dailyTargetMinutes,
        "preferredExperienceMode" to value.preferredExperienceMode.name,
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
    )

    private fun parseLearningProfile(bytes: ByteArray): LearningProfile {
        val root = parseObject(bytes.text(), LEARNING_PROFILE)
        return LearningProfile(
            version = root.int("version"),
            onboardingCompleted = root.boolean("onboardingCompleted"),
            skillLevel = enumValue(root.string("skillLevel")),
            goals = root.list("goals").map { enumValue<LearningGoal>(it as String) }.toSet(),
            dailyTargetMinutes = root.int("dailyTargetMinutes"),
            preferredExperienceMode = enumValue(root.string("preferredExperienceMode")),
            createdAt = root.long("createdAt"),
            updatedAt = root.long("updatedAt"),
        )
    }

    private fun customVoicingMap(value: CustomVoicing) = linkedMapOf<String, Any?>(
        "id" to value.id, "chordSymbol" to value.chordSymbol, "name" to value.name,
        "frets" to value.frets.toList(), "fingers" to value.fingers.toList(), "startFret" to value.startFret,
        "note" to value.note, "createdAt" to value.createdAt,
    )

    private fun parseCustomVoicing(root: Map<String, Any?>) = CustomVoicing(
        root.string("id"), root.string("chordSymbol"), root.string("name"), root.intArray("frets"),
        root.intArray("fingers"), root.int("startFret"), root.string("note"), root.long("createdAt"),
    )

    private fun progressionMap(value: ChordProgression) = linkedMapOf<String, Any?>(
        "id" to value.id, "name" to value.name, "keySignature" to value.keySignature,
        "timeSignature" to value.timeSignature.toString(), "bpm" to value.bpm, "loop" to value.loop,
        "createdAt" to value.createdAtEpochMillis, "updatedAt" to value.updatedAtEpochMillis, "notes" to value.notes,
        "steps" to value.steps.map { step -> linkedMapOf<String, Any?>(
            "chordSymbol" to step.chordSymbol, "voicingId" to step.voicingId, "beats" to step.beats,
            "strumPattern" to step.strumPattern, "order" to step.order,
        ) },
    )

    private fun parseProgression(root: Map<String, Any?>) = ChordProgression(
        root.string("id"), root.string("name"), root.string("keySignature"), TimeSignature.parse(root.string("timeSignature")),
        root.int("bpm"), root.boolean("loop"), root.list("steps").map { raw -> raw.obj().let { step ->
            ProgressionStep(step.string("chordSymbol"), step.string("voicingId"), step.number("beats").toDouble(), step.string("strumPattern"), step.int("order"))
        } }, root.long("createdAt"), root.long("updatedAt"), root.string("notes"),
    )

    private fun practiceSessionMap(value: PracticeSession) = linkedMapOf<String, Any?>(
        "id" to value.id, "startedAt" to value.startedAtEpochMillis, "endedAt" to value.endedAtEpochMillis,
        "type" to value.type.name, "symbols" to value.chordSymbols, "bpm" to value.bpm,
        "timeSignature" to value.timeSignature, "switchMode" to value.switchMode.name,
        "plannedDurationSeconds" to value.plannedDurationSeconds, "actualDurationSeconds" to value.actualDurationSeconds,
        "attemptCount" to value.attemptCount, "successCount" to value.successCount, "failureCount" to value.failureCount,
        "bestStreak" to value.bestStreak, "legacyCompletionCount" to if (value.legacy) value.completionCount else 0,
        "legacy" to value.legacy, "sourceProgressionId" to value.sourceProgressionId,
        "useProgressionRhythm" to value.useProgressionRhythm,
    )

    private fun parsePracticeSession(root: Map<String, Any?>) = PracticeSession(
        root.string("id"), root.long("startedAt"), root.long("endedAt"), enumValue(root.string("type")),
        root.list("symbols").map { it as String }, root.int("bpm"), root.string("timeSignature"),
        enumValue(root.string("switchMode")), root.int("plannedDurationSeconds"), root.int("actualDurationSeconds"),
        root.int("attemptCount"), root.int("successCount"), root.int("failureCount"), root.int("bestStreak"),
        root.int("legacyCompletionCount"), root.boolean("legacy"),
        root["sourceProgressionId"] as? String ?: "",
        root["useProgressionRhythm"] as? Boolean ?: false,
    )

    private fun attemptMap(value: TransitionAttempt) = linkedMapOf<String, Any?>(
        "id" to value.id, "sessionId" to value.sessionId, "timestamp" to value.timestampEpochMillis,
        "fromChord" to value.fromChord, "toChord" to value.toChord, "fromVoicingId" to value.fromVoicingId,
        "toVoicingId" to value.toVoicingId, "bpm" to value.bpm, "timeSignature" to value.timeSignature,
        "switchMode" to value.switchMode.name, "success" to value.success,
        "confirmationOffsetMillis" to value.confirmationOffsetMillis, "practiceMode" to value.practiceMode.name,
        "songId" to value.songId, "sectionId" to value.sectionId,
    )

    private fun parseAttempt(root: Map<String, Any?>) = TransitionAttempt(
        root.string("id"), root.string("sessionId"), root.long("timestamp"), root.string("fromChord"),
        root.string("toChord"), root.string("fromVoicingId"), root.string("toVoicingId"), root.int("bpm"),
        root.string("timeSignature"), enumValue(root.string("switchMode")), root.boolean("success"),
        (root["confirmationOffsetMillis"] as? Number)?.toLong(), enumValue(root.string("practiceMode")),
        root["songId"] as? String ?: "", root["sectionId"] as? String ?: "",
    )

    private fun songProjectMap(value: SongProject): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to value.schemaVersion,
        "parserVersion" to value.parserVersion,
        "id" to value.id,
        "title" to value.title,
        "artist" to value.artist,
        "originalText" to value.originalText,
        "originalKey" to value.originalKey,
        "transposeSemitones" to value.transposeSemitones,
        "capoFret" to value.capoFret,
        "bpm" to value.bpm,
        "timeSignature" to value.timeSignature,
        "timingState" to value.timingState.name,
        "notes" to value.notes,
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
        "accidentalPreference" to value.accidentalPreference.name,
        "sections" to value.sections.map { section ->
            linkedMapOf<String, Any?>(
                "id" to section.id,
                "name" to section.name,
                "type" to section.type.name,
                "order" to section.order,
                "repeatCount" to section.repeatCount,
                "rows" to section.rows.map { row ->
                    linkedMapOf<String, Any?>(
                        "id" to row.id,
                        "lyricText" to row.lyricText,
                        "rawChordText" to row.rawChordText,
                        "order" to row.order,
                        "chordEvents" to row.chordEvents.map { event ->
                            linkedMapOf<String, Any?>(
                                "id" to event.id,
                                "chordSymbol" to event.chordSymbol,
                                "normalizedChordSymbol" to event.normalizedChordSymbol,
                                "characterPosition" to event.characterPosition,
                                "durationBeats" to event.durationBeats,
                                "selectedVoicingId" to event.selectedVoicingId,
                                "measureIndex" to event.measureIndex,
                                "order" to event.order,
                            )
                        },
                    )
                },
            )
        },
    )

    private fun parseSongProject(root: Map<String, Any?>): SongProject {
        val storedSchema = root.int("schemaVersion")
        if (storedSchema !in 1..SongLimits.PROJECT_SCHEMA_VERSION) {
            throw BackupFormatException("不支持的曲谱数据 schema：$storedSchema。")
        }
        return SongProject(
            schemaVersion = SongLimits.PROJECT_SCHEMA_VERSION,
            parserVersion = root.int("parserVersion"),
            id = root.string("id"),
            title = root.string("title"),
            artist = root.string("artist"),
            originalText = root.string("originalText"),
            originalKey = root.string("originalKey"),
            transposeSemitones = root.int("transposeSemitones"),
            capoFret = root.int("capoFret"),
            bpm = root.int("bpm"),
            timeSignature = root.string("timeSignature"),
            timingState = enumValue<SongTimingState>(root.string("timingState")),
            sections = root.list("sections").map { sectionRaw ->
                val section = sectionRaw.obj()
                SongSection(
                    id = section.string("id"),
                    name = section.string("name"),
                    type = enumValue<SongSectionType>(section.string("type")),
                    order = section.int("order"),
                    repeatCount = section.int("repeatCount"),
                    rows = section.list("rows").map { rowRaw ->
                        val row = rowRaw.obj()
                        SongRow(
                            id = row.string("id"),
                            lyricText = row.string("lyricText"),
                            rawChordText = row.string("rawChordText"),
                            chordEvents = row.list("chordEvents").map { eventRaw ->
                                val event = eventRaw.obj()
                                SongChordEvent(
                                    id = event.string("id"),
                                    chordSymbol = event.string("chordSymbol"),
                                    normalizedChordSymbol = event.string("normalizedChordSymbol"),
                                    characterPosition = (event["characterPosition"] as? Number)?.toInt(),
                                    durationBeats = (event["durationBeats"] as? Number)?.toDouble(),
                                    selectedVoicingId = event["selectedVoicingId"] as? String,
                                    measureIndex = (event["measureIndex"] as? Number)?.toInt(),
                                    order = event.int("order"),
                                )
                            },
                            order = row.int("order"),
                        )
                    },
                )
            },
            notes = root.string("notes"),
            createdAt = root.long("createdAt"),
            updatedAt = root.long("updatedAt"),
            accidentalPreference = (root["accidentalPreference"] as? String)?.let {
                enumValue<MusicTheoryUtils.AccidentalPreference>(it)
            } ?: MusicTheoryUtils.AccidentalPreference.AUTO,
        )
    }

    private fun songPracticeRunMap(value: SongPracticeRun): Map<String, Any?> = linkedMapOf(
        "id" to value.id,
        "songId" to value.songId,
        "sectionId" to value.sectionId,
        "mode" to value.mode.name,
        "bpm" to value.bpm,
        "transposeSemitones" to value.transposeSemitones,
        "capoFret" to value.capoFret,
        "startedAt" to value.startedAt,
        "endedAt" to value.endedAt,
        "actualDurationSeconds" to value.actualDurationSeconds,
        "completed" to value.completed,
        "loopEnabled" to value.loopEnabled,
        "showFretboard" to value.showFretboard,
        "selectedVoicingIds" to value.selectedVoicingIds,
        "reportedDifficultTransitions" to value.reportedDifficultTransitions.map { transition ->
            linkedMapOf("fromChord" to transition.fromChord, "toChord" to transition.toChord)
        },
    )

    private fun parseSongPracticeRun(root: Map<String, Any?>) = SongPracticeRun(
        id = root.string("id"),
        songId = root.string("songId"),
        sectionId = root["sectionId"] as? String,
        mode = enumValue<SongPracticeMode>(root.string("mode")),
        bpm = root.int("bpm"),
        transposeSemitones = root.int("transposeSemitones"),
        capoFret = root.int("capoFret"),
        startedAt = root.long("startedAt"),
        endedAt = root.long("endedAt"),
        actualDurationSeconds = root.int("actualDurationSeconds"),
        completed = root.boolean("completed"),
        reportedDifficultTransitions = root.list("reportedDifficultTransitions").map { raw ->
            val transition = raw.obj()
            SongTransition(transition.string("fromChord"), transition.string("toChord"))
        },
        loopEnabled = root["loopEnabled"] as? Boolean ?: true,
        showFretboard = root["showFretboard"] as? Boolean ?: true,
        selectedVoicingIds = (root["selectedVoicingIds"] as? Map<*, *>)?.entries?.associate { (key, value) ->
            (key as? String ?: throw BackupFormatException("固定指法快照事件 ID 无效。")) to
                (value as? String ?: throw BackupFormatException("固定指法快照值无效。"))
        }.orEmpty(),
    )

    private fun songDifficultyMap(value: UserReportedDifficulty): Map<String, Any?> = linkedMapOf(
        "id" to value.id,
        "songId" to value.songId,
        "sectionId" to value.sectionId,
        "fromChord" to value.fromChord,
        "toChord" to value.toChord,
        "reportedAt" to value.reportedAt,
        "resolved" to value.resolved,
        "note" to value.note,
    )

    private fun parseSongDifficulty(root: Map<String, Any?>) = UserReportedDifficulty(
        id = root.string("id"),
        songId = root.string("songId"),
        sectionId = root["sectionId"] as? String,
        fromChord = root.string("fromChord"),
        toChord = root.string("toChord"),
        reportedAt = root.long("reportedAt"),
        resolved = root.boolean("resolved"),
        note = root.string("note"),
    )

    private fun previewFrom(
        entries: Map<String, ByteArray>,
        schema: Int,
        appVersion: String,
        createdAt: Long,
        incompatible: Boolean,
    ) = BackupPreview(
        schema, appVersion, createdAt,
        parseList(entries.required(FAVORITES), FAVORITES).size,
        parseList(entries.required(CUSTOM_VOICINGS), CUSTOM_VOICINGS).size,
        parseList(entries.required(PROGRESSIONS), PROGRESSIONS).size,
        parseList(entries.required(PRACTICE_SESSIONS), PRACTICE_SESSIONS).size,
        parseList(entries.required(TRANSITION_ATTEMPTS), TRANSITION_ATTEMPTS).size,
        entries[SONG_PROJECTS]?.let { parseList(it, SONG_PROJECTS).size } ?: 0,
        entries[SONG_PRACTICE_RUNS]?.let { parseList(it, SONG_PRACTICE_RUNS).size } ?: 0,
        entries[SONG_DIFFICULTIES]?.let { parseList(it, SONG_DIFFICULTIES).size } ?: 0,
        incompatible,
    )

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw BackupFormatException("备份单个条目超过大小限制。")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sameCustomVoicing(left: CustomVoicing, right: CustomVoicing) =
        left.id == right.id && left.chordSymbol == right.chordSymbol && left.name == right.name &&
            left.frets.contentEquals(right.frets) && left.fingers.contentEquals(right.fingers) &&
            left.startFret == right.startFret && left.note == right.note && left.createdAt == right.createdAt

    private fun json(value: Any?): String = JsonSupport.stringify(value)
    private fun String.bytes() = toByteArray(Charsets.UTF_8)
    private fun ByteArray.text() = toString(Charsets.UTF_8)
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun parseObject(json: String, section: String) = try { JsonSupport.parse(json).obj() } catch (error: Throwable) {
        throw BackupFormatException("$section JSON 无效。", error)
    }
    private fun parseList(bytes: ByteArray, section: String) = try { JsonSupport.parse(bytes.text()).listValue() } catch (error: Throwable) {
        throw BackupFormatException("$section JSON 无效。", error)
    }
    private fun parseStringList(bytes: ByteArray, section: String) = parseList(bytes, section).map {
        it as? String ?: throw BackupFormatException("$section 包含非文本值。")
    }
    private inline fun <reified T : Enum<T>> enumValue(raw: String): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: throw BackupFormatException("不支持的枚举值：$raw")

    private fun Any?.obj(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { (key, value) ->
        (key as? String ?: throw BackupFormatException("JSON 对象键无效。")) to value
    } ?: throw BackupFormatException("预期 JSON 对象。")
    private fun Any?.listValue(): List<Any?> = this as? List<Any?> ?: throw BackupFormatException("预期 JSON 数组。")
    private fun Map<String, Any?>.string(key: String) = this[key] as? String ?: throw BackupFormatException("缺少文本字段 $key。")
    private fun Map<String, Any?>.number(key: String) = this[key] as? Number ?: throw BackupFormatException("缺少数值字段 $key。")
    private fun Map<String, Any?>.int(key: String) = number(key).toInt()
    private fun Map<String, Any?>.long(key: String) = number(key).toLong()
    private fun Map<String, Any?>.boolean(key: String) = this[key] as? Boolean ?: throw BackupFormatException("缺少布尔字段 $key。")
    private fun Map<String, Any?>.list(key: String) = this[key].listValue()
    private fun Map<String, Any?>.objectValue(key: String) = this[key].obj()
    private fun Map<String, Any?>.intArray(key: String) = list(key).map { (it as Number).toInt() }.toIntArray()
    private fun Map<String, ByteArray>.required(name: String) = this[name] ?: throw BackupFormatException("备份缺少 $name。")

    private data class BackupArchive(
        val entries: Map<String, ByteArray>,
        val schemaVersion: Int,
        val appVersionName: String,
        val createdAt: Long,
    )
    private data class BackupData(
        val appSettings: AppSettings,
        val practicePreferences: PracticePreferences,
        val aiSettings: AiSettings,
        val learningProfile: LearningProfile,
        val favorites: List<String>,
        val history: List<UserChordStore.HistoryEntry>,
        val customVoicings: List<CustomVoicing>,
        val progressions: List<ChordProgression>,
        val progressionDrafts: List<ChordProgression>,
        val practiceSessions: List<PracticeSession>,
        val transitionAttempts: List<TransitionAttempt>,
        val songProjects: List<SongProject>,
        val songPracticeRuns: List<SongPracticeRun>,
        val songDifficulties: List<UserReportedDifficulty>,
    ) {
        fun recordCount() = favorites.size + history.size + customVoicings.size + progressions.size +
            progressionDrafts.size + practiceSessions.size + transitionAttempts.size + songProjects.size +
            songPracticeRuns.size + songDifficulties.size
    }
    private data class BackupSnapshot(
        val appSettings: AppSettings,
        val learningProfile: LearningProfile,
        val favorites: List<String>,
        val history: List<UserChordStore.HistoryEntry>,
        val customVoicings: List<CustomVoicing>,
        val progressions: List<ChordProgression>,
        val progressionDrafts: List<ChordProgression>,
        val practicePreferences: PracticePreferences,
        val practiceSessions: List<PracticeSession>,
        val transitionAttempts: List<TransitionAttempt>,
        val aiSettings: AiSettings,
        val songProjects: List<SongProject>,
        val songPracticeRuns: List<SongPracticeRun>,
        val songDifficulties: List<UserReportedDifficulty>,
    )
    private data class ProgressionMerge(
        val values: List<ChordProgression>,
        val added: Int,
        val skipped: Int,
        val conflicts: Int,
    )
    private data class SongProjectMerge(
        val values: List<SongProject>,
        val restoredSongIds: Map<String, String>,
        val added: Int,
        val skipped: Int,
        val conflicts: Int,
    )

    private companion object {
        const val SCHEMA_VERSION = 2
        const val MAX_FILES = 32
        const val MAX_ENTRY_BYTES = 5 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 20 * 1024 * 1024
        const val MANIFEST = "manifest.json"
        const val SETTINGS = "settings.json"
        const val LEARNING_PROFILE = "learning-profile.json"
        const val FAVORITES = "favorites.json"
        const val HISTORY = "history.json"
        const val CUSTOM_VOICINGS = "custom-voicings.json"
        const val FAMILIAR_VOICINGS = "familiar-voicings.json"
        const val PROGRESSIONS = "progressions.json"
        const val PROGRESSION_DRAFTS = "progression-drafts.json"
        const val PRACTICE_SESSIONS = "practice-sessions.json"
        const val TRANSITION_ATTEMPTS = "transition-attempts.json"
        const val SONG_PROJECTS = "song-projects.json"
        const val SONG_PRACTICE_RUNS = "song-practice-runs.json"
        const val SONG_DIFFICULTIES = "song-difficulties.json"
        val V1_SECTIONS = setOf(
            SETTINGS, LEARNING_PROFILE, FAVORITES, HISTORY, CUSTOM_VOICINGS, FAMILIAR_VOICINGS,
            PROGRESSIONS, PROGRESSION_DRAFTS, PRACTICE_SESSIONS, TRANSITION_ATTEMPTS,
        )
        val REQUIRED_SECTIONS = setOf(
            *V1_SECTIONS.toTypedArray(), SONG_PROJECTS, SONG_PRACTICE_RUNS, SONG_DIFFICULTIES,
        )
    }
}
