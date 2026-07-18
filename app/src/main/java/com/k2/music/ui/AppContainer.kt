package com.k2.music.ui

import android.app.Application
import com.k2.music.AiResultCache
import com.k2.music.AiResultValidator
import com.k2.music.AiService
import com.k2.music.AiSettingsStore
import com.k2.music.CapoAssistant
import com.k2.music.ChordAudioPlayer
import com.k2.music.ChordIdentifier
import com.k2.music.ChordRepository
import com.k2.music.ChordTransposer
import com.k2.music.CustomVoicingStore
import com.k2.music.PracticePlanDraftStore
import com.k2.music.LastPracticeConfigStore
import com.k2.music.PracticePreferencesStore
import com.k2.music.PracticeRecordStore
import com.k2.music.TransitionAttemptStore
import com.k2.music.ProgressionPresetRepository
import com.k2.music.ProgressionStore
import com.k2.music.UserChordStore
import com.k2.music.VoicingRecommendationEngine
import com.k2.music.FullBackupManager
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.song.SongProjectStore
import com.k2.music.song.SongPracticeRunStore
import com.k2.music.song.UserReportedDifficultyStore
import com.k2.music.song.RepositorySongChordResolver
import com.k2.music.song.SongSheetParser
import com.k2.music.song.SongArrangementEngine
import com.k2.music.song.SongTransitionExtractor
import com.k2.music.ui.learning.LearningProfileStore
import com.k2.music.ui.gateway.DefaultChordCatalogGateway
import com.k2.music.ui.gateway.DefaultUserLibraryGateway
import com.k2.music.ui.gateway.PlaybackController
import com.k2.music.ui.gateway.DefaultRecognitionGateway
import com.k2.music.ui.gateway.DefaultTransposeGateway
import com.k2.music.ui.gateway.DefaultProgressionGateway
import com.k2.music.ui.gateway.DefaultProgressionTransport
import com.k2.music.ui.gateway.DefaultPracticeGateway
import com.k2.music.ui.gateway.DefaultAiGateway
import com.k2.music.ui.gateway.DefaultExportGateway
import com.k2.music.ui.song.DefaultSongGateway
import com.k2.music.ui.song.SongImportDraftStore
import java.io.File
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RepositoryLoadState {
    data object Loading : RepositoryLoadState
    data class Ready(val services: CoreServices) : RepositoryLoadState
    data class Error(val message: String) : RepositoryLoadState
}

