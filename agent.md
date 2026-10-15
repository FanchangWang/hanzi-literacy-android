# agent.md

给 AI 编程助手的工作说明。人类贡献者请先读 [README.md](README.md)。

这个项目有一个反复出现的失败模式：**凭直觉猜第三方 API 的语义，然后靠用户上真机验证来纠错**。历史上因此浪费了大量时间。本文件的首要目的，是把已经付出代价才换来的结论固化下来，让它们不必再被发现一次。

---

## 0. 本项目最重要的一条规则

**不要猜 native / 第三方库的语义。** 遇到下面这些情况，先读源码或查官方文档，再写代码：

| 情况 | 必须做的 |
|---|---|
| 依赖的是本地 `.aar` / `.so` | 反编译或读其源码确认 API 行为 |
| 用到不熟悉的 Android API 语义 | 查 `developer.android.com`，不要凭印象 |
| 涉及并发 + 共享 native 资源 | 查该库是否声明线程安全；不确定就当作**不安全**处理 |
| 写等待循环 / 轮询条件 | 确认那个标志位真的会在目标状态下变化 |

历史上五个 bug 全部属于「未经验证的 API 语义」，每个都只能在真机上暴露。详见第 4 节。

---

## 1. 项目速览

| | |
|---|---|
| 包名 | `com.pupil.hanzi` |
| 语言 / UI | Kotlin 2.4.21 + Jetpack Compose（Material 3） |
| 构建 | AGP 9.4.1 / Gradle 9.8 / JDK 17 / compileSdk 37 / minSdk 31 |
| 模块 | 单模块 `:app` |
| 测试 | 47 个单元测试，`:app:testDebugUnitTest` |
| 网络权限 | **无。** 所有数据随包分发 |

### 关键依赖

```
com.hankcs:hanlp:portable-1.8.6     上下文分词 → 多音字判定
com.k2-fsa/sherpa-onnx (本地 .aar)  整句语音合成，CPU 推理
app/src/main/assets/tts/*.onnx     zh_CN-xiao_ya-medium 模型（63MB）
```

`sherpa-onnx-1.13.8.aar` 是**文件依赖**，不在 Maven 上，它自带四种 ABI 的原生库。

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
│   │   ├── SettingsStore.kt     DataStore 持久化四个开关
│   │   └── VoiceAssets.kt       音节 mp3 的存在性探测
│   ├── tts/
│   │   ├── NativeTts.kt         sherpa 引擎封装、词典生成
│   │   ├── TtsRepository.kt     整句合成、缓存、预构建
│   │   ├── Phonemes.kt          拼音 → 音素 token
│   │   └── PinyinRange.kt       声调区间
│   ├── audio/AudioPlayer.kt     音节 mp3 解码播放（TTS 回退路径）
│   ├── stroke/
│   │   ├── StrokeGeometry.kt    medians/outlines → Compose Path
│   │   ├── StrokeTimeline.kt    笔顺时序
│   │   └── StrokeTiming.kt      时长分配
│   └── ui/                      MainScreen / CharGrid / DetailSheet /
│                                HanziViewModel / PinyinText / ReadingPicker
├── assets/
│   ├── draw_all/         53 个分片，30MB  笔顺 medians + outlines
│   ├── explanation_all/  41 个分片，20MB  释义
│   ├── voice/          2055 个 mp3，14MB  音节库
│   └── tts/                         63MB  模型 + tokens
└── res/                             图标、主题、拼音字体
```

### 数据分片机制

`cnchar-data` 里每字一个 JSON 文件（约 1.8 万个），`tools/merge_assets.py` 合并成 `assets/draw_all/` 与 `assets/explanation_all/`。

**分片键 = `ord(char) >> 9`**，文件名 `s{shard:x}.json`（实际见 s17–s4f）。查一个字只需加载它所在的分片，不必全量解析。

分片是**提交进仓库**的，所以干净克隆可以直接构建，不必跑合并脚本。

---

## 3. 修改代码时必须遵守的约束

### 3.1 词典顺序不可改（`NativeTts.kt`）

sherpa 的 `character-lexicon.cc` 在 `Read()` 里构建 `word2ids_` 和 `all_words_`，**之后永不重读**。所以顺序固定为：

```
写词典文件 → 释放旧引擎 → 创建新引擎 → 合成
```

之前写成「先建引擎，播放时再写文件」，结果每次都是 OOV，模型退化成静默输出。

### 3.2 词典 key 必须是单个词

sherpa 用**自己的最长匹配分词**，和 HanLP 的切法不一致。词典里写 30 字的长句，sherpa 永远匹配不上。

正确做法：`NativeTts.buildLexicon` 按 HanLP 的分词结果逐词写一行。

### 3.3 引擎不可并发

**onnxruntime 不是可重入的。** 两个协程同时进入 `generate()`，会在 `libonnxruntime.so` 内空指针崩溃。

所有触碰引擎的路径必须经 `TtsRepository.engineLock` 串行化。**新增任何异步路径时，先确认它不会绕过这把锁。**

### 3.4 `MODE_STATIC` 播完不会退出 `PLAYING`

等待播放结束必须用 `playbackHeadPosition`，不能用 `playState`：

```kotlin
// 错：MODE_STATIC 缓冲区播完后不会自动转 STOPPED，这个循环永不退出
while (track.playState == AudioTrack.PLAYSTATE_PLAYING) Thread.sleep(5)

