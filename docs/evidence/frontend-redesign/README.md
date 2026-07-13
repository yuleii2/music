# 前端重构证据索引

日期：2026-07-12
设备：Android 15 / API 35 AVD，4GB RAM，1080×2400、480 dpi；同一 AVD 还以 600dp、960dp、横屏和不同字体比例运行。Macrobenchmark 使用 Release、不可调试目标包；AndroidX 会明确警告模拟器数据不能代表物理设备。

## 自动化结果

- [UI/instrumentation XML](ui-tests.xml)：15/15 通过，0 failure、0 skipped；包含 12 个 Compose 端到端场景、2 个正式导出回归和 1 个加载/空/错误视觉状态场景。
- [Macrobenchmark XML](macrobenchmark-tests.xml)：3/3 通过，0 failure、0 skipped。
- [Baseline Profile XML](baseline-profile-tests.xml)：2 个 profile 采集场景通过；3 个 Macrobenchmark 场景按生成任务过滤规则跳过。
- [Lint XML](lint-results-debug.xml)：0 error、2 个有意保留的工程 warning、0 hint；产品源码 warning 已清零。
- [Macrobenchmark 原始 JSON](macrobenchmark-data.json) 与三份摘要：[冷启动](macrobenchmark-cold-start.txt)、[和弦库/详情/按法](macrobenchmark-library-detail.txt)、[进行/练习](macrobenchmark-progression-practice.txt)。
- [Release 输出元数据](release-output-metadata.json)。Perfetto 的 11 个大文件保留在 `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/K2_Music_API35(AVD) - 15/`，未复制进文档目录。

## 视觉矩阵

| 场景 | 证据 |
|---|---|
| 360dp 浅色/深色 | [浅色首页](compact-light-home.png)、[深色首页](compact-dark-home.png)、[深色和弦库](compact-dark-library.png) |
| 200% 字体 | [首页](compact-font200-home.png)、[我的顶部](compact-font200-profile-top.png)、[我的中部](compact-font200-profile-mid.png)、[我的底部](compact-font200-profile-lower.png)、[练习设置顶部](compact-font200-practice-setup.png)、[练习设置底部](compact-font200-practice-setup-lower.png) |
| 600dp / 130% 字体 | [工具](medium-font130-workbench.png)、[进行编辑](medium-font130-progression-editor.png)、[沉浸练习](medium-font130-practice-session.png)、[AI 未配置](medium-font130-ai-unconfigured.png)、[AI 设置](medium-font130-ai-settings.png)、[AI 错误](medium-font130-ai-settings-error.png) |
| 展开双栏 | [和弦库双栏](expanded-light-library-dual-pane.png)、[选择后详情](expanded-light-library-selected.png) |
| 横屏/状态恢复 | [手机横屏](compact-landscape-library.png)、[进程重建详情](compact-process-recreated-detail.png) |
| TalkBack | [详情焦点](talkback-focus-full-detail.png)、[按法焦点](talkback-voicing-focus.png) |
| 动画退化 | [应用动画关闭](compact-motion-off-voicing.png)、[系统动画关闭](compact-system-animations-off-home.png) |
| 预测返回 | [返回手势进行中](compact-predictive-back-progress.png)、[返回后](compact-predictive-back-after.png) |
| 状态页 | [加载](visual-loading.png)、[空状态](visual-empty.png)、[错误](visual-error.png)、[和弦库空状态](medium-font130-library-empty.png) |

截图只作为布局、主题、字体和交互状态证据；帧耗时以 Macrobenchmark JSON/Perfetto 为准。物理设备性能复测及 Android 10/11、高刷新率设备矩阵列为 P2，详见实施报告。