/** Shared instances of the existing Java core. No music-theory logic lives here. */
class CoreServices internal constructor(
    application: Application,
    val learningProfileStore: LearningProfileStore,
    appPreferences: AppPreferences,
) : Closeable {
    val chordRepository = ChordRepository()
    val userChordStore = UserChordStore(application, chordRepository)
    val customVoicingStore = CustomVoicingStore(application)
    val chordAudioPlayer = ChordAudioPlayer()
    val chordIdentifier = ChordIdentifier(chordRepository)
    val chordTransposer = ChordTransposer()
    val capoAssistant = CapoAssistant(chordTransposer)
    val progressionStore = ProgressionStore(ProgressionStore.defaultFile(application.filesDir))
    val progressionDraftStore = ProgressionStore(File(application.filesDir, "progression-drafts-v1.bin"))
    val progressionPresets = ProgressionPresetRepository()
    val practiceRecordStore = PracticeRecordStore(PracticeRecordStore.defaultFile(application.filesDir))
    val transitionAttemptStore =
        TransitionAttemptStore(TransitionAttemptStore.defaultFile(application.filesDir))
    val practicePreferencesStore =
        PracticePreferencesStore(PracticePreferencesStore.defaultFile(application.filesDir))
    val practicePlanDraftStore = PracticePlanDraftStore(application)
    val lastPracticeConfigStore =
        LastPracticeConfigStore(LastPracticeConfigStore.defaultFile(application.filesDir))
    val songProjectStore = SongProjectStore(SongProjectStore.defaultFile(application.filesDir))
    val songPracticeRunStore = SongPracticeRunStore(SongPracticeRunStore.defaultFile(application.filesDir))
    val songDifficultyStore =
        UserReportedDifficultyStore(UserReportedDifficultyStore.defaultFile(application.filesDir))
    val songImportDraftStore = SongImportDraftStore(SongImportDraftStore.defaultFile(application.filesDir))
    val voicingRecommendationEngine = VoicingRecommendationEngine()
    val aiSettingsStore = AiSettingsStore(application)
    val aiResultCache = AiResultCache(application)
    val aiService = AiService(application)
    val aiResultValidator = AiResultValidator(chordRepository)
    val chordCatalogGateway = DefaultChordCatalogGateway(chordRepository)
    val userLibraryGateway = DefaultUserLibraryGateway(
        userChordStore,
        customVoicingStore,
        practicePreferencesStore,
    )
    private var chordPlaybackReference: PlaybackController? = null
    val progressionTransport = DefaultProgressionTransport(
        chordRepository,
        customVoicingStore,
        chordAudioPlayer,
        beforeStart = {
            chordPlaybackReference?.stop()
        },
    )
    val playbackController = PlaybackController(
        chordAudioPlayer,
        beforePlay = { progressionTransport.stop() },
    ).also { chordPlaybackReference = it }
    val recognitionGateway = DefaultRecognitionGateway(chordIdentifier, chordRepository, customVoicingStore)
    val transposeGateway = DefaultTransposeGateway(chordTransposer, capoAssistant)
    val progressionGateway = DefaultProgressionGateway(
        progressionStore,
        progressionDraftStore,
        progressionPresets,
        chordRepository,
        customVoicingStore,
        chordTransposer,
        practicePreferencesStore,
        voicingRecommendationEngine,
    )
    val aiGateway = DefaultAiGateway(
        aiService,
        aiSettingsStore,
        aiResultValidator,
        chordRepository,
        aiResultCache,
        practicePlanDraftStore,
    )
    val exportGateway = DefaultExportGateway(
        application,
        chordRepository,
        customVoicingStore,
        userChordStore,
    )
    val songArrangementEngine = SongArrangementEngine(
        chordRepository,
        chordTransposer,
        capoAssistant,
        voicingRecommendationEngine,
    ) { symbol -> customVoicingStore.forChord(symbol).map { it.toVoicing() } }
    val songTransitionExtractor = SongTransitionExtractor(RepositorySongChordResolver(chordRepository))
    val songGateway = DefaultSongGateway(
        songProjectStore,
        songPracticeRunStore,
        songDifficultyStore,
        SongSheetParser(RepositorySongChordResolver(chordRepository)),
        songImportDraftStore,
        songArrangementEngine,
        practicePreferencesStore,
        userChordStore,
        transitionAttemptStore,
        progressionGateway,
        songTransitionExtractor,
    )
    val practiceGateway = DefaultPracticeGateway(
        practiceRecordStore,
        transitionAttemptStore,
        practicePreferencesStore,
        practicePlanDraftStore,
        progressionGateway,
        lastPracticeConfigStore,
        songGateway,
    )
    val fullBackupManager = FullBackupManager(
        appPreferences,
        learningProfileStore,
        userChordStore,
        customVoicingStore,
        progressionStore,
        progressionDraftStore,
        practicePreferencesStore,
        practiceRecordStore,
        transitionAttemptStore,
        aiSettingsStore,
        songProjectStore,
        songPracticeRunStore,
        songDifficultyStore,
    )

    override fun close() {
        progressionTransport.close()
        playbackController.close()
        aiService.cancelActive()
    }
}

/** Application-scoped dependency container used by explicit ViewModel factories. */
class AppContainer(private val application: Application) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _repositoryState = MutableStateFlow<RepositoryLoadState>(RepositoryLoadState.Loading)
    private var loadJob: Job? = null

    val repositoryState: StateFlow<RepositoryLoadState> = _repositoryState.asStateFlow()
    val appPreferences = AppPreferences(application)
    val learningProfileStore = LearningProfileStore(application)

    init {
        retryRepositoryLoad()
    }

    fun retryRepositoryLoad() {
        loadJob?.cancel()
        _repositoryState.value = RepositoryLoadState.Loading
        loadJob = scope.launch {
            runCatching { CoreServices(application, learningProfileStore, appPreferences) }
                .onSuccess { services ->
                    (_repositoryState.value as? RepositoryLoadState.Ready)?.services?.close()
                    _repositoryState.value = RepositoryLoadState.Ready(services)
                }
                .onFailure {
                    _repositoryState.value = RepositoryLoadState.Error(
                        "和弦数据暂时无法加载，请重试。离线数据与安全设置均未被修改。",
                    )
                }
        }
    }

    fun pauseForLifecycle() {
        val services = (_repositoryState.value as? RepositoryLoadState.Ready)?.services ?: return
        services.playbackController.stop()
        services.progressionTransport.pauseForLifecycle()
        services.aiService.cancelActive()
    }

    override fun close() {
        loadJob?.cancel()
        (_repositoryState.value as? RepositoryLoadState.Ready)?.services?.close()
        scope.cancel()
    }
}
