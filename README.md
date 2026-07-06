# 吉他和弦字典 Android 版

这是一个面向吉他学习者的 Android 和弦字典应用。当前实现版本为 V1.3，仅面向 Android；HarmonyOS、iOS、网页端和桌面端暂不实现。应用支持输入和弦名称，查看组成音、音程结构、和弦类型说明、常见吉他按法、指板示意图，并可点击按钮试听当前按法或基础组成音。

## 当前功能

- Android 移动端应用。
- 支持常见和弦查询：C、D、E、F、G、A、B、Am、Dm、Em、G7、A7、E7、Cmaj7、Fmaj7、Am7、Dm7、Bm7、Csus2、Dsus4、Bdim、Caug、Cadd9、C7、D7、Bm、F#m、C9、G9、D9、A9、C/E、G/B、D/F#。
- 展示和弦根音、类型、组成音、音程结构和学习说明。
- 展示每个已支持和弦的推荐吉他按法。
- 支持 C、G、Am、F 等和弦的多个按法切换。
- 支持收藏当前和弦，并在本机保存。
- 支持最近查询记录，并可点击历史记录快速回到和弦结果。
- 支持常见别名输入，例如 `CM7`、`cmaj7`、`Amin`、`B°`、`C+`。
- 支持常见分数和弦输入，例如 `G/B`、`D/F#`。
- 对 `H`、`Cb`、`E#` 等容易混淆的输入给出提示。
- 使用自定义指板图展示按弦、空弦和闷弦。
- 支持选择导出路径、设置文件名前缀，并将当前和弦或收藏和弦的全部指法批量导出为 JPG/PNG 图片。
- 使用白底、黑色粗体、浅灰功能块和轻量动画的新版移动端界面。
- 使用 MIDI 音高数据驱动内置合成音色进行当前按法试听和组成音试听。
- 显示试听状态，包括播放中、结束和不可用提示。
- 对空输入、非法输入和暂不支持和弦给出提示。

## 构建

本机需要 Android SDK。当前项目已包含 Gradle Wrapper。

```powershell
$env:ANDROID_HOME='C:\Users\summer\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\summer\AppData\Local\Android\Sdk'
.\gradlew.bat assembleDebug :app:chordDataSmokeTest
```

构建成功后，会在 `dist` 目录生成带版本号的安装包，当前版本为：

```text
D:\K2\music\dist\guitar-chord-dictionary-v1.3-debug.apk
```

Gradle 默认输出目录中仍会保留 Android 原始文件名 `app-debug.apk`，但请优先使用 `dist` 目录下带版本号的 APK，避免不同版本混淆。

## 需求文档

项目需求规格说明书位于 [docs/requirements.md](docs/requirements.md)。