// 对
while (track.playbackHeadPosition < pcm.size) { /* ... */ }
```

用错会导致 `play()` 永不返回，上层的 `finally` 也执行不到，UI 状态永久卡住。

### 3.5 浮点 PCM 不可靠

模型输出归一化浮点（[-1, 1]）。`AudioTrack.write(float[])` 到 `MODE_STATIC` 缓冲在不同设备上行为不一致，**统一转 int16**：

```kotlin
(samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
```

### 3.6 缓存 key 包含全部读音

`TtsRepository.cacheKey` = 文本 + 每个字的读音。这是「切多音字要重新构建、原文重播不重新构建」的实现基础。改动这个 key 会同时破坏这两个行为。

---

## 4. 历史 bug 与根因

这五个都已在真机验证修复，记录在此以免重犯。

| 现象 | 根因 | 教训 |
|---|---|---|
| 整句被切成音节朗读 | 词典是构造期快照，写入顺序错了 | 读依赖源码确认生命周期 |
| 词典全部 OOV | key 写了整句，sherpa 分词规则不同 | 跨库集成必须验证分词一致性 |
| 播完卡在「停止播放」 | `MODE_STATIC` 状态语义猜错 | 见 3.4 |
| 切拼音后闪退 | onnxruntime 并发崩溃 | 见 3.3 |
| 合成无声音 | 浮点 PCM 未被设备接受 | 见 3.5 |

---

## 5. 调试：MIUI 会过滤应用日志

测试设备（Redmi / HyperOS）过滤掉了应用的 logcat 输出，`logcat` 里经常什么都看不到。

**有效手段，按优先级：**

1. **把状态显示在界面上。** 这是最有效的办法——界面上的字能穿透日志过滤。临时加一个状态 Text，测完删掉。
2. **native 崩溃用 `dumpsys dropbox`：**

   ```bash
   adb shell dumpsys dropbox --print | grep -A 40 "data_app_crash"
   ```

   native 崩溃没有 Java 异常，logcat 的 crash buffer 里也常常没有，但 dropbox 里有完整 tombstone 调用栈。这是定位 onnxruntime 崩溃的唯一途径。

3. **用时间戳对齐。** 在操作前记一个时间点，再按时间窗 grep。

---

## 6. 构建与校验

```bash
./gradlew :app:assembleDebug          # 构建 APK
./gradlew :app:testDebugUnitTest      # 47 个单元测试
./gradlew :app:assembleRelease        # 发布包，输出到 app/release/
```

改动 `tts/` 或 `stroke/` 后，**单元测试覆盖不到，必须真机验证**——这几个 bug 全部是测试通过但真机崩的。

**签名**：`jks/` 目录，含 keystore 和明文口令（见 `jks/readme.txt`）。个人自用工具，不上架应用市场，故签名文件入库。

---

## 7. 已知问题

- **`assets/tts/lexicon_full.txt`（2MB）无任何代码引用。** 是模型自带词典的参考副本，`NativeTts` 写的是 `filesDir/tts/lexicon.txt`。可以直接删。
- `app/src/main/ic_launcher-playstore.png` 放在 `res/` 目录下，不参与编译，是 Android Studio 导出图标时的落点。保留即可。
- `TtsRepository.prepared` 是无上限的 `HashMap`，只在 `release()` 清空。长时间使用会缓慢增长，当前场景可接受。
- Gradle 有一条 `Configuration.setVisible()` 弃用警告，来自 **AGP 内部**，不是本项目代码。Gradle 10 才移除，届时升级 AGP 即可。
