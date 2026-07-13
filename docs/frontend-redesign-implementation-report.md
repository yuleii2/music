# 前端重构实施与验收记录

日期：2026-07-11
项目：`D:\K2\music`
依据：`docs/frontend-redesign-execution-spec.md`

## Phase 0 交付：基线与保护

### 已完成

- 已完整阅读执行规格、`README.md`、`docs/requirements.md` 与原实现报告。
- 已记录并保护重构前工作区：`main` 分支存在大量已修改与未跟踪的 Java 核心、旧 View 前端、资源、测试和文档；后续全部视为用户已有工作，不执行重置、覆盖或清理。
- 已运行规格要求的完整基线验证。
- 已检查 Android SDK、连接设备与 AVD 状态。

### 验证

- 命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug --stacktrace --console=plain`
- 结果：成功，退出码 0，用时 41.3 秒。
- 离线测试：`ChordRepositorySmokeTest`、`ChordTheoryParserTest`、`SvgVoicingRendererTest`、`AdvancedMusicToolsSmokeTest`、`Phase3CoreSmokeTest` 均由 `offlineCoreJvmTest` 执行成功。
- Lint：0 error，46 warning。警告均为重构前基线，主要来自旧 Java View 的硬编码文本与 `SetTextI18n`。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，1,155,940 字节。
- 基线 APK SHA-256：`3D1A6DDA07DD11B62550A66BCCCC9154820EF3ECFA1CB19833C41FD91DA598B8`。
- 2026-07-11 续接检查再次运行同一命令，退出码 0，用时 95.5 秒；当时生成的 18 个 Kotlin/JVM 测试均为 0 failure，Lint 与 APK 打包成功。续接 APK 为 20,156,079 字节，SHA-256 `82F39C7403A9235ACA261C89DF9E593E645317E46DFCD029EA9FB5118F10528E`。

### 视觉/性能证据

- Android SDK 可用。
- 重构开始时没有已连接 Android 设备，也没有已配置 AVD；因此 Phase 0 无法执行真机/模拟器视觉与交互基线。
- 当前构建只提供 Debug APK，不构成 release/profileable 性能证据。

### 风险与遗留

- 46 条既有 Lint warning 记录为重构前债务；新 Compose 代码不得增加 Lint error，旧前端删除后应同步消除相关警告。
- 后续视觉验收需要建立模拟器或连接设备；在获得真实截图/交互证据前不宣称视觉验收完成。

## Phase 1 交付：Compose 基础设施

### 已完成

- 使用 AGP 9.2 内置 Kotlin 与 Kotlin/Compose compiler 2.3.21 建立 Kotlin 编译链。
- 使用稳定 Compose BOM 2026.06.01、Material 3、Navigation Compose 2.9.8、Lifecycle 2.11.0、Material 3 Adaptive 1.2.0。
- 保持 `minSdk 23`、`targetSdk 36`、`applicationId com.k2.music` 与版本号不变；仅将 `compileSdk` 提升到 37，以满足最新稳定 Lifecycle 的 AAR 元数据要求。
- 新增 `ComposeMainActivity` 并将 Launcher 临时切换至该 Activity；旧 `MainActivity` 与全部旧 Page 继续保留、编译。
- 建立应用级 `AppContainer`、后台 Repository 加载状态、共享 Java 核心实例与生命周期停止入口。
- 建立显式 `MusicViewModelFactory`，统一为后续功能 ViewModel 提供 `SavedStateHandle`。
- 建立浅色/深色设计令牌、系统字体排版、圆角、额外能量色，以及完整/简化/关闭三级动效令牌。
- 建立五项手机底部导航、600dp 起 Navigation Rail、各一级 Tab 状态恢复、预测返回开关。
- 建立启动 Splash、加载骨架、错误页、空状态、InlineMessage、Snackbar 宿主与统一按钮。

### 主要文件

- `ComposeMainActivity.kt`
- `ui/AppContainer.kt`, `ui/MusicViewModelFactory.kt`, `ui/MusicApp.kt`
- `ui/navigation/*`
- `ui/theme/*`
- `ui/components/*`
- `ui/preferences/AppPreferences.kt`

### 验证

- 命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug --offline --stacktrace --console=plain`
- 结果：`BUILD SUCCESSFUL`，61 个任务，24 秒（依赖首次在线解析后复验）。
- 既有离线套件：5/5 通过。
- 新增单元测试：`MotionTokensTest` 3/3 通过。
- Lint：0 error，45 warning；未新增 Compose Lint error。
- Debug APK：20,155,843 字节，Phase 1 初次完整构建 SHA-256 为 `94BC4666462E24D114FD416657A4ED56F9C10E42758AF271CF153F3CCBBEA9DF`。

### 视觉/性能证据

- 代码层已覆盖浅色/深色、字体随系统缩放、紧凑底栏与 600dp Rail 分支。
- 三级动画令牌由单元测试验证；系统 Animator Duration Scale 为 0 时会强制关闭应用动画。
- 当前仍无设备/AVD，本阶段不声明真实视觉、TalkBack 或帧性能验收完成。

### 风险与遗留

- `targetSdk` 按规格保持 36，因此 Lint 保留一条 `OldTargetApi` 提示；不擅自改到 37。
- 旧 Java View 警告仍存在，按规格在 P0 全面对齐后再随旧 UI 删除清理。

## Phase 2 交付：首页、和弦库与详情

### 已完成

- 建立 `ChordCatalogGateway`、`UserLibraryGateway` 与共享 `PlaybackController`，后台包装现有 Java 仓库、收藏/历史、自定义指法、练习熟悉度和音频核心。
- 首页实现全屏搜索、140ms 防抖与 `mapLatest` 旧任务取消、合法和弦 IME 直达、非法输入就地说明、最近查看移除/撤销、入门推荐和三项快捷工具。
- 和弦库实现全部/收藏/最近/自定义四分段、固定搜索区、可恢复筛选、Adaptive `LazyVerticalGrid`、稳定 key/contentType、长按多选与上下文操作框架。
- 详情实现理论摘要、多指法 HorizontalPager、按法选择器、收藏、熟悉度、自定义指法安全删除、按法/组成音试听和固定底部操作栏。
- 新增纯 Compose `FretboardCanvas`：静态弦/品缓存、六弦标记位置动画、O/X 交叉透明度、横按、手指编号与完整 TalkBack 文本等价。
- 使用 Navigation 2 `SharedTransitionLayout` 连接卡片容器、和弦名与指板；动画简化/关闭设置会禁用空间共享元素并安全退化。
- slash chord 使用 UTF-8 百分号编码查询参数，`G/B`、`D/F#`、Unicode 和弦符号均有往返测试。
- 旧 Java View 前端仍保留且可编译。

### 主要文件

- `ui/gateway/ChordGateways.kt`
- `ui/model/ChordUiModels.kt`
- `ui/home/*`, `ui/library/*`, `ui/detail/*`
- `ui/components/FretboardCanvas.kt`, `FretboardGeometry.kt`, `ChordCard.kt`
- `ui/navigation/RouteCodec.kt`, `SharedElements.kt`

### 验证

- 命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug assembleDebugAndroidTest --offline --stacktrace --console=plain`
- 结果：`BUILD SUCCESSFUL`，91 个任务，65 秒。
- 既有离线套件：5/5 通过。
- Kotlin 单元测试：13/13 通过（Home/Library/Detail 状态、140ms debounce、segment 恢复、slash route、指板几何、动效令牌）。
- Compose instrumentation：新增五项导航与 slash chord 搜索场景，AndroidTest APK 编译成功。
- Lint：0 error，45 warning。
- Debug APK：20,155,961 字节，SHA-256 `9089D256B2DD58C022986A8C95D7362992A4D31D5AE1D1582CB7782DE4D5527F`。

### 视觉/性能证据

- 和弦库只使用 Lazy Grid；字体缩放达到 1.6 倍时最小卡宽变为 280dp，自然降为单列。
- 指板静态几何使用 `drawWithCache`，动态层只绘制线、圆和圆角矩形；几何纯函数测试通过。
- 共享元素在来源离屏时由 Compose Navigation 自动退化为普通目的地转场。
- 当前无设备/AVD，因此 AndroidTest 未执行，真实截图、TalkBack、字体 200% 与帧数据仍未验收。

### 风险与遗留

- 批量导出与 AI 入口在详情/多选中已占位但尚未接入后台任务，按 Phase 5 完成。
- 展开窗口的列表—详情双栏属于 P1，留到 P0 功能域完成后补齐。

## Phase 3 交付：工具、识别、移调与变调夹

### 已完成

- 工具首页使用自适应 Lazy Grid 展示反向识别、移调/变调夹、和弦进行、节拍器和 AI，并持久记录最近使用的工具；所有离线工具入口不依赖 AI。
- `RecognitionGateway` 在后台复用 `ChordIdentifier` 与 `CustomVoicingStore`；交互指板和音符输入各自保存状态，120ms 防抖使用 `mapLatest` 取消旧计算。
- 交互指板提供品位、空弦、闷弦、擦除、清空、可见品位移动和逐弦按钮替代操作；清空可从 Snackbar 撤销。
- 候选最多五项，显示得分、完全匹配/转位/缺失音/额外音等文字证据，并支持试听、详情、收藏和保存自定义指法。
- 自定义指法 BottomSheet 收集所属和弦、名称、六弦手指编号、起始品位和备注；保存前继续经过本地和弦与实际指板匹配校验。
- `TransposeGateway` 复用 `ChordTransposer` 与 `CapoAssistant`；移调和变调夹拆分为两个分段页，支持 `-11…+11`、升降号偏好、slash bass、实时结果、复制、详情和带入进行编辑器。
- 变调夹覆盖“目标实际和弦 + 希望形状查找 0–12 品”和“已知形状 + capo 计算实际声音”两种模式，查找结果以形状/capo/实际声音三列展示。

### 主要文件

- `ui/gateway/ToolGateways.kt`
- `ui/workbench/WorkbenchScreen.kt`
- `ui/recognition/RecognitionViewModel.kt`, `RecognitionScreen.kt`, `EditableFretboardCanvas.kt`
- `ui/transpose/TransposeViewModel.kt`, `TransposeScreen.kt`
- `ui/preferences/AppPreferences.kt`
- `ui/navigation/MusicNavHost.kt`
- `ui/recognition/RecognitionViewModelTest.kt`, `ui/transpose/TransposeViewModelTest.kt`

### 验证

- 命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug assembleDebugAndroidTest --stacktrace --console=plain`
- 结果：`BUILD SUCCESSFUL`，91 个任务，用时 49 秒。
- 既有离线套件：和弦数据、理论、SVG、高级工具和 Phase 3 核心 smoke test 全部通过。
- Kotlin 单元测试：19/19 通过，0 failure、0 skipped；新增覆盖识别候选共享收藏状态。
- Compose instrumentation APK 编译成功；工具页到识别/移调的导航用例已存在，等待设备执行。
- Lint：0 error，45 warning；警告仍来自旧 View 基线。
- Debug APK：20,156,079 字节，SHA-256 `D12D114939FC85545DA340FC7468FEE41B2577D0FE185CEBE28FDF0CBD39CEB0`。

### 视觉/性能证据

- 工具页和候选列表均使用 Lazy 布局；识别与移调计算在 Default dispatcher，旧输入任务由 `mapLatest` 取消，UI 线程不执行乐理计算。
- 指板有完整文字 semantics，所有点按/滑动能力都有可见按钮替代；候选状态不只依赖颜色。
- 当前 PATH 中仍找不到 `adb`/`emulator`，因此本阶段尚未声明设备截图、TalkBack 或实际帧性能通过；Phase 6 将继续定位 SDK 工具并执行。

### 风险与遗留

- “加入进行”目前只把规范化结果编码到 Phase 4 路由，正式草稿与步骤写入将在 Phase 4 接通。
- 识别指板的标记状态动画需在设备上复核，必要的动画和触觉调整留到 Phase 6 统一验收。

## Phase 4 交付：和弦进行、播放器与 MiniPlayer

### 已完成

- 新增进行列表与本地预设视图；列表展示名称、调性、BPM、拍号、步骤和和弦序列，并提供读取、复制、重命名、删除确认和删除后撤销。
- 新增进行编辑器：横向 Lazy 时间轴、长按拖动排序、左右移动按钮替代、批量添加和弦、步骤 BottomSheet、拍数、扫弦型、按法和删除步骤。
- 当前步骤显示大型 Compose 指板，下一步骤提供文字预览；播放步骤变化由 `ProgressionPlayer.Listener` 驱动并自动滚入可见范围。
- 编辑变化以 650ms 防抖写入独立 `progression-drafts-v1.bin`；该文件继续使用核心 `ProgressionStore` 的版本头、原子替换和备份，不覆盖正式进行，异常退出后可恢复。
- 保存、复制、重命名、删除继续复用 `ProgressionStore`；预设继续复用 `ProgressionPresetRepository` 与 `KeySignature`。
- 应用级 `ProgressionTransport` 统一包装 `ProgressionPlayer`、`MetronomeEngine` 和共享 `ChordAudioPlayer`，使用同一绝对时间锚点启动进行与节拍器；Compose 未使用 delay 驱动音频。
- 支持播放/暂停/继续/停止/前后切换、BPM、拍号、循环、整和弦/分解和弦，并接入独立节拍器页面。
- 接入自动、初学者、最小移动、开放和弦和高把位推荐模式，并执行“不允许横按”和最高品位硬限制；推荐理由在 UI 中可见。
- 一级页面显示应用级 MiniPlayer，可继续/暂停/停止进行或节拍器，也能返回真实编辑器；详情等二级页面不重复显示。
- 和弦试听与进行播放共用单一主播放会话：启动新会话会停止前一会话；进入后台统一暂停进行/节拍器并停止和弦试听。

### 主要文件

- `ui/model/ProgressionUiModels.kt`
- `ui/gateway/ProgressionGateway.kt`, `ProgressionPlaybackController.kt`
- `ui/progression/ProgressionListViewModel.kt`, `ProgressionListScreen.kt`
- `ui/progression/ProgressionEditorViewModel.kt`, `ProgressionEditorScreen.kt`
- `ui/progression/MetronomeScreen.kt`
- `ui/components/MiniPlayer.kt`, `MusicAppScaffold.kt`
- `ui/AppContainer.kt`, `ui/MusicApp.kt`, `ui/navigation/RouteCodec.kt`, `MusicNavHost.kt`
- `ui/progression/ProgressionViewModelTest.kt`, `ComposeFrontendTest.kt`

### 验证

- 命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug assembleDebugAndroidTest --stacktrace --console=plain`
- 结果：`BUILD SUCCESSFUL`，91 个任务，用时 55 秒。
- 既有离线套件（含进行 CRUD、播放器暂停/循环和 1,000 拍调度漂移）全部通过。
- Kotlin 单元测试：23/23 通过，0 failure、0 skipped；新增覆盖列表/预设加载、650ms 草稿防抖、可访问重排和共享 transport。
- Compose instrumentation APK 编译成功；新增“工具 → 进行 → 新建 → 批量加入 slash chord → 可访问重排按钮”场景。
- Lint：0 error，45 warning；`git diff --check` 无空白错误。
- Debug APK：20,156,079 字节，SHA-256 `C9787F7940CA008A4CE4D78BC78AFA7039009A715B0B64225CD81BD14CA3A544`。

### 视觉/性能证据

- 进行列表、时间轴和步骤按法均为 Lazy 容器并提供稳定 key/contentType；当前步骤高频状态与编辑器 UiState 分离。
- 音频/节拍来自现有绝对时间调度器，UI 只观察 listener 映射的 StateFlow；拖动排序同时提供逐步左右移动按钮。
- MiniPlayer、编辑器固定 transport 和独立节拍器均使用同一个播放状态源，避免重复控制条与状态漂移。
- 本阶段仍缺设备执行证据；实际拖动跟手、节拍视听同步、横竖屏布局和 MiniPlayer 遮挡将在 Phase 6 模拟器验收。

### 风险与遗留

- 草稿文件采用核心版本化二进制格式，版本为 v1；若未来正式进行业务格式升级，草稿迁移需同步评估。
- 设备级音频延迟和 ToneGenerator 音量无法由 JVM 验证，Phase 6 需在模拟器/设备实测并记录。

## Phase 5 交付：练习、AI、设置与导出

### 已完成

- 练习拆分为首页、轻量设置、沉浸会话和结果四层；支持双和弦、多和弦、随机挑战、30/60/自定义时长、BPM、2/4/3/4/4/4/6/8、每拍/每小节切换、首拍重音和偏好硬限制。
- 沉浸练习把临时进行交给应用级 `ProgressionTransport`，和弦切换、声音与节拍继续由 `ProgressionPlayer + MetronomeEngine` 的共同绝对时间锚点驱动；倒计时只按单调时钟计算剩余量，不驱动音频。
- 会话支持暂停、重置、完成次数、当前/最佳连续、后台自动暂停、退出确认和旋转/SavedState 恢复；结果保存继续复用 `PracticeRecordStore`，并显示今日/七日统计和最近一次对比。
- AI 助手覆盖推荐和弦、推荐进行、解释和弦、优化进行、练习计划、切换建议和情绪生成；详情、进行和练习入口自动带入本地上下文。
- AI 未配置时明确显示零网络状态；请求有加载、取消和结构化错误；结果分成“AI 建议”与“本地验证”，所有 parser 继续复用 `AiResultValidator`，未验证候选不能保存。
- AI 接受动作必须由用户明确点击：进行只进入草稿，练习计划只写入 `PracticePlanDraftStore`；AI 不生成正式 frets/fingers。
- AI 设置保留启用、服务名、HTTPS Base URL、API Key、模型、温度和超时，以及保存、可取消连接测试、清除配置和缓存。完整 API Key 只存在于屏幕局部瞬时状态与 Keystore 写入调用，不进入 ViewModel/UiState、错误或日志。
- “我的”实现练习概览、主题、完整/简化/关闭动画、新手/专业模式、默认关闭的动态颜色、AI 状态、数据与导出和关于软件。
- 详情当前按法/全部按法、库多选和“我的 > 收藏”三个导出入口全部接通；JPG、PNG、SVG、自定义前缀与 Android 文件夹选择器均继续复用现有导出器。
- 导出按单个指法在 IO dispatcher 逐项执行并报告总数、完成、成功、失败和首个文件名；支持显式取消与离开确认，正式文件不是屏幕截图。

### 主要文件

- `ui/gateway/PracticeGateway.kt`, `AiGateway.kt`, `ExportGateway.kt`
- `ui/practice/PracticeViewModels.kt`, `PracticeHomeScreen.kt`, `PracticeSetupScreen.kt`, `PracticeSessionScreen.kt`
- `ui/ai/AiViewModels.kt`, `AiAssistantScreen.kt`, `AiSettingsScreen.kt`
- `ui/export/ExportScreen.kt`
- `ui/profile/ProfileScreen.kt`
- `ui/navigation/RouteCodec.kt`, `MusicNavHost.kt`
- `ui/phase5/Phase5ViewModelTest.kt`, `ComposeFrontendTest.kt`

### 验证

- 主命令：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug assembleDebugAndroidTest --stacktrace --console=plain`
- 结果：`BUILD SUCCESSFUL`，91 个任务，用时 73 秒。
- 修复新代码 Lint 提示后复验：`.\gradlew.bat lintDebug testDebugUnitTest assembleDebugAndroidTest --stacktrace --console=plain`，成功，用时 54 秒。
- 既有离线练习、AI 安全边界、导出与核心套件全部通过。
- Kotlin 单元测试：28/28 通过，0 failure、0 skipped；新增覆盖练习绝对时间/暂停恢复、设置本地校验、API Key 不进入 UiState、AI 取消和导出成功/失败/首文件进度。
- Compose instrumentation APK 编译成功；新增练习完整冒烟、AI 未配置状态与收藏导出入口场景。
- Lint：0 error，45 warning；数量回到旧 View 基线，没有新增 warning。
- Debug APK：20,282,112 字节，SHA-256 `CA8305ED15D9C38620FAAF8CBE100E69C38DD05A74F9AD8D64B8A203FD4CCC7F`。

### 视觉/性能证据

- 沉浸练习在 700dp 起切换横向双栏，紧凑宽度保持单列；指板继续使用缓存 Canvas，节拍只重组小型状态区域。
- AI、导出、设置和练习长内容使用 LazyColumn；导出图片/文件写入、AI 网络与练习记录均不在主线程。
- 动画三级设置已作用于全局主题 token，练习节拍脉冲在关闭动画时退化为即时状态；实际脉冲同步与布局仍待 Phase 6 设备证据。

### 风险与遗留

- Android 文件夹选择器和实际导出文件内容需要在可用设备/模拟器上执行，JVM 只能验证任务状态与核心导出器。
- AI 在线成功路径需要用户提供自己的服务与密钥；本轮只验证默认关闭、错误分类、取消、本地校验和密钥安全边界，不会擅自发起第三方网络请求。

## Phase 6 交付：自适应、性能、无障碍与旧 UI 删除

### 已完成

- 完成 P1 自适应布局：紧凑宽度使用底部导航，中等宽度切换 Navigation Rail，和弦库在可用宽度达到 760dp 时使用 master/detail 双栏；筛选、按钮和 chip 组通过 FlowRow 自适应换行。
- 统一应用级动效令牌为完整、简化、关闭三档。导航、共享元素、列表重排、详情按法、展开区、练习节拍脉冲和进行自动滚动均遵循令牌；系统 Animator Duration Scale 为 0 时强制关闭应用动画。
- 补齐收藏和进行删除撤销、筛选复选语义、所有手势的按钮替代与指板完整 content description。真实启用 Google TalkBack 后完成“进入详情 → 切换第二按法 → 收藏 → 返回”核心流程。
- 在 API 35 AVD 上实际检查浅色、深色、360dp、600dp、展开双栏、横屏、字体 1.0/1.3/2.0、加载/空/错误、进程重建、预测返回、TalkBack 和两类动画关闭状态。
- 新增 `benchmark` 模块、三条 Release Macrobenchmark、两条 Baseline/Startup Profile 采集旅程和 11 份 Perfetto trace。最终 profile 已进入源码和压缩 Release APK。
- 将 Compose instrumentation 扩展到导航、slash chord、状态恢复、收藏撤销、筛选与按法、理论无按法、识别、移调错误、进行编辑/撤销、练习、AI 默认关闭、系统目录选择器和正式导出内容。
- 功能对齐、回归和设备验收完成后，才删除旧 Java View Activity/Page/View。清单只保留 `ComposeMainActivity`；`FretboardDiagramRenderer`、`VoicingImageExporter`、`SvgVoicingRenderer` 与 `VoicingSvgExporter` 因仍被正式导出依赖而保留。

### 主要文件

- `build.gradle`, `settings.gradle`, `app/build.gradle`, `benchmark/build.gradle`
- `benchmark/src/main/java/com/k2/music/benchmark/K2Journeys.kt`
- `benchmark/src/main/java/com/k2/music/benchmark/BaselineProfileGenerator.kt`
- `benchmark/src/main/java/com/k2/music/benchmark/MusicMacrobenchmark.kt`
- `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt`
- `app/src/main/java/com/k2/music/ui/theme/MusicTheme.kt`, `MotionTokens.kt`
- `app/src/main/java/com/k2/music/ui/components/AdaptiveControlGroup.kt`, `MusicAppScaffold.kt`
- `app/src/main/java/com/k2/music/ui/library/LibraryScreen.kt`, `LibraryViewModel.kt`
- `app/src/main/java/com/k2/music/ui/navigation/MusicNavHost.kt`
- `app/src/androidTest/java/com/k2/music/ComposeFrontendTest.kt`
- `app/src/androidTest/java/com/k2/music/ComposeVisualStateTest.kt`
- `app/src/androidTest/java/com/k2/music/CoreExportInstrumentationTest.kt`
- `docs/evidence/frontend-redesign/`

### 验证

- 最终工程门禁：`.\gradlew.bat offlineCoreJvmTest lintDebug assembleDebug assembleDebugAndroidTest --stacktrace --console=plain`；清理旧占位代码并修复源码 Lint 后重跑，`BUILD SUCCESSFUL`，90 个任务，用时 1 分 49 秒。
- Java 核心：和弦数据、名称/理论、SVG、进阶工具和 Phase 3 核心五组可执行 smoke tests 全部通过。
- Kotlin 单元测试：10 个 suite、31/31 通过，0 failure、0 skipped。
- 设备测试：`.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace --console=plain`；最终冷启动 AVD 全量回归 15/15 通过，0 failure、0 skipped，Gradle 用时 4 分 30 秒，XML 用时 216.394 秒。
- 一次紧接长时间 profile/R8 后的设备测试在规则清理阶段遇到 AVD `system_server` watchdog：应用 Activity 已进入 `PAUSED`，系统未及时派发 `STOPPED/DESTROYED`，练习流程断言本身已经完成且没有应用 ANR。冷重启并恢复设备设置后，完整 15 项全部通过；最终归档 XML 为通过结果。
- Lint：0 error、2 warning、0 hint；产品源码 warning 已清零。保留项为规格要求的 targetSdk 36（compileSdk 37）以及 Gradle Wrapper 9.6.1 可用提示；不在重构收尾时擅自改变 targetSdk 或构建工具小版本。
- Macrobenchmark：Release 目标包的 3/3 场景通过，0 failure、0 skipped，XML 总用时 415.652 秒；原始 JSON、摘要和 11 份 trace 均保留。
- Baseline Profile：`BaselineProfileGenerator` 的 startup 与 critical journeys 2/2 通过；生成任务按预期跳过三条 Macrobenchmark。最终 Baseline 27,239 行、Startup 18,141 行。
- Release：`.\gradlew.bat :app:assembleRelease "-Pandroid.baselineProfile.automaticGenerationDuringBuild=false" --stacktrace --console=plain`；最终源码重建 `BUILD SUCCESSFUL`，130 个任务，用时 11 分 36 秒。APK 内核验存在 `assets/dexopt/baseline.prof`（10,981 字节）与 `baseline.profm`（621 字节）。
- Release APK：2,092,008 字节，SHA-256 `EDA497DC3CCA788CD69FBDE7FBE469314FFE6771DADA940D0AF0256C83C6EC2E`；启动入口 `com.k2.music.ComposeMainActivity`，包名、versionCode 4、versionName 1.3、minSdk 23、targetSdk 36 未改变。
- Profile 哈希：Baseline `EDC69DD44767196ED96E701EFBD6B774CE29271DC467210FC2698BF6903A84CB`；Startup `418CED4011CE17C5631444192B2F99FA4335D857B783AAB97808E6EC54E07724`。
- 精确旧 UI/旧占位类名静态审计：`NO_OLD_UI_OR_PLACEHOLDER_REFERENCES`；生产源码无 TODO/FIXME/NotImplemented 占位；`git diff --check` 无空白错误。

### 视觉/性能证据

- 完整截图矩阵与自动化 XML 位于 `docs/evidence/frontend-redesign/README.md`。代表性证据包括紧凑浅/深色、200% 字体、多段长页面、600dp/130%、展开双栏、横屏、TalkBack 焦点、预测返回进行中、进程重建、动画关闭和加载/空/错误状态。
- TalkBack 服务实际启用并产生 TTS 日志；指板被读作完整六弦/品位/指法描述，而不是不可理解的逐像素节点。收藏、切换按法和返回均可完成。
- 预测返回在系统开关启用后捕获手势 progress 帧，并验证完成后回到和弦库。后台杀进程后，系统任务恢复到原详情目标；横竖屏和草稿状态由 SavedState 恢复。
- Macrobenchmark 运行于 API 35、4GB RAM、host GPU 模拟器。冷启动 `timeToInitialDisplayMs`：min 1,836.5、median 2,060.8、max 2,461.0（5 次）。
- 和弦库/详情/按法场景：`frameDurationCpuMs` P50 125.1、P90 143.9、P95 155.1、P99 186.7；`frameOverrunMs` P50 162.6、P90 191.8、P95 207.4、P99 271.3。
- 进行/练习场景：`frameDurationCpuMs` P50 127.3、P90 148.3、P95 161.8、P99 197.0；`frameOverrunMs` P50 164.5、P90 197.9、P95 217.0、P99 253.3。
- 上述模拟器帧数据明显没有达到规格中的约 16ms/1% 目标，且 AndroidX 明确警告模拟器性能不代表真实设备。因此本报告只认定“已有 Release/profileable 测量证据”，不认定物理设备性能门禁通过。11 份 trace 已保留用于后续定位。
- Baseline/Startup Profile 已按真实核心旅程生成并打入 R8 Release APK；性能工具链因 AGP 9.2 DSL 兼容性使用 `androidx.baselineprofile`/Macrobenchmark `1.5.0-alpha07`，产品 UI 依赖仍使用稳定版本。版本选择依据记录在 README。

### 风险与遗留

- P2：在 Android 10/11、约 4GB RAM 的中端物理设备和当前高刷新率物理设备重跑同一 Macrobenchmark；以 Perfetto 定位搜索、按法切换和进行/练习场景的长帧，在真实设备上达到阈值后才能关闭性能项。
- P2：把“连续快速搜索、完整库滚动、20 次按法切换、60 秒练习/节拍器和大收藏批量导出”拆为更细的长期性能场景，并加入基准回归阈值；当前三条旅程已覆盖核心链路，但没有把每个第 14.5 节场景独立量化。
- P2：在物理设备补做扬声器延迟、触觉感受、OLED 深色对比度与高刷新率动画复核；模拟器已完成布局和交互矩阵，但不能替代这些硬件结论。
- AI 在线成功路径仍需要用户自己的 HTTPS 服务与密钥；为保护安全边界，本次没有注入凭据或向第三方发送请求。默认关闭、零网络、错误、取消、本地校验和接受动作已通过测试。
- Release 构建会因 Baseline Profile 采集和 R8 首次运行耗时较长；后续 CI 可把 profile 采集设为独立定期任务并在普通 release 构建中消费已提交的 profile。
- 构建链仍提示 Gradle 9.6.1 可用以及 Gradle 10 将移除当前使用的部分兼容行为；首次 Release 还提示 AndroidX `libandroidx.graphics.path.so` 无需/无法再次 strip 并按原样打包。三者均不影响当前 APK，但应在后续构建工具升级任务中验证并清理。

Phase 0–6 的全部 P0 与 P1 已实现；上述事项均为设备覆盖、性能深化或硬件复核性质的 P2，不阻塞本次前端交付。
