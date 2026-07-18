# 吉他和弦工作室 Android 版

原生 Android 吉他和弦、编排与练习工具，当前产品版本为 V1.6（`versionCode 7`）。前端使用 Kotlin + Jetpack Compose，乐理、音频、存储、导出与 AI 校验继续复用现有 Java 核心。查询、识别、移调、播放、练习、曲谱、统计、备份和导出均可离线使用，不依赖账号或云同步；AI 是默认关闭的可选增强层。

V1.4 建立了“发现薄弱点 → 安排今日练习 → 进行练习 → 记录跟上/没跟上 → 统计方向性和弦切换 → 调整练习难度 → 展示长期进步”的本地学习闭环。推荐和统计只使用真实资料与可信练习数据；样本不足时明确显示数据不足，不生成虚假的薄弱切换。

V1.5 在该闭环上增加“本地曲谱练习”：用户可粘贴自己已有的和弦谱，预览并逐行修正解析结果，保存为可移调、可分段、可循环、可固定指法和可统计的本地项目；专项切换继续写入可信 `TransitionAttempt`，连续演奏只记录真实时长、配置、完成状态和用户主动勾选的困难点，不伪造成功率。

V1.6 完成十二音复杂和弦库升级：48 种和弦性质按三和弦、六和弦、七和弦、九和弦、挂留、延伸、变化及斜杠和弦分层浏览。576 个“根音 × 性质”和 6 个录制斜杠和弦均有合法指法，其他“和弦主体 + 指定低音”组合按需离线生成并校验真实最低音；高级和弦的关键音与可省略音会显式展示。

## 当前架构

- `ComposeMainActivity` 是唯一启动入口，五项一级导航为首页、和弦库、工具、练习和我的。
- `app/src/main/java/com/k2/music/ui/` 包含 Compose 页面、状态模型、ViewModel、网关和设计 token。
- `app/src/main/java/com/k2/music/` 保留 Java 乐理、识别、绝对时间音频调度、本地存储、JPG/PNG/SVG 导出与 AI 本地校验核心。
- `benchmark/` 包含 Baseline Profile 采集和 Macrobenchmark 场景；生成结果位于 `app/src/main/generated/baselineProfiles/`。
- 旧 Java View 页面已在功能对齐、设备测试和导出回归之后删除；保留仍被正式导出依赖的 renderer。

界面支持浅色/深色、完整/简化/关闭动画、200% 字体、TalkBack、预测返回和自适应布局。紧凑宽度使用底部导航，中等宽度使用 Navigation Rail，展开宽度的和弦库使用列表—详情双栏。

## 主要功能

- JSON 驱动的离线和弦系统：48 种公式、十二个根音的理论生成、405 条审校 JSON 指法，并由规则生成器补齐新增类型和任意合法斜杠低音。
- 统一 `ChordSymbolParser`：处理大小写、空格、中英文别名、中文升降音、Unicode 升降号、`△`、`°`、`ø`、复杂后缀与 slash chord，并返回可定位的失败 token。
- 多指法详情、收藏、历史、自定义指法、合成试听，以及 JPG、PNG、矢量 SVG 单个或批量导出。
- 反向识别：可编辑六弦指板或音符列表输入，返回最多五个带评分和文字证据的候选。
- 移调与变调夹助手：`-11…+11` 半音、升降号偏好、slash bass 和 `0…12` 品变调夹计算。
- 和弦进行：本地 CRUD、调性预设、卡片编辑、可访问重排、草稿恢复、循环和整和弦/分解和弦播放。
- 使用绝对单调时钟的播放器与节拍器，支持 `2/4`、`3/4`、`4/4`、`6/8`、首拍重音和生命周期暂停。
- 首次启动四步引导：学习水平、最多两个目标、每日时长与新手/专业体验模式，可跳过并在“我的”中重新设置。
- 动态今日练习：首页按学习资料、收藏/熟悉和弦及可信历史生成确定性任务；支持直接开始、调整参数和恢复上次配置。
- 可信练习：双和弦、多和弦循环、随机挑战、已保存进行节奏、绝对时间播放，以及逐次持久化的“跟上了/没跟上”。失败会清零当前连续次数，但不会抹去最佳连续次数。
- 方向性切换统计：`C → G` 与 `G → C` 分开；最近最多 20 次样本计算成功率、稳定 BPM、连续次数、时效与 0–100 熟练度，少于 5 次不判定强弱。
- 练习设置和难度建议：最近/收藏/熟悉/推荐/保存进行选材，±1/±5 BPM、常用速度、预设时长及真实保存进行节奏；至少 10 次结果后按成功率给出保持、升 5 或降 5/10 BPM 建议。
- 本地曲谱库：曲名/作者搜索、最近练习、未完成设置、粘贴导入、解析预览、逐行角色修正、详情和编辑；原始文本始终独立保留。
- 确定性曲谱解析：支持行内 `[C]歌词`、和弦行+歌词行、`| C | G |` 小节、中文/英文/自定义段落名，并通过本地和弦核心验证 slash chord、别名及 Unicode 升降号。英文自然语言行使用有效和弦比例、词数、空格和小节结构联合判定，低置信内容进入警告而不静默删除。
- 三态曲谱节奏：`UNTYPED` 只提供手动滚动/切换，`SIMPLE_MEASURES` 显示确定性小节推断，`EXPLICIT_BEATS` 要求每个事件都有明确拍数；只有可靠拍数才启用精确高亮、下一和弦预告和自动播放。
- 曲谱编配：复用本地移调、变调夹和指法核心，区分实际音高、手型调与 capo；支持 `-11…+11` 半音、`0…12` 品、slash bass、升降号偏好、最多三个可解释方案及每事件固定指法回退。
- 两种曲谱练习：`GUIDED_TRANSITION` 只记录真实方向与真实播放步骤；`PERFORMANCE` 记录真实时长、完成状态和配置，困难切换由用户主动勾选并独立保存，不写成失败尝试。
- 曲谱成长与首页续练：歌曲伴奏目标按最近曲谱、主动困难、全局弱项、未练段落、陌生和弦和稳定段落排序；续练恢复曲谱、段落、模式、BPM、移调、capo、固定指法快照、循环和显示设置。
- 成长看板：七日练习时长、尝试/成功/失败/成功率、连续练习天数、最强/最弱方向切换和稳定速度。
- 新手/专业模式会即时改变详情信息密度、默认指法范围、横按/品位约束、练习速度/时长和高级设置展开状态。
- 完整 ZIP 备份与恢复：预览、校验、合并/覆盖、是否恢复设置、取消与失败回滚；稳定 ID 去重保证重复恢复不使练习统计翻倍，且 API Key 从不进入备份。
- 确定性指法推荐：初学者、最小移动、开放和弦、高把位与自动模式，并执行横按和最高品位约束。
- 可选 AI 和弦助手：OpenAI-compatible 接口、结构化 JSON、一次修复重试、取消/超时/HTTP 错误分类和严格本地验证。

