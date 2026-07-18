package com.k2.music.ui.song

import com.k2.music.MusicTheoryUtils
import com.k2.music.PracticePreferences
import com.k2.music.PracticePreferencesStore
import com.k2.music.TransitionAttemptStore
import com.k2.music.UserChordStore
import com.k2.music.song.SongArrangement
import com.k2.music.song.SongArrangementEngine
import com.k2.music.song.SongPracticeRun
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.SongProject
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongSheetParseResult
import com.k2.music.song.SongSheetParser
import com.k2.music.song.SongParseLineRole
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongTransition
import com.k2.music.song.SongTransitionExtractor
import com.k2.music.song.UserReportedDifficulty
import com.k2.music.song.UserReportedDifficultyStore
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.k2.music.ui.gateway.ProgressionGateway
import com.k2.music.ui.model.ProgressionUiModel

data class SongPracticePreparation(
    val project: SongProject,
    val sectionId: String?,
    val sectionName: String,
    val progression: ProgressionUiModel,
    val transitions: List<SongTransition>,
    val lyricLines: List<String>,
    val preciseTiming: Boolean,
    val timingMessage: String,
)

data class SongImportDraft(
    val title: String,
    val artist: String,
    val originalText: String,
    val timeSignature: String,
    val parseResult: SongSheetParseResult,
    val lineOverrides: Map<Int, SongParseLineRole> = emptyMap(),
)

data class SongCardUi(
    val project: SongProject,
    val lastPracticeAt: Long?,
    val recentBpm: Int?,
    val practiceStatus: String,
) {
    val sectionCount: Int get() = project.sections.size
    val chordCount: Int get() = project.chordEventCount
}

data class SongLibraryData(
    val recent: List<SongCardUi>,
    val all: List<SongCardUi>,
    val incomplete: List<SongCardUi>,
)

data class SongDetailData(
    val project: SongProject,
    val runs: List<SongPracticeRun>,
    val difficulties: List<UserReportedDifficulty>,
    val transitionsBySection: Map<String?, List<SongTransition>> = emptyMap(),
    val progress: SongProgressStats = SongProgressStats(),
) {
    val unpracticedSectionIds: Set<String> = project.sections.map { it.id }.toSet() -
        runs.mapNotNull { it.sectionId }.toSet()
    val lastRun: SongPracticeRun? = runs.maxByOrNull { it.startedAt }
    val difficultyLabel: String = when {
        project.chordEventCount >= 48 || project.sections.size >= 8 -> "进阶"
        project.chordEventCount >= 20 || project.sections.size >= 4 -> "中等"
        else -> "入门"
    }
}

data class SongProgressStats(
    val totalPracticeSeconds: Int = 0,
    val sevenDayPracticeSeconds: Int = 0,
    val sectionPracticeCounts: Map<String, Int> = emptyMap(),
    val recentBpm: Int? = null,
    val highestCompletedBpm: Int? = null,
    val unresolvedDifficultyCount: Int = 0,
    val resolvedDifficultyCount: Int = 0,
    val unpracticedSectionCount: Int = 0,
    val lastCompletePerformanceAt: Long? = null,
    val completionCount: Int = 0,
)

enum class SongHomeTaskType {
    CONTINUE_RECENT,
    REVIEW_REPORTED_DIFFICULTY,
    REVIEW_GLOBAL_WEAKNESS,
    CONTINUE_UNFINISHED_SECTION,
    LEARN_NEW_CHORD,
    PERFORM_STABLE_SECTION,
}

data class SongHomeTask(
    val type: SongHomeTaskType,
    val title: String,
    val reason: String,
    val songId: String,
    val sectionId: String?,
    val mode: SongPracticeMode?,
    val bpm: Int,
    val transposeSemitones: Int,
    val capoFret: Int,
    val loopEnabled: Boolean = true,
    val showFretboard: Boolean = true,
    val transition: SongTransition? = null,
    val timeSignature: String = "4/4",
    val selectedVoicingIds: Map<String, String> = emptyMap(),
)

