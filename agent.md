# agent.md

给 AI 编程助手的工作说明。人类贡献者请先读 [README.md](README.md)。

这个项目有一个反复出现的失败模式：**凭直觉猜第三方 API 的语义，然后靠用户上真机验证来纠错**。历史上因此浪费了大量时间。本文件的首要目的，是把已经付出代价才换来的结论固化下来，让它们不必再被发现一次。

---

## 0. 本项目最重要的一条规则

**不要猜 native / 第三方库的语义。** 遇到下面这些情况，先读源码或查官方文档，再写代码：

| 情况 | 必须做的 |
|---|---|
| 依赖第三方库（尤其本地 `.aar` / `.so`）的行为 | 反编译或读其源码确认 API 行为 |
| 用到不熟悉的 Android API 语义 | 查 `developer.android.com`，不要凭印象 |
| 涉及并发 + 共享 native 资源 | 查该库是否声明线程安全；不确定就当作**不安全**处理 |
| 写等待循环 / 轮询条件 | 确认那个标志位真的会在目标状态下变化 |

历史上踩过的坑全部属于「未经验证的 API 语义」，其中多数只能在真机上暴露。详见第 4 节。

---

## 1. 项目速览

| | |
|---|---|
| 包名 | `com.pupil.hanzi` |
| 语言 / UI | Kotlin 2.4.21 + Jetpack Compose（Material 3） |
| 构建 | AGP 9.4.1 / Gradle 9.8 / JDK 17 / compileSdk 37 / minSdk 31 |
| 模块 | 单模块 `:app` |
| 测试 | 54 个单元测试，`:app:testDebugUnitTest` |
| 网络权限 | **无。** 所有数据随包分发 |

### 关键依赖

```
com.hankcs:hanlp:portable-1.8.6        上下文分词 → 多音字判定
app/src/main/assets/hanlp_cws_crf.bin  CRF 分词模型（12MB），首次启动拷到 filesDir
```

除 HanLP 外没有第三方运行时依赖，也**没有 native 库**：语音是随包分发的 mp3 音节录音。

---

## 2. 目录结构

```
app/src/main/
├── java/com/pupil/hanzi/
│   ├── MainActivity.kt          入口，edge-to-edge
│   ├── data/
│   │   ├── AssetRepository.kt   分片资源的懒加载与预热
│   │   ├── PinyinService.kt     汉字判定、带调拼音拼装
│   │   ├── StrokeData.kt        笔顺数据结构
│   │   ├── SettingsStore.kt     DataStore 持久化四个开关（默认全开）
│   │   ├── HistoryStore.kt      DataStore 持久化确认过的文字（JSON，含时间，最多 30 条）
│   │   └── VoiceAssets.kt       音节 mp3 的存在性探测
│   ├── nlp/Segmenters.kt        CRF 分词模型的加载与回退
│   ├── audio/AudioPlayer.kt     音节 mp3 解码播放
│   ├── stroke/
│   │   ├── StrokeGeometry.kt    medians/outlines → Compose Path
│   │   ├── StrokeTimeline.kt    笔顺时序
│   │   └── StrokeTiming.kt      时长分配
│   └── ui/                      MainScreen / CharGrid / HistoryList / DetailSheet /
│                                HanziViewModel / PinyinText / ReadingPicker
├── assets/
│   ├── draw_all/         53 个分片，30MB  笔顺 medians + outlines
│   ├── explanation_all/  41 个分片，20MB  释义
│   ├── voice/          2055 个 mp3，14MB  音节库
│   └── hanlp_cws_crf.bin           12MB  CRF 分词模型
└── res/                             图标、主题、拼音字体
```

### 数据分片机制

`cnchar-data` 里每字一个 JSON 文件（约 1.8 万个），`tools/merge_assets.py` 合并成 `assets/draw_all/` 与 `assets/explanation_all/`。

**分片键 = `ord(char) >> 9`**，文件名 `s{shard:x}.json`（实际见 s17–s4f）。查一个字只需加载它所在的分片，不必全量解析。

分片是**提交进仓库**的，所以干净克隆可以直接构建，不必跑合并脚本。

---

## 3. 修改代码时必须遵守的约束

### 3.1 `MODE_STATIC` 播完不会退出 `PLAYING`

等待播放结束必须用 `playbackHeadPosition`，不能用 `playState`：

```kotlin
// 错：MODE_STATIC 缓冲区播完后不会自动转 STOPPED，这个循环永不退出
while (track.playState == AudioTrack.PLAYSTATE_PLAYING) Thread.sleep(5)

// 对
while (track.playbackHeadPosition < pcm.size) { /* ... */ }
```

用错会导致 `play()` 永不返回，上层的 `finally` 也执行不到，UI 状态永久卡住。

### 3.2 设置栏在窄窗口折叠

`SettingsBar` 在 `screenWidthDp < 600`（Material 的 compact 断点，常量 `WIDE_WIDTH_DP`）时把四个开关收进一个芯片，点开才铺到第二行；宽窗口保持单行平铺。用断点而不是测量芯片宽度，是因为断点不需要第二次布局就能定下来。

折叠状态用 `rememberSaveable(mode)` 记忆：**输入页默认展开、字卡页默认收起**，切页时回到该页的默认值，同一页内旋转保持用户的选择。芯片文案是「显示设置」/「收起设置」——「更多」看不出这里是什么。

**不要改回横向滚动。** 滚动把开关藏在一个没有任何提示的位置，之前手机上「屏幕常亮」就是被「编辑」按钮盖住、又被滚走的那一个。