## AI 安全边界

- AI 默认关闭；未配置或禁用时不会发起网络请求，全部离线功能保持可用。
- API Key 使用 Android Keystore + AES-GCM 加密；界面状态、日志、错误、导出和缓存均不保存或回显完整密钥。
- AI 不能生成正式 `frets`/`fingers` 数据。和弦、调性关系、指法和切换评分由本地核心提供或验证。
- AI 建议只有在用户明确确认后，才会转换为本地进行草稿或练习草稿。

## 数据文件

```text
app/src/main/assets/chords/
├── chord_formulas.json
└── guitar_voicings.json
```

`ChordRepository` 是兼容入口，内部组合数据加载、统一符号解析、理论与静态/规则指法仓库。应用在后台预加载并校验 JSON，再按公式的 required/optional/omittable 规则补齐指法；失败时启用安全回退数据并向界面报告。

V1.4 新增或升级的本地数据包括 `practice-records-v1.bin`（文件名保持兼容、内部 schema 升至 3）、`transition-attempts-v1.bin` 和 `learning_profile_v1`。V1.5 将 `transition-attempts-v1.bin` 内部 schema 升至 2，并新增 `song-projects-v1.bin`（Store schema 2）、`song-practice-runs-v1.bin`（Store schema 3）和 `song-difficulties-v1.bin`（Store schema 1）。所有曲谱 Store 使用稳定 ID、数量/文本上限、原子替换和 `.bak` 回退；项目 v1、练习记录 v1/v2 可读并迁移到当前内存模型。完整备份 schema 升至 2，增加三个曲谱 JSON 分区及 SHA-256 校验，重复恢复幂等且仍不包含 API Key。

## 构建与验证

本机需要 Android SDK，项目已包含 Gradle Wrapper。

```powershell
# 离线 Java 核心、Kotlin 单元测试、Lint、Debug 与 instrumentation APK
.\gradlew.bat offlineCoreJvmTest lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest --stacktrace --console=plain

# 编译 instrumentation APK
.\gradlew.bat assembleDebugAndroidTest

# 在已启动设备/模拟器上运行 Compose、导出和视觉状态测试
.\gradlew.bat :app:connectedDebugAndroidTest

# Release Macrobenchmark
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.k2.music.benchmark.MusicMacrobenchmark"

# 重新采集 Baseline/Startup Profile
.\gradlew.bat :app:generateBaselineProfile

# 使用已提交 profile 进行 R8、资源压缩和 Release APK 构建；无需连接采集设备
.\gradlew.bat :app:assembleRelease "-Pandroid.baselineProfile.automaticGenerationDuringBuild=false" --stacktrace --console=plain
```

`androidx.baselineprofile` 与 Macrobenchmark 使用 `1.5.0-alpha07`，原因是项目采用 AGP 9.2，而 AndroidX Benchmark 1.5 的 AGP 9 DSL 支持自 alpha01 起提供。该例外仅用于性能测试/构建链，产品运行时代码没有引入预览版 UI 组件。版本依据见 [AndroidX Benchmark release notes](https://developer.android.com/jetpack/androidx/releases/benchmark)。

主要构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app/build/outputs/apk/release/app-release.apk
dist/guitar-chord-dictionary-v1.6-debug.apk
dist/guitar-chord-dictionary-v1.6-release.apk
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

项目历史需求与执行材料见 [docs/requirements.md](docs/requirements.md)、[docs/frontend-redesign-execution-spec.md](docs/frontend-redesign-execution-spec.md)、[docs/frontend-redesign-implementation-report.md](docs/frontend-redesign-implementation-report.md) 和 [docs/v1.5-song-practice-implementation-report.md](docs/v1.5-song-practice-implementation-report.md)；原有 Java 核心的历史实现记录保留在 [docs/implementation-report.md](docs/implementation-report.md)。