interface SongGateway {
    suspend fun library(query: String = ""): SongLibraryData
    suspend fun parseImport(
        title: String,
        artist: String,
        originalText: String,
        timeSignature: String = "4/4",
    ): SongImportDraft
    suspend fun importDraft(): SongImportDraft?
    suspend fun setImportLineRole(lineNumber: Int, role: SongParseLineRole): SongImportDraft
    suspend fun parseText(originalText: String, timeSignature: String): SongSheetParseResult
    suspend fun saveImportDraft(): SongProject
    suspend fun createManual(): SongProject
    suspend fun detail(songId: String): SongDetailData?
    suspend fun saveProject(project: SongProject): SongProject
    suspend fun deleteProject(songId: String): Boolean
    suspend fun arrangement(songId: String): SongArrangement?
    suspend fun configureArrangement(
        songId: String,
        transposeSemitones: Int,
        capoFret: Int,
        accidentalPreference: MusicTheoryUtils.AccidentalPreference,
    ): SongProject
    suspend fun resetArrangement(songId: String): SongProject
    suspend fun pinVoicing(songId: String, eventId: String, voicingId: String?): SongProject
    suspend fun preparePractice(
        songId: String,
        sectionId: String? = null,
        onlyTransition: SongTransition? = null,
    ): SongPracticePreparation
    suspend fun savePracticeRun(
        songId: String,
        sectionId: String?,
        mode: SongPracticeMode,
        bpm: Int,
        startedAt: Long,
        endedAt: Long,
        actualDurationSeconds: Int,
        completed: Boolean,
        difficultTransitions: List<SongTransition> = emptyList(),
        runId: String? = null,
        loopEnabled: Boolean = true,
        showFretboard: Boolean = true,
    ): SongPracticeRun
    suspend fun homeTasks(songAccompanimentGoal: Boolean): List<SongHomeTask>
    suspend fun restorePracticeConfiguration(
        songId: String,
        bpm: Int,
        transposeSemitones: Int,
        capoFret: Int,
        selectedVoicingIds: Map<String, String> = emptyMap(),
        restoreVoicings: Boolean = false,
    ): SongProject
}