### 3.3 网格按输入的行断开

`cells` 和输入文本**一一对应**（换行符也是一个 cell，`isPunctuation = true`），行结构只由 `CharGrid.kt` 的 `gridRows(cells, columns)` 决定：换行结束当前行，连续换行算一次换行，首尾换行不产生空行。渲染用 `LazyColumn` + `Row`，不是 `LazyVerticalGrid`——固定列数的网格只会把 cell 塞进下一个空位，没法提前结束一行。

### 3.5 输入页只有历史记录滚动

手机竖屏：输入框和「清空/粘贴/确认」固定在上方，只有历史记录 `LazyColumn` 吃 `weight(1f)`。平板或手机横屏（同一个 `WIDE_WIDTH_DP` 断点）：输入框在左、历史记录在右，左栏 `fillMaxHeight`，输入框用 `weight(1f, fill = false)` + `heightIn(min = 140.dp)`——文本多时输入框自己长高，最多长到按钮留下的空间，长文在框内滚动，**按钮永远不会被顶出屏幕**。改这里时别把输入框换成固定高度或让整个页面滚动。

### 3.6 喇叭那一格的高度只在语音开着时保留

`CharCard` 里喇叭外面那个 36dp 的 `Box` 是**行内对齐**用的：同一行里可能有标点（`isPunctuation`）或还没查到读音的字（`reading == null`），它们没有喇叭；如果空格塌掉，一行里几张卡就不一样高，行高会参差不齐。

但它**只在 `speakEnabled` 为真时才存在**——语音整个关掉时所有卡都没有喇叭，再留 36dp 就是白占地方（2026-10-16 之前就是这样，下一行不会上移）。拼音格反过来：`showPinyin` 是全局设置，关掉时所有卡一起少一格，所以直接塌掉。

### 3.7 CRF 分词模型是 HanLP 的私有 `.bin` 格式

`assets/hanlp_cws_crf.bin` 不是 CRF++ 的文本模型，是 HanLP 自己写的二进制格式（`LogLinearModel` 的 `load`）。加载它只能用 `com.hankcs.hanlp.model.crf.CRFLexicalAnalyzer(path)`：路径以 `.bin` 结尾时它直接按二进制读。

**不要用 `com.hankcs.hanlp.seg.CRF.CRFSegment`。** 这个类在 1.8.6 已整体废弃，它的 `String` 构造走 `CRFModel.loadTxt`（CRF++ 文本格式），读到二进制文件会抛 `NumberFormatException`。

危险的是失败方式：`Segmenters.ensure` 用 `catch (Throwable)` 兜底回退到 Viterbi，异常只进 logcat。**改分词相关代码后要确认 `Segmenters.isCrf == true`**（`HanziViewModel` 的 warmup 日志里带这个字段），否则「CRF 分词」可能根本没生效，而界面一切正常。

---

## 4. 历史 bug 与根因

以下都已修复，记录在此以免重犯。

| 现象 | 根因 | 教训 |
|---|---|---|
| 播完卡在「停止播放」 | `MODE_STATIC` 状态语义猜错 | 见 3.1 |
| CRF 分词静默没生效，一直用 Viterbi | 用了已废弃的 `CRFSegment(String)`，它按 CRF++ 文本格式读 `.bin` 文件，异常被 `catch (Throwable)` 吞掉 | 见 3.7 |

**整句语音合成（sherpa-onnx）已于 2026-10 移除**，它带走的四个 bug——词典是构造期快照、词典 key 必须是单个词、onnxruntime 不可重入、模型输出浮点 PCM 需转 int16——也随之消失。需要这些结论时看 `backup/onnx-tts` 分支上的本文件。

---

## 5. 调试：MIUI 会过滤应用日志

测试设备（Redmi / HyperOS）过滤掉了应用的 logcat 输出，`logcat` 里经常什么都看不到。

**有效手段，按优先级：**

1. **把状态显示在界面上。** 这是最有效的办法——界面上的字能穿透日志过滤。临时加一个状态 Text，测完删掉。
2. **用时间戳对齐。** 在操作前记一个时间点，再按时间窗 grep。

---

## 6. 构建与校验

```bash
./gradlew :app:assembleDebug          # 构建 APK
./gradlew :app:testDebugUnitTest      # 54 个单元测试
./gradlew :app:assembleRelease        # 发布包，输出到 app/build/outputs/apk/release/
```

改动 `stroke/` 或 `audio/` 后，**单元测试覆盖不到，必须真机验证**——历史上测试通过但真机出问题的都是这两处。

**签名**：`jks/` 目录，含 keystore 和明文口令（见 `jks/readme.txt`）。debug / release / androidTest 三个变体共用同一把 key（`app/build.gradle.kts` 里名为 `shared` 的签名配置），所以 debug 包可以直接覆盖 release 包。个人自用工具，不上架应用市场，故签名文件入库。

---

## 7. 已知问题

- `AudioPlayer.playWithSystemTts` / `ensureTts` 是平台 TTS 兜底，**当前没有任何调用点**，只保留了实现。
- `app/src/main/ic_launcher-playstore.png` 放在 `res/` 目录下，不参与编译，是 Android Studio 导出图标时的落点。保留即可。
- Gradle 有一条 `Configuration.setVisible()` 弃用警告，来自 **AGP 内部**，不是本项目代码。Gradle 10 才移除，届时升级 AGP 即可。
