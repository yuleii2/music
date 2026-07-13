# 吉他和弦工作室 Android 版

原生 Android 吉他和弦、编排与练习工具，当前产品版本为 V1.4（`versionCode 5`）。前端使用 Kotlin + Jetpack Compose，乐理、音频、存储、导出与 AI 校验继续复用现有 Java 核心。查询、识别、移调、播放、练习、统计、备份和导出均可离线使用，不依赖账号或云同步；AI 是默认关闭的可选增强层。

V1.4 建立了“发现薄弱点 → 安排今日练习 → 进行练习 → 记录跟上/没跟上 → 统计方向性和弦切换 → 调整练习难度 → 展示长期进步”的本地学习闭环。推荐和统计只使用真实资料与可信练习数据；样本不足时明确显示数据不足，不生成虚假的薄弱切换。

## 当前架构

- `ComposeMainActivity` 是唯一启动入口，五项一级导航为首页、和弦库、工具、练习和我的。
- `app/src/main/java/com/k2/music/ui/` 包含 Compose 页面、状态模型、ViewModel、网关和设计 token。
- `app/src/main/java/com/k2/music/` 保留 Java 乐理、识别、绝对时间音频调度、本地存储、JPG/PNG/SVG 导出与 AI 本地校验核心。
- `benchmark/` 包含 Baseline Profile 采集和 Macrobenchmark 场景；生成结果位于 `app/src/main/generated/baselineProfiles/`。
- 旧 Java View 页面已在功能对齐、设备测试和导出回归之后删除；保留仍被正式导出依赖的 renderer。

界面支持浅色/深色、完整/简化/关闭动画、200% 字体、TalkBack、预测返回和自适应布局。紧凑宽度使用底部导航，中等宽度使用 Navigation Rail，展开宽度的和弦库使用列表—详情双栏。

## 主要功能

- JSON 驱动的离线和弦系统：26 种公式、十二个根音的理论生成，以及 202 条内置吉他指法。
- 和弦名称解析：大小写、空格、英文别名、Unicode 升降号、`△`、`°`、`ø` 与 slash chord。
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

`ChordRepository` 是兼容入口，内部组合数据加载、名称解析、理论和指法仓库。应用在后台预加载并校验 JSON；失败时启用安全回退数据并向界面报告。

V1.4 新增或升级的本地数据包括 `practice-records-v1.bin`（文件名保持兼容、内部 schema 升至 3）、`transition-attempts-v1.bin` 和 `learning_profile_v1`。旧 V1/V2 练习会话读取后原子迁移到 schema 3，并由通用二进制存储层维护 `.bak` 回退；旧记录保留时长等历史信息，但没有逐次结果，因此不会按 100% 成功计入成功率。完整备份自身使用 schema 1，并通过清单 SHA-256 校验每个 JSON 分区。

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

# R8、资源压缩和 profile 打包后的 Release APK
.\gradlew.bat :app:assembleRelease
```

`androidx.baselineprofile` 与 Macrobenchmark 使用 `1.5.0-alpha07`，原因是项目采用 AGP 9.2，而 AndroidX Benchmark 1.5 的 AGP 9 DSL 支持自 alpha01 起提供。该例外仅用于性能测试/构建链，产品运行时代码没有引入预览版 UI 组件。版本依据见 [AndroidX Benchmark release notes](https://developer.android.com/jetpack/androidx/releases/benchmark)。

主要构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

项目历史需求与前端执行材料见 [docs/requirements.md](docs/requirements.md)、[docs/frontend-redesign-execution-spec.md](docs/frontend-redesign-execution-spec.md) 和 [docs/frontend-redesign-implementation-report.md](docs/frontend-redesign-implementation-report.md)；原有 Java 核心的历史实现记录保留在 [docs/implementation-report.md](docs/implementation-report.md)。