class DefaultSongGateway(
    private val projectStore: SongProjectStore,
    private val runStore: SongPracticeRunStore,
    private val difficultyStore: UserReportedDifficultyStore,
    private val parser: SongSheetParser,
    private val importDraftStore: SongImportDraftStore,
    private val arrangementEngine: SongArrangementEngine? = null,
    private val practicePreferencesStore: PracticePreferencesStore? = null,
    private val userChordStore: UserChordStore? = null,
    private val transitionAttemptStore: TransitionAttemptStore? = null,
    private val progressionGateway: ProgressionGateway? = null,
    private val transitionExtractor: SongTransitionExtractor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : SongGateway {
    override suspend fun library(query: String): SongLibraryData = withContext(ioDispatcher) {
        val normalizedQuery = query.trim()
        val runs = runStore.list()
        val runsBySong = runs.groupBy { it.songId }
        val cards = projectStore.list()
            .filter { project ->
                normalizedQuery.isEmpty() || project.title.contains(normalizedQuery, ignoreCase = true) ||
                    project.artist.contains(normalizedQuery, ignoreCase = true) ||
                    project.originalText.contains(normalizedQuery, ignoreCase = true)
            }
            .map { project ->
                val songRuns = runsBySong[project.id].orEmpty()
                val recent = songRuns.maxByOrNull { it.startedAt }
                SongCardUi(
                    project = project,
                    lastPracticeAt = recent?.startedAt,
                    recentBpm = recent?.bpm,
                    practiceStatus = when {
                        songRuns.isEmpty() -> "未练习"
                        recent?.completed == true -> "最近已完成"
                        else -> "待继续"
                    },
                )
            }
        SongLibraryData(
            recent = cards.filter { it.lastPracticeAt != null }
                .sortedByDescending { it.lastPracticeAt }
                .take(5),
            all = cards,
            incomplete = cards.filter {
                it.project.sections.isEmpty() || it.project.chordEventCount < 2 ||
                    it.project.originalKey.isBlank() || it.project.timingState == SongTimingState.UNTYPED
            },
        )
    }

    override suspend fun parseImport(
        title: String,
        artist: String,
        originalText: String,
        timeSignature: String,
    ): SongImportDraft = withContext(ioDispatcher) {
        val result = parser.parse(originalText, timeSignature)
        val resolvedTitle = title.trim().ifBlank { result.detectedTitle.orEmpty() }
        require(resolvedTitle.isNotBlank()) { "请输入曲名，或在原文中使用“标题：曲名”。" }
        val draft = SongImportDraft(
            title = resolvedTitle,
            artist = artist.trim(),
            originalText = originalText,
            timeSignature = timeSignature,
            parseResult = result,
            lineOverrides = emptyMap(),
        )
        importDraftStore.write(draft)
        draft
    }

    override suspend fun importDraft(): SongImportDraft? = withContext(ioDispatcher) {
        importDraftStore.read()?.let { persisted ->
            persisted.toDraft(
                parser.parse(persisted.originalText, persisted.timeSignature, persisted.lineOverrides),
            )
        }
    }

    override suspend fun setImportLineRole(
        lineNumber: Int,
        role: SongParseLineRole,
    ): SongImportDraft = withContext(ioDispatcher) {
        val persisted = importDraftStore.read() ?: error("导入草稿已失效，请返回重新解析。")
        val lineCount = persisted.originalText.replace("\r\n", "\n").replace('\r', '\n').split('\n').size
        require(lineNumber in 1..lineCount) { "修正行号超出原文范围。" }
        val overrides = persisted.lineOverrides.toMutableMap().apply {
            if (role == SongParseLineRole.AUTO) remove(lineNumber) else put(lineNumber, role)
        }.toSortedMap()
        val result = parser.parse(persisted.originalText, persisted.timeSignature, overrides)
        persisted.copy(lineOverrides = overrides).toDraft(result).also(importDraftStore::write)
    }

    override suspend fun parseText(
        originalText: String,
        timeSignature: String,
    ): SongSheetParseResult = withContext(ioDispatcher) {
        parser.parse(originalText, timeSignature)
    }

    override suspend fun saveImportDraft(): SongProject = withContext(ioDispatcher) {
        val draft = importDraftStore.read() ?: error("导入草稿已失效，请返回重新解析。")
        val result = parser.parse(draft.originalText, draft.timeSignature, draft.lineOverrides)
        require(result.chordEventCount > 0) { "没有可保存的和弦，请先修正原文。" }
        val project = SongProject.create(
            title = draft.title,
            artist = draft.artist,
            originalText = draft.originalText,
            timeSignature = draft.timeSignature,
            timingState = result.timingState,
            sections = result.sections,
            parserVersion = result.parserVersion,
            now = clock(),
        )
        projectStore.add(project)
        importDraftStore.clear()
        project
    }

    override suspend fun createManual(): SongProject = withContext(ioDispatcher) {
        SongProject.create(title = "未命名曲谱", now = clock())
    }

    override suspend fun detail(songId: String): SongDetailData? = withContext(ioDispatcher) {
        projectStore.read(songId)?.let { project ->
            val transitions = if (arrangementEngine != null && transitionExtractor != null) {
                val arrangement = arrangementEngine.arrange(
                    project,
                    practicePreferencesStore?.load() ?: PracticePreferences.defaults(),
                    userChordStore?.favorites().orEmpty().toSet(),
                    transitionAttemptStore?.list().orEmpty(),
                )
                buildMap<String?, List<SongTransition>> {
                    put(null, transitionExtractor.extract(project, arrangement, null, true, true))
                    project.sections.forEach { section ->
                        put(section.id, transitionExtractor.extract(project, arrangement, section.id, true, true))
                    }
                }
            } else {
                emptyMap()
            }
            SongDetailData(
                project = project,
                runs = runStore.forSong(songId),
                difficulties = difficultyStore.forSong(songId).filterNot { it.resolved },
                transitionsBySection = transitions,
                progress = buildProgress(project, runStore.forSong(songId), difficultyStore.forSong(songId)),
            )
        }
    }

    override suspend fun saveProject(project: SongProject): SongProject = withContext(ioDispatcher) {
        val now = clock().coerceAtLeast(project.createdAt)
        projectStore.save(project.copy(updatedAt = now))
    }

    override suspend fun deleteProject(songId: String): Boolean = withContext(ioDispatcher) {
        val deleted = projectStore.delete(songId)
        if (deleted) {
            runStore.deleteForSong(songId)
            difficultyStore.deleteForSong(songId)
        }
        deleted
    }

    override suspend fun arrangement(songId: String): SongArrangement? = withContext(ioDispatcher) {
        val project = projectStore.read(songId) ?: return@withContext null
        val engine = requireNotNull(arrangementEngine) { "曲谱编配服务尚未初始化。" }
        engine.arrange(
            project,
            practicePreferencesStore?.load() ?: PracticePreferences.defaults(),
            userChordStore?.favorites().orEmpty().toSet(),
            transitionAttemptStore?.list().orEmpty(),
        )
    }

    override suspend fun configureArrangement(
        songId: String,
        transposeSemitones: Int,
        capoFret: Int,
        accidentalPreference: MusicTheoryUtils.AccidentalPreference,
    ): SongProject = withContext(ioDispatcher) {
        val current = projectStore.read(songId) ?: error("这份曲谱不存在或已被删除。")
        val engine = requireNotNull(arrangementEngine) { "曲谱编配服务尚未初始化。" }
        projectStore.save(engine.configure(current, transposeSemitones, capoFret, accidentalPreference, clock()))
    }

    override suspend fun resetArrangement(songId: String): SongProject = withContext(ioDispatcher) {
        val current = projectStore.read(songId) ?: error("这份曲谱不存在或已被删除。")
        val engine = requireNotNull(arrangementEngine) { "曲谱编配服务尚未初始化。" }
        projectStore.save(engine.reset(current, clock()))
    }

    override suspend fun pinVoicing(
        songId: String,
        eventId: String,
        voicingId: String?,
    ): SongProject = withContext(ioDispatcher) {
        val current = projectStore.read(songId) ?: error("这份曲谱不存在或已被删除。")
        val engine = requireNotNull(arrangementEngine) { "曲谱编配服务尚未初始化。" }
        projectStore.save(engine.pinVoicing(current, eventId, voicingId, clock()))
    }

    override suspend fun preparePractice(
        songId: String,
        sectionId: String?,
        onlyTransition: SongTransition?,
    ): SongPracticePreparation = withContext(ioDispatcher) {
        val project = projectStore.read(songId) ?: error("这份曲谱不存在或已被删除。")
        val engine = requireNotNull(arrangementEngine) { "曲谱编配服务尚未初始化。" }
        val extractor = requireNotNull(transitionExtractor) { "曲谱切换提取服务尚未初始化。" }
        val progressions = requireNotNull(progressionGateway) { "曲谱播放服务尚未初始化。" }
        if (sectionId != null) require(project.sections.any { it.id == sectionId }) { "所选段落不存在。" }
        val arrangement = engine.arrange(
            project,
            practicePreferencesStore?.load() ?: PracticePreferences.defaults(),
            userChordStore?.favorites().orEmpty().toSet(),
            transitionAttemptStore?.list().orEmpty(),
        )
        val ordered = extractor.orderedChords(project, arrangement, sectionId)
        require(ordered.size >= 2) { "所选曲谱范围至少需要两个和弦事件。" }
        val selected = if (onlyTransition == null) {
            ordered
        } else {
            val orderedPairs = ordered.zipWithNext().toMutableList().apply {
                add(ordered.last() to ordered.first())
            }
            val matchingPair = orderedPairs.firstOrNull { (from, to) ->
                !sameChord(from.shapeChord, to.shapeChord) &&
                    sameChord(from.shapeChord, onlyTransition.fromChord) &&
                    sameChord(to.shapeChord, onlyTransition.toChord)
            }
            require(matchingPair != null) { "所选方向性切换不属于当前曲谱顺序或循环边界。" }
            listOf(matchingPair.first, matchingPair.second)
        }
        require(selected.size <= 512) { "当前范围超过 512 个播放步骤，请改为选择单个段落练习。" }
        val eventById = project.sections.flatMap { it.rows }.flatMap { it.chordEvents }.associateBy { it.id }
        val lyricByRowId = project.sections.flatMap { it.rows }.associate { it.id to it.lyricText }
        val base = progressions.createDraft(selected.joinToString(" ") { it.shapeChord }, "${project.title} · 曲谱练习")
        val precise = onlyTransition == null && project.timingState != SongTimingState.UNTYPED &&
            selected.all { eventById[it.eventId]?.durationBeats != null }
        val measureBeats = project.timeSignature.substringBefore('/').toDoubleOrNull() ?: 4.0
        val progression = base.copy(
            id = "song-practice-${java.util.UUID.randomUUID()}",
            name = "${project.title} · ${sectionId?.let { id -> project.sections.first { it.id == id }.name } ?: "整首"}",
            keySignature = arrangement.shapeKey.takeUnless { it == "未设置" || it == "无法计算" }.orEmpty(),
            timeSignature = project.timeSignature,
            bpm = project.bpm,
            loop = true,
            steps = base.steps.mapIndexed { index, step ->
                val rendered = selected[index]
                val duration = if (precise) eventById[rendered.eventId]?.durationBeats ?: measureBeats else measureBeats
                step.copy(
                    chordSymbol = rendered.shapeChord,
                    voicingId = rendered.voicing?.id?.takeIf { id -> step.voicingOptions.any { it.id == id } }
                        ?: step.voicingId,
                    beats = duration,
                    order = index,
                )
            },
            saved = false,
            notes = "songId=${project.id};sectionId=${sectionId.orEmpty()}",
        )
        val transitions = if (onlyTransition != null) {
            listOf(onlyTransition)
        } else {
            extractor.extract(project, arrangement, sectionId, includeLoopBoundary = true, unique = true)
        }
        SongPracticePreparation(
            project = project,
            sectionId = sectionId,
            sectionName = sectionId?.let { id -> project.sections.first { it.id == id }.name } ?: "整首",
            progression = progression,
            transitions = transitions,
            lyricLines = selected.map { lyricByRowId[it.rowId].orEmpty() },
            preciseTiming = precise,
            timingMessage = if (precise) {
                "当前拍数可靠：启用当前和弦高亮、下一和弦预告、节拍同步与自动播放。"
            } else {
                "当前拍数不可靠：只启用手动和弦切换、手动滚动与节拍器，不宣称精确同步。"
            },
        )
    }

    override suspend fun savePracticeRun(
        songId: String,
        sectionId: String?,
        mode: SongPracticeMode,
        bpm: Int,
        startedAt: Long,
        endedAt: Long,
        actualDurationSeconds: Int,
        completed: Boolean,
        difficultTransitions: List<SongTransition>,
        runId: String?,
        loopEnabled: Boolean,
        showFretboard: Boolean,
    ): SongPracticeRun = withContext(ioDispatcher) {
        val project = projectStore.read(songId) ?: error("这份曲谱不存在或已被删除。")
        if (sectionId != null) require(project.sections.any { it.id == sectionId }) { "所选段落不存在。" }
        require(mode == SongPracticeMode.PERFORMANCE || difficultTransitions.isEmpty()) {
            "专项切换结果不能写成用户报告困难点。"
        }
        if (difficultTransitions.isNotEmpty()) {
            val engine = requireNotNull(arrangementEngine)
            val extractor = requireNotNull(transitionExtractor)
            val arrangement = engine.arrange(
                project,
                practicePreferencesStore?.load() ?: PracticePreferences.defaults(),
                userChordStore?.favorites().orEmpty().toSet(),
                transitionAttemptStore?.list().orEmpty(),
            )
            val allowed = extractor.extract(project, arrangement, sectionId, includeLoopBoundary = true, unique = true)
            require(difficultTransitions.all { wanted ->
                allowed.any { sameChord(it.fromChord, wanted.fromChord) && sameChord(it.toChord, wanted.toChord) }
            }) { "困难切换必须来自本次演奏范围。" }
        }
        val run = SongPracticeRun(
            id = runId?.trim()?.ifBlank { null } ?: java.util.UUID.randomUUID().toString(),
            songId = project.id,
            sectionId = sectionId,
            mode = mode,
            bpm = bpm.coerceIn(40, 240),
            transposeSemitones = project.transposeSemitones,
            capoFret = project.capoFret,
            startedAt = startedAt.coerceAtLeast(0L),
            endedAt = endedAt.coerceAtLeast(startedAt),
            actualDurationSeconds = actualDurationSeconds.coerceIn(0, 86_400),
            completed = completed,
            reportedDifficultTransitions = difficultTransitions.distinct(),
            loopEnabled = loopEnabled,
            showFretboard = showFretboard,
            selectedVoicingIds = projectVoicingSnapshot(project),
        )
        runStore.save(run)
        if (mode == SongPracticeMode.PERFORMANCE) {
            difficultTransitions.distinct().forEach { transition ->
                difficultyStore.save(
                    UserReportedDifficulty.create(
                        songId = songId,
                        sectionId = sectionId,
                        fromChord = transition.fromChord,
                        toChord = transition.toChord,
                        reportedAt = endedAt,
                    ),
                )
            }
        }
        run
    }

    override suspend fun homeTasks(songAccompanimentGoal: Boolean): List<SongHomeTask> = withContext(ioDispatcher) {
        if (!songAccompanimentGoal) return@withContext emptyList()
        val projects = projectStore.list()
        if (projects.isEmpty()) return@withContext emptyList()
        val runs = runStore.list()
        val difficulties = difficultyStore.list()
        val projectsById = projects.associateBy { it.id }
        val latestRun = runs.filter { it.songId in projectsById }
            .maxWithOrNull(compareBy<SongPracticeRun> { it.startedAt }.thenBy { it.id })
        val latestProject = latestRun?.let { run -> projectsById[run.songId] } ?: projects.first()
        val tasks = mutableListOf<SongHomeTask>()
        if (latestRun != null) {
            val sectionName = latestRun.sectionId?.let { id -> latestProject.sections.firstOrNull { it.id == id }?.name } ?: "整首"
            tasks += SongHomeTask(
                SongHomeTaskType.CONTINUE_RECENT,
                "继续《${latestProject.title}》的$sectionName",
                "上次练习 ${latestRun.bpm} BPM · ${if (latestRun.mode == SongPracticeMode.PERFORMANCE) "连续演奏" else "专项切换"}",
                latestProject.id,
                latestRun.sectionId,
                latestRun.mode,
                latestRun.bpm,
                latestRun.transposeSemitones,
                latestRun.capoFret,
                latestRun.loopEnabled,
                latestRun.showFretboard,
                timeSignature = latestProject.timeSignature,
                selectedVoicingIds = latestRun.selectedVoicingIds,
            )
        }

        val recentDifficulty = difficulties
            .filter { !it.resolved && it.songId == latestProject.id }
            .maxWithOrNull(compareBy<UserReportedDifficulty> { it.reportedAt }.thenBy { it.id })
        recentDifficulty?.let { difficulty ->
            val sectionName = difficulty.sectionId?.let { id -> latestProject.sections.firstOrNull { it.id == id }?.name }
            tasks += SongHomeTask(
                SongHomeTaskType.REVIEW_REPORTED_DIFFICULTY,
                "先练 ${difficulty.fromChord} → ${difficulty.toChord}，再继续${sectionName ?: "曲谱"}",
                "这是你在最近连续演奏后主动标记的困难切换",
                latestProject.id,
                difficulty.sectionId,
                SongPracticeMode.GUIDED_TRANSITION,
                latestRun?.bpm ?: latestProject.bpm,
                latestProject.transposeSemitones,
                latestProject.capoFret,
                transition = SongTransition(difficulty.fromChord, difficulty.toChord),
                timeSignature = latestProject.timeSignature,
                selectedVoicingIds = projectVoicingSnapshot(latestProject),
            )
        }

        if (arrangementEngine != null && transitionExtractor != null) {
            val preferences = practicePreferencesStore?.load() ?: PracticePreferences.defaults()
            val arrangement = arrangementEngine.arrange(
                latestProject,
                preferences,
                userChordStore?.favorites().orEmpty().toSet(),
                transitionAttemptStore?.list().orEmpty(),
            )
            val songTransitions = transitionExtractor.extract(latestProject, arrangement, null, true, true)
            val attempts = transitionAttemptStore?.list().orEmpty()
            val weakest = songTransitions.mapNotNull { transition ->
                val matching = attempts.filter {
                    transitionExtractor.equivalent(it.fromChord, transition.fromChord) &&
                        transitionExtractor.equivalent(it.toChord, transition.toChord)
                }.sortedByDescending { it.timestampEpochMillis }.take(20)
                matching.takeIf { it.size >= 5 }?.let { values ->
                    transition to values.count { it.success }.toDouble() / values.size
                }
            }.minWithOrNull(compareBy<Pair<SongTransition, Double>> { it.second }
                .thenBy { it.first.fromChord }.thenBy { it.first.toChord })
            weakest?.let { (transition, rate) ->
                tasks += SongHomeTask(
                    SongHomeTaskType.REVIEW_GLOBAL_WEAKNESS,
                    "复习曲谱中的 ${transition.fromChord} → ${transition.toChord}",
                    "全局方向性统计最近成功率 ${"%.0f".format(rate * 100)}%",
                    latestProject.id,
                    null,
                    SongPracticeMode.GUIDED_TRANSITION,
                    (latestRun?.bpm ?: latestProject.bpm).let { if (rate < 0.75) it - 5 else it }.coerceIn(40, 240),
                    latestProject.transposeSemitones,
                    latestProject.capoFret,
                    transition = transition,
                    timeSignature = latestProject.timeSignature,
                    selectedVoicingIds = projectVoicingSnapshot(latestProject),
                )
            }

            val practicedSections = runs.filter { it.songId == latestProject.id }.mapNotNull { it.sectionId }.toSet()
            latestProject.sections.sortedBy { it.order }.firstOrNull { it.id !in practicedSections }?.let { section ->
                tasks += SongHomeTask(
                    SongHomeTaskType.CONTINUE_UNFINISHED_SECTION,
                    "继续《${latestProject.title}》的${section.name}",
                    "这个段落尚无曲谱练习记录",
                    latestProject.id,
                    section.id,
                    SongPracticeMode.PERFORMANCE,
                    latestProject.bpm,
                    latestProject.transposeSemitones,
                    latestProject.capoFret,
                    timeSignature = latestProject.timeSignature,
                    selectedVoicingIds = projectVoicingSnapshot(latestProject),
                )
            }

            arrangement.renderedChords.distinctBy { it.shapeChord }.firstOrNull { rendered ->
                rendered.availableVoicings.none { it.familiar }
            }?.let { rendered ->
                tasks += SongHomeTask(
                    SongHomeTaskType.LEARN_NEW_CHORD,
                    "学习《${latestProject.title}》中的 ${rendered.shapeChord}",
                    "曲谱需要这个手型，但尚未标记熟悉指法",
                    latestProject.id,
                    rendered.sectionId,
                    null,
                    latestProject.bpm,
                    latestProject.transposeSemitones,
                    latestProject.capoFret,
                    timeSignature = latestProject.timeSignature,
                    selectedVoicingIds = projectVoicingSnapshot(latestProject),
                )
            }
        }

        runs.filter { it.songId == latestProject.id && it.completed && it.sectionId != null }
            .maxByOrNull { it.bpm }
            ?.let { stable ->
                val section = latestProject.sections.firstOrNull { it.id == stable.sectionId }
                tasks += SongHomeTask(
                    SongHomeTaskType.PERFORM_STABLE_SECTION,
                    "完整演奏${section?.name ?: "稳定段落"}",
                    "该段落曾在 ${stable.bpm} BPM 完整演奏，可继续巩固",
                    latestProject.id,
                    stable.sectionId,
                    SongPracticeMode.PERFORMANCE,
                    stable.bpm,
                    stable.transposeSemitones,
                    stable.capoFret,
                    stable.loopEnabled,
                    stable.showFretboard,
                    timeSignature = latestProject.timeSignature,
                    selectedVoicingIds = stable.selectedVoicingIds,
                )
            }
        tasks.distinctBy { it.type }.take(6)
    }

    override suspend fun restorePracticeConfiguration(
        songId: String,
        bpm: Int,
        transposeSemitones: Int,
        capoFret: Int,
        selectedVoicingIds: Map<String, String>,
        restoreVoicings: Boolean,
    ): SongProject = withContext(ioDispatcher) {
        val project = projectStore.read(songId) ?: throw IllegalArgumentException("找不到要继续练习的曲谱。")
        val restoredSections = if (restoreVoicings) {
            project.sections.map { section ->
                section.copy(
                    rows = section.rows.map { row ->
                        row.copy(
                            chordEvents = row.chordEvents.map { event ->
                                event.copy(selectedVoicingId = selectedVoicingIds[event.id])
                            },
                        )
                    },
                )
            }
        } else project.sections
        projectStore.save(
            project.copy(
                bpm = bpm.coerceIn(40, 240),
                transposeSemitones = transposeSemitones.coerceIn(-11, 11),
                capoFret = capoFret.coerceIn(0, 12),
                sections = restoredSections,
                updatedAt = clock().coerceAtLeast(project.createdAt),
            ),
        )
    }

    private fun buildProgress(
        project: SongProject,
        runs: List<SongPracticeRun>,
        difficulties: List<UserReportedDifficulty>,
    ): SongProgressStats {
        val now = clock()
        val sevenDaysAgo = now - 7L * 86_400_000L
        val completed = runs.filter { it.completed }
        val practicedSectionIds = runs.mapNotNull { it.sectionId }.toSet()
        return SongProgressStats(
            totalPracticeSeconds = runs.sumOf { it.actualDurationSeconds },
            sevenDayPracticeSeconds = runs.filter { it.startedAt >= sevenDaysAgo }.sumOf { it.actualDurationSeconds },
            sectionPracticeCounts = project.sections.associate { section ->
                section.name to runs.count { it.sectionId == section.id }
            },
            recentBpm = runs.maxByOrNull { it.startedAt }?.bpm,
            highestCompletedBpm = completed.maxOfOrNull { it.bpm },
            unresolvedDifficultyCount = difficulties.count { !it.resolved },
            resolvedDifficultyCount = difficulties.count { it.resolved },
            unpracticedSectionCount = project.sections.count { it.id !in practicedSectionIds },
            lastCompletePerformanceAt = completed.filter { it.mode == SongPracticeMode.PERFORMANCE }
                .maxOfOrNull { it.startedAt },
            completionCount = completed.size,
        )
    }

    private fun sameChord(first: String, second: String): Boolean {
        val extractor = transitionExtractor ?: return first == second
        return extractor.equivalent(first, second)
    }

    private fun projectVoicingSnapshot(project: SongProject): Map<String, String> = buildMap {
        project.sections.forEach { section ->
            section.rows.forEach { row ->
                row.chordEvents.forEach { event -> event.selectedVoicingId?.let { put(event.id, it) } }
            }
        }
    }
}

/** Process-death-safe cache for the import preview; saved songs still use SongProjectStore. */
class SongImportDraftStore(private val file: File) {
    @Synchronized
    fun write(draft: SongImportDraft) {
        val target = file.absoluteFile
        target.parentFile?.mkdirs()
        val temporary = File(target.path + ".tmp")
        FileOutputStream(temporary).use { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(draft.timeSignature)
                writeLargeString(output, draft.title)
                writeLargeString(output, draft.artist)
                writeLargeString(output, draft.originalText)
                output.writeInt(draft.lineOverrides.size)
                draft.lineOverrides.toSortedMap().forEach { (lineNumber, role) ->
                    output.writeInt(lineNumber)
                    output.writeUTF(role.name)
                }
                output.flush()
                stream.fd.sync()
            }
        }
        val backup = File(target.path + ".bak")
        if (backup.exists() && !backup.delete()) throw IOException("无法轮换曲谱导入草稿备份。")
        if (target.exists() && !target.renameTo(backup)) throw IOException("无法备份曲谱导入草稿。")
        if (!temporary.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("无法保存曲谱导入草稿。")
        }
    }

    @Synchronized
    fun read(): PersistedSongImportDraft? {
        val candidates = listOf(file, File(file.path + ".bak")).filter(File::isFile)
        if (candidates.isEmpty()) return null
        var firstError: IOException? = null
        candidates.forEach { target ->
            try {
                return readOne(target)
            } catch (error: IOException) {
                if (firstError == null) firstError = error else error.addSuppressed(requireNotNull(firstError))
            }
        }
        throw requireNotNull(firstError)
    }

    private fun readOne(target: File): PersistedSongImportDraft = FileInputStream(target).use { stream ->
            DataInputStream(stream).use { input ->
                if (input.readInt() != MAGIC) {
                    throw IOException("曲谱导入草稿版本无效。")
                }
                val version = input.readInt()
                if (version !in 1..VERSION) throw IOException("曲谱导入草稿版本无效。")
                val signature = input.readUTF()
                val title = readLargeString(input)
                val artist = readLargeString(input)
                val text = readLargeString(input)
                val overrides = if (version >= 2) {
                    val count = input.readInt()
                    if (count !in 0..10_000) throw IOException("曲谱导入修正数量无效。")
                    buildMap {
                        repeat(count) {
                            val line = input.readInt()
                            val roleName = input.readUTF()
                            val role = enumValues<SongParseLineRole>().firstOrNull { it.name == roleName }
                                ?: throw IOException("曲谱导入修正类型无效。")
                            if (put(line, role) != null) throw IOException("曲谱导入修正行重复。")
                        }
                    }
                } else emptyMap()
                PersistedSongImportDraft(title, artist, text, signature, overrides)
            }
        }

    @Synchronized
    fun clear() {
        listOf(file, File(file.path + ".bak"), File(file.path + ".tmp")).forEach { target ->
            if (target.exists() && !target.delete()) throw IOException("无法清除曲谱导入草稿。")
        }
    }

    companion object {
        private const val MAGIC = 0x4B325349 // K2SI
        private const val VERSION = 2
        private const val MAX_BYTES = 800_000

        fun defaultFile(filesDir: File): File = File(filesDir, "song-import-draft-v1.bin")

        private fun writeLargeString(output: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BYTES) { "曲谱导入文本超过缓存限制。" }
            output.writeInt(bytes.size)
            output.write(bytes)
        }

        private fun readLargeString(input: DataInputStream): String {
            val size = input.readInt()
            if (size !in 0..MAX_BYTES) throw IOException("曲谱导入草稿长度无效。")
            return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
        }
    }
}

data class PersistedSongImportDraft(
    val title: String,
    val artist: String,
    val originalText: String,
    val timeSignature: String,
    val lineOverrides: Map<Int, SongParseLineRole> = emptyMap(),
) {
    fun toDraft(parseResult: SongSheetParseResult): SongImportDraft = SongImportDraft(
        title = title,
        artist = artist,
        originalText = originalText,
        timeSignature = timeSignature,
        parseResult = parseResult,
        lineOverrides = lineOverrides,
    )
}
