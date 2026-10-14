# 汉字识字（Android 原生实现）技术方案

> 本文档面向"接手写代码的 AI/开发者"，描述目标架构与已验证的技术结论。
> 文档中标注 **[已验证]** 的结论均在本机实际跑过探针脚本，非推测。
>
> **五条不可协商的结论**（详见对应章节）：
> 1. 笔画用 Compose Canvas 自绘，**不引第三方库**（§3.1）
> 2. 拼音用 `com.github.houbb:pinyin` 的 `NUM_LAST`（§3.2）
> 3. 朗读用自带 mp3 音节库，**不用系统 TTS、不用 ExoPlayer**（§3.3）
> 4. 拼音必须用内置专用字体，**不能用系统字体**（§3.5）
> 5. 声调转换必须按 §3.5.5 四条规则，否则拼音会显示错误
>
> 仓库根目录下的 `IMPLEMENTING_ANDROID_PLAN.md` 是早期 AI 产出的**废弃草稿**，与本文冲突时以本文为准。

---

## 1. 项目信息

仓库已创建：`C:\Users\guyue\code\hanzi-literacy-android`（git remote 已配置）。
Android Studio 已生成 **Compose Activity** 骨架并提交（`70396f4`）。**在此骨架上开发，不要重建工程。**

| 项 | 值 |
|---|---|
| 仓库 / 根项目名 | `hanzi-literacy-android` |
| app 模块名 | `app` |
| applicationId / 包名 | `com.pupil.hanzi` |
| minSdk | **31**（Android 12）✅ 已配置 |
| 目标设备 | Android 12+ 平板 |

数据源仓库（**只读参考，不要改动**）：`C:\Users\guyue\code\pupil-learning-tool`

---

## 2. 技术栈

### 2.1 构建与语言

**当前状态：已升级到最新稳定版，`assembleDebug` 通过。**

| 组件 | 版本 | 说明 |
|---|---|---|
| Gradle | **9.8.0** | Android Studio 初始模板是 9.6.0，已手动升级 |
| Android Gradle Plugin | **9.4.1** | 模板是 9.4.0，已升级 |
| Kotlin | **2.4.21** | 模板是 2.2.10，已升级（2.5.0 仍是 Beta，不用） |
| Compose BOM | **2026.09.00** | 模板是 2026.02.01，已升级 |
| JDK | **17** | `compileOptions` 已从 `VERSION_11` 提到 `VERSION_17` |

> ⚠️ **不要退回模板的旧版本。** Android Studio 的项目模板刻意锁定在较旧版本（AGP 9.4.0 / Kotlin 2.2.10 / Compose BOM 2026.02.01），这是模板的保守策略，**不是推荐配置**。上述版本已核实为各组件的最新稳定版并编译通过。

**已验证：AGP 9.4.1 无需 `org.jetbrains.kotlin.android` 插件。** `app/build.gradle.kts` 只 apply `android.application` + `kotlin.compose`，Kotlin 代码正常编译。此前草稿里"AGP 9 内置 Kotlin、不能再 apply kotlin-android"的说法**属实**。

### 2.2 已加入的依赖

`libs.versions.toml` 中已配置好，接手的代码直接用即可：

| 依赖 | 版本 | 用途 |
|---|---|---|
| `androidx.compose:compose-bom` | 2026.09.00 | 覆盖 ui / material3 / foundation / animation |
| `androidx.core:core-ktx` | 1.19.1 | |
| `androidx.activity:activity-compose` | 1.13.0 | |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | `collectAsStateWithLifecycle()` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | ViewModel |
| `androidx.datastore:datastore-preferences` | 1.2.1 | 三个开关的持久化 |
| `com.github.houbb:pinyin` | 0.4.0 | 汉字→拼音（§3.2） |

### 2.3 关于构建输出里的 deprecation 警告（**正常，不必处理**）

每次编译都会出现：

```
> Configure project :app
The Configuration.setVisible(boolean) method has been deprecated.
  → scheduled to be removed in Gradle 11
```

与本项目代码无关。`problems-report.html` 那个链接是 Gradle 9.x 的正常输出格式，不是错误。

### 2.4 可选依赖：自然语音

坐标 `sherpa-onnx-1.13.8.aar` —— **不在 Maven**，需从 GitHub Release 下载后放进 `app/libs/`：

```
https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
```

**整条链路可选，不进默认构建**（体积见 §8 风险 5）。

**刻意不引入**：ExoPlayer/Media3（太重，用系统 `MediaPlayer`）、Lottie、任何汉字笔画第三方库（不存在，见 §3.1）。

### 2.5 `settings.gradle.kts` 已配置阿里镜像

骨架把 `maven.aliyun.com` 三条镜像放在 `google()` / `mavenCentral()` **之前**，并启用 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`。**保持原样**，不要在 `build.gradle.kts` 里另加仓库声明（会直接报错）。


---

## 3. 关键选型结论

### 3.1 汉字笔画：没有第三方库，用 Compose Canvas 自绘 ✔

**[已验证]** 全网检索（Maven Central / JitPack / GitHub）确认：**不存在可用的 Android 汉字笔画渲染库**。cnchar 全家桶是纯 TypeScript，无 Java/Kotlin 移植；GitHub 上所谓"Kotlin 笔顺动画"项目全部是从零手写 Canvas 的教程文章，无一演化为维护中的库。

这不是短板——渲染层本就该用框架原生能力：

- `androidx.compose.ui.graphics.vector.PathParser`（Compose 内置）解析 SVG path 字符串
- `android.graphics.PathMeasure.getSegment()` 逐段生长实现笔顺动画
- 田字格、米字格、辅助线全部是 `Canvas.drawLine` / `drawPath`

**不引入任何绘图库。**

### 3.2 拼音：houbb/pinyin ✔

三个候选对比：

| 库 | 多音字 | 声调 | 结论 |
|---|---|---|---|
| `com.github.houbb:pinyin:0.4.0` | ✅ | ✅ | **选用** |
| `com.belerweb:pinyin4j:2.5.1` | ✅ | ✅ | 2016 年停更，备选 |
| `com.github.promeg:TinyPinyin` | ❌ 单音 | ❌ 无调 | 排除 |

**[已验证] API 形态**（`javap` 解包 + 实际运行确认）：

```java
// 静态入口，最常用
List<String> all = PinyinHelper.toPinyinList(char, PinyinStyleEnum.NUM_LAST);
String word = PinyinHelper.toPinyin("重复", PinyinStyleEnum.NUM_LAST);  // "chong2 fu4"

// 声母/韵母（需自行组装 PinyinContext，见下方警告）
IPinyin p = new Pinyin();
List<String> sheng = p.shengMuList(str, ctx);
List<String> yun   = p.yunMuList(str, ctx);
```

**`PinyinStyleEnum.NUM_LAST` 是本项目的关键选择**，实测输出：

```
重 -> [zhong4, chong2, tong2]
长 -> [zhang3, chang2]
行 -> [xing2, hang2, xing4, hang4, heng2]
一 -> [yi1, yi2, yi4]
```

输出格式 `声母韵母+声调数字`，**与 `voice/` 目录下 mp3 文件名逐字相同**（`chong2.mp3`）。这让"拼音"和"音频资源"用同一个字符串索引，零转换。

其他枚举：`NORMAL`→`zhong`，`DEFAULT`→`zhòng`，`NUM_LAST`→`zhong4`。

⚠️ **[已验证] 声母/韵母 API 有坑**：`PinyinContext.newInstance()` 返回的对象 `segment()` 为 null，直接调 `shengMuList()` 会抛 NPE；库内没有现成的默认 SPI 实现（`nlp-common` 里只有分词器，不含拼音 SPI）。

**处理方案**：不要依赖该 API。声母/韵母在 App 内按字符串前缀自行切分——声母是 21 个固定前缀（`zh ch sh b p m f d t n l g k h j q x r z c s y w`）的最长匹配，剩下即韵母。这既绕开 SPI，又让红/蓝双色渲染完全可控。

⚠️ **[已验证] houbb 的读音列表比 cnchar 音频库更全**。例如「一」给出 `yi1 yi2 yi4`、`不」给出 `bu4 fou3 fou1 fu1 bu2`。**存在部分读音没有对应 mp3 的情况**，App 必须做兜底（见 §5.3）。

### 3.3 朗读：分两条路径

| 路径 | 实现 | 多音字准确度 | 体积 |
|---|---|---|---|
| **默认：音节库** | `voice/*.mp3` + 系统 `MediaPlayer` | ✅ **100%**（用户选第几个音节就播第几个） | +14MB（数据已在仓库） |
| 可选：自然语音 | sherpa-onnx + Matcha 中文模型 | ✅（靠自定义 lexicon，§3.4） | +115MB 模型 +~50MB AAR |

**不使用系统 `TextToSpeech`**：无情感，且多音字判断不可靠——正是用户明确否决的点。

**不使用 ExoPlayer/Media3**：我们播的是 5–6KB 单音节 mp3，不是流媒体/播客。系统 `MediaPlayer` 零依赖足够。若实测延迟有问题，备选 `SoundPool`（专为短音效设计，同样是框架原生）。

### 3.4 sherpa-onnx 自定义 lexicon：**已实测可行** ✔

用 Python 绑定（`uv` 环境 + `matcha-icefall-zh-baker` + `vocos-22khz-univ.onnx`）跑了四组探针，结论：

| 探针 | 问题 | 结果 |
|---|---|---|
| P3 | 强制 `重` 为 chong1/chong2/chong4/zhong1/zhong4，输出是否都不同？ | ✅ 五种两两不同；**chong2 与参照字「虫」样本数完全相等（7175 = 7175）**，证明写入的音节确实生效 |
| P4b | 单字覆盖会不会污染词组读音？ | ✅ **不会**。强制 `长→zhang4` 后 `长江` 仍读 chang2（14064 未变）；强制 `重→chong2` 后 `重复` 仍读 chong2 fu4。**词条优先于单字条** |
| P4b-Q3 | 对照组 | ✅ 不含该字的文本（你好/天地）零变化 |
| Noise | 方法学 | ⚠️ 见下 |

**结论**：可以生成一份"用户选什么读音就写什么"的 lexicon 打包进 App，多音字问题彻底绕开 TTS 的自动判断。词级读音不受单字覆盖影响。

### 3.5 拼音字体：**必须内置专用字体** ✔

#### 3.5.1 先纠正一个前提

**[已验证] 当前 HTML 版本根本没有引入任何字体。** 全仓库搜索结果：

```
@font-face     → 无匹配
@import        → 无匹配
*.ttf/otf/woff → 无文件
font-family    → 仅 2 处，均为 'Microsoft YaHei', sans-serif
```

拼音 `.pinyin` 是 `font-family: inherit`，最终落到 Windows 自带的 `Microsoft YaHei`。`cnchar` 的 9575 个 `draw/*.json` 中也不含任何字体资源（grep `font|ttf|otf|woff` 零命中）——cnchar 自己不注入字体，汉字是由调用页面的字体渲染的。

**所以这不是"移植现有字体方案"，而是"从零建立字体方案"。** 跨到 Android 后 `Microsoft YaHei` 不存在，默认回落到 Noto Sans CJK，拼音字母形态与教材的差距会比现在**更明显**。必须显式内置字体。

#### 3.5.2 字体信息（已实测 fontTools 解析）

仓库根目录的 `印氪先生汉语拼音优化版W4-Regular-1.001.ttf`，173,692 字节：

| 项 | 值 |
|---|---|
| family | `印氪先生汉语拼音 优化版W4` |
| subfamily / weight | `Regular` / `usWeightClass 400` |
| PostScript name | `Mr.inker-Pinyin-YHB-W4` |
| version | `1.001` |
| unitsPerEm | 2048 |
| 字符总数 | 624 字形 / 615 个 codepoint |
| 版权 | `Typeface © (邹文彪). 2023. All Rights Reserved` |
| 许可元数据 | **nameID 13/14（license / licenseURL）均不存在** |

⚠️ **许可**：字体内嵌元数据只有 "All Rights Reserved"，无 OFL/Apache 声明。字体作者是邹文彪（印氪字库），此类商用字体通常授权个人及非商业学习使用。项目定位为自用，若将来要分发，需自行向印氪确认授权范围。

#### 3.5.3 关键字符覆盖（已验证）

| 字符类 | 覆盖 | 说明 |
|---|---|---|
| `a-z` `A-Z` | **52/52** | 基础字母齐全 |
| ASCII 可见字符 | **95/95** | 无缺字 |
| 带调元音（预组合） | **24/24** | `ā á ǎ à` `ē é ě è` `ī í ǐ ì` `ō ó ǒ ò` `ū ú ǔ ù` `ǖ ǘ ǚ ǜ` 全有 |
| 大写带调元音 | **26/26** | |
| 组合附加符号 U+0300–U+036F | 9 个 | `U+0300/0301/0302/0303/0304/0307/0308/030A/030C` |
| 拼音专用字母 | **2/17** | 有 `ɑ`(U+0251) 和 `ɡ`(U+0261)，**其余 IPA 类字母缺失** |
| CJK 汉字 | **0** | **纯拼音字体，不含汉字** |

两个关键事实：

1. **`ɑ`(U+0251) 与 `a`(U+0061) 是两个不同字形**（`same=False`），advance 宽度都是 1444。即字体真的画了教材式的单层 `ɑ`。同理 `ɡ`(advance 1262) 与 `g`(1258) 不同。
2. **字体不含任何汉字** —— 正好符合需求：田字格里的汉字是 Canvas 画的 SVG 笔画（§3.1），本就不走字体。

#### 3.5.4 放置方式与文件名（Android 硬性约束）

`res/font/` 的资源文件名**必须**只含小写 `a-z`、`0-9`、`_`，否则 AAPT2 直接报 `invalid file name`。当前中文文件名必须重命名：

```
印氪先生汉语拼音优化版W4-Regular-1.001.ttf   ✗ 非法
        ↓ 重命名
app/src/main/res/font/pinyin_regular.ttf     ✔ 合法，R.font.pinyin_regular
```

```kotlin
// 拼音专用 TextStyle —— 只作用于拼音，不影响全局
val PinyinFont = FontFamily(Font(R.font.pinyin_regular))

@Composable
fun PinyinText(text: String, initialColor: Color, finalColor: Color) {
    // 声母红 / 韵母蓝，两段分别着色（§5.1）
    Text(text = text, style = TextStyle(fontFamily = PinyinFont, fontSize = 20.sp))
}
```

**不要设全局字体**。标题、按钮、界面文字继续用系统默认（Noto Sans CJK）；只有拼音行用 `PinyinFont`。

#### 3.5.5 ⚠️ 声调转换：四个已踩过的坑

houbb 输出的是 `hao3` 这种「音节+声调数字」，必须转成 `hǎo` 才能显示。**这个转换极易写错**，以下是实测踩出的四条规则，配一套 53 个真实音节的验证用例：

**① 声调标在「韵母核」上，不是第一个元音。**
```
you3  -> yǒu   （标 u，不是 yǒou）
wei4  -> wèi   （标 i，不是 wùei）
jiao1 -> jiāo  （标 a）
```

**② 韵母核优先级：`a` > `o` > `e`，否则取最后一个「真元音」。**
`n`/`m`/`ê` 不算韵母内的元音，只能整个韵母单独成音（`en`→`ēn`，不是 `ěn`）。

**③ 必须用预组合字符，不能用基字母+组合符。**
`nǚ` 必须是 `n` + `U+01DA`。写成 `n` + `u` + `U+0308` 会显示成 `nv̌`（ü 的两点丢失），这是实测中最容易犯的错。

**④ `y`/`w` 是正字法符号，不是真正的声母。**
拼音数据里 `yu`/`yuan`/`wei` 的 `y`/`w` 之后的部分已经是一个完整韵母，直接处理即可；且 `yu` 的 `u` **不带两点**（`yuán` 不是 `yüán`）。数据中的 `v` 才代表 `ü`（`nv3` → `nǚ`，`lve4` → `lüè`）。

参考实现（Python，逻辑直译为 Kotlin 即可）：

```python
TONE_VOWELS = {
    "a": ["ā","á","ǎ","à","a"], "o": ["ō","ó","ǒ","ò","o"],
    "e": ["ē","é","ě","è","e"], "i": ["ī","í","ǐ","ì","i"],
    "u": ["ū","ú","ǔ","ù","u"], "ü": ["ǖ","ǘ","ǚ","ǜ","ü"],
    "n": ["n","n","ň","n","n"],   "m": ["m","m","m","m","m"],
}
TONES = {"1":0, "2":1, "3":2, "4":3, "5":4}

def mark_final(fin: str, ti: int) -> str:
    fin = fin.replace("v", "ü")
    if not fin: return fin
    for v in "aoe":                      # ② 优先级 a > o > e
        if v in fin:
            i = fin.index(v)
            return fin[:i] + TONE_VOWELS[v][ti] + fin[i+1:]
    idx = -1
    for i, ch in enumerate(fin):
        if ch in "aoeiuü": idx = i       # 取最后一个真元音
    if idx < 0:
        return TONE_VOWELS.get(fin, [fin]*5)[ti] + fin[1:]
    return fin[:idx] + TONE_VOWELS[fin[idx]][ti] + fin[idx+1:]

def to_display(syl: str) -> str:
    """syl: hao3 -> hǎo ; nv3 -> nǚ ; you3 -> yǒu"""
    ti = TONES[syl[-1]]
    body = syl[:-1]
    ini = next((x for x in sorted(INITIALS, key=len, reverse=True)
                if body.startswith(x)), "")
    return ini + mark_final(body[len(ini):], ti)
```

**[已验证]** 用 23 个声母 × 37 个韵母 × 5 个声调穷举生成 **4391 个不同的显示串、15140 次字符出现，全部命中字体字形，零缺字**。53 个教材标准音节抽样（含 `hǎo / zhōng / chóng / nǚ / lǜ / lüè / yuán / yǒu / wèi / shuǐ / kuài / zhuāng / qióng`）全部转换正确。

#### 3.5.6 四线三格（可选增强）

教材上的拼音书写格（四线三格）**不是字体能解决的**，需要用 `Canvas.drawLine` 画四条横线 + 居中排布字母，与 §5.2 的田字格渲染放在同一层实现。列为可选功能，不阻塞主线。

### 3.6 ⚠️ sherpa-onnx 两条必须知道的实现约束

**（1）单字条必须原地改写并去重。**
`lexicon.txt` 中单字条大量重复（`长` 出现 226 条、`重` 202 条）。正确做法：替换该字**首次**出现的那一行，删除其后所有同名行。**追加到文件末尾完全无效**——实测追加模式下输出与 stock 逐样本相同。

**（2）TTS 实例只有第一次 `generate()` 可复现。**

```
A. 同一实例连续 4 次 generate("你好")：[12242, 12051, 12084, 12172]  ← 漂移 1~2%
B. 每次新建实例：                        [12242, 12242, 12242, 12242]  ← 稳定
D. num_threads 1/2/4：  无影响
```

Android 端实现时**不要缓存并反复复用 TTS 实例**，否则每次播放的发音参数都可能不同。

---

## 4. 数据资产

全部复用现有 `cnchar-data/cnchar-data/` + 仓库根目录的拼音字体，**不需要下载任何新数据**。

其中 `res/font/pinyin_regular.ttf`（源文件 `印氪先生汉语拼音优化版W4-Regular-1.001.ttf`，170KB）是**唯一不来自 `cnchar-data/` 的资产**，详见 §3.5。

| 目录 | 数量 | 大小 | 格式 | 用途 |
|---|---|---|---|---|
| `draw/` | 9575 | 31MB | `{"strokes":[SVG path],"medians":[[[x,y],...]]}` | 笔顺；**1024×1024 网格**，即 Make Me a Hanzi / hanzi-writer-data 格式（cnchar 本身就派生自它），格式通用、随时可换数据源 |
| `voice/` | 2055 | 14MB | `<音节><声调>.mp3`，5KB 左右 | 音节朗读 |
| `explanation/` | 8616 | — | `{"好辩":"1.亦作…\n2.谓喜欢与人辩论。"}` | 字词释义。**key 是词不是单字**，见 §5.4 |
| `words/words.json` | 1 | — | 组词表 | 本期不用 |

### 4.1 资产打包（重要）

`assets/` 下 11000+ 个小文件会让 APK 体积膨胀、首次访问慢。方案：

1. 构建期用 Gradle 脚本把 `draw/` 与 `explanation/` 各**合并成单个文件**（结构化拼接，非 ZIP，保持随机访问简单）：
   - `draw_all.json`：`{"好":{"strokes":[...],"medians":[...]}, ...}`
   - `explanation_all.json`：原样合并
2. `voice/` 的 mp3 **必须保持独立文件**（音频流式播放，不适合合并）。
3. 首次启动时把 `draw_all.json`（约 31MB）解析进内存索引 `Map<String, StrokeData>`；解析耗时需实测，若过长则改为懒加载分片。
4. APK 体积预估：31MB（笔画）+ 14MB（音频）+ 释义 ≈ **50MB+**，属正常范围。

> 若第一步解析耗时不可接受，备选方案：首次启动导入 SQLite（Room 或裸 `SQLiteOpenHelper`），按需查询。**先测再定，不要预先上 Room。**

---

## 5. 功能实现要点

### 5.1 拼音渲染（专用字体 + 红/蓝双色）

**字体**：必须用内置的拼音专用字体 `res/font/pinyin_regular.ttf`，详见 §3.5。用系统字体渲染拼音**不可接受**——字母形态与教材差距明显。

**显示文本**：houbb 的 `NUM_LAST` 输出（`hao3`）必须经 §3.5.5 的转换才能显示为 `hǎo`，且**必须遵循该节的四条规则**（韵母核定位、预组合字符、y/w 正字法处理）。

**双色**：沿用现有 HTML 的视觉约定——声母红色、韵母蓝色。声母用 §3.2 的前缀最长匹配切分。

多音字（读音列表 > 1）需要视觉标记 + 可切换交互，见 §6。

### 5.2 笔画渲染

```
输入：char → draw_all.json 查 {strokes[], medians[]}
坐标系：1024 → 目标像素（等比缩放 + 居中偏移）
绘制层（自底向上）：
  1. 田字格：边框 + 中横 + 中竖（虚线可选）
  2. 灰色轮廓（可选，showOutline）
  3. 红色部首（可选）
  4. 已完成笔画：实心填充
  5. 当前笔画：按动画进度用 PathMeasure 截取
  6. 未完成笔画：浅灰轮廓
```

动画用 `Animatable` 驱动 `progress: Float 0..1`，笔画间 delay 可配。medians（每笔的运笔中线）用于**逐笔分解视图**和描红引导线。

### 5.3 音频播放

```
选中的读音（如 chong2）
  ├─ assets/voice/chong2.mp3 存在 → MediaPlayer 播放   ← 默认路径
  └─ 不存在 → 依次尝试：
       1. 该字读音列表中的第一个有 mp3 的读音
       2. 系统 TTS（TextToSpeech, Locale.CHINA）——仅兜底，不作为主路径
       3. 都不行 → 静默失败，不崩溃
```

**[已验证] 兜底是必需的**：houbb 给出的读音多于音频库覆盖范围（如 `yi2`、`fou3` 可能无 mp3）。

整句朗读：逐音节串行播放队列，忽略标点。

### 5.4 释义查询

`explanation/好.json` 的 key 是**词**（`好艾`、`好辩`、`好不好`…）。查单字「好」的方法：

1. 读入 `explanation/好.json`
2. 遍历所有 key，取**以「好」开头**的词条
3. 按词长度升序（短词优先）展示：`词 → 释义`
4. 过滤掉纯单字自身与明显无关的长词

**[已验证]** 数据量 8616 个文件，格式规整，解析成本低。

---

## 6. UI 设计（重新设计，不照搬 HTML）

### 6.1 布局状态机

单 Activity，两个互斥状态 + 常驻开关区：

```
┌─────────────────────────────────────┐
│  [展示拼音] [笔顺动画] [语音]  ← 常驻 │  设置区（DataStore 持久化）
├─────────────────────────────────────┤
│                                     │
│         状 态 A：字符网格            │  常态。上方有「编辑」按钮
│      （拼音 + 田字格 横排/网格）      │
│                                     │
├─────────────────────────────────────┤
│         [编辑]                      │
└─────────────────────────────────────┘
              ↓ 点击「编辑」
┌─────────────────────────────────────┐
│  [展示拼音] [笔顺动画] [语音]         │
├─────────────────────────────────────┤
│         状态 B：输入                 │  TextField + [清空] [确认]
│      内容预填上次输入                 │
└─────────────────────────────────────┘
              ↓ 点「确认」
         回到状态 A
```

- **不分栏**（用户明确否决）
- **不用全屏详情页**（用户明确否决）
- 详情走**模态弹窗**
- 空白输入 → Snackbar 提示（原 HTML 是 3 秒自动消失的 alert，用 Material3 `Snackbar` 更原生）

### 6.2 字符网格项

```
      hǎo          ← 拼音，声母红 / 韵母蓝；多音字加浅灰底
   ┌───────┐
   │  好   │      ← 田字格 Canvas，可开关动画
   └───────┘
    🔊            ← 语音按钮（语音开关开启时显示；标点符号占位对齐）
```

多音字交互：点击拼音区域 → `DropdownMenu` 列出全部读音，选中即改（与 HTML 版行为一致）。

标点符号：无拼音无朗读，格子内直接显示标点，用 placeholder 对齐语音按钮位置。

### 6.3 详情模态弹窗

`ModalBottomSheet`（平板上比 Dialog 更适合、内容可滚动）：

```
┌────────────────────────────────────┐
│  好                    [多音字: hǎo hào] │
├────────────────────────────────────┤
│   ┌─────────┐      读音切换 chips    │
│   │  大     │      hǎo  hào         │
│   │ 田字格  │                        │
│   └─────────┘      [播放] [笔顺动画] │
├────────────────────────────────────┤
│  释义                                │
│  好人  1.对自己和对自己以外的人      │
│  好几  1.许多。2.Quite...           │
├────────────────────────────────────┤
│  笔顺分解（每笔一个小田字格，横排）    │
│  ①  ②  ③  ④ ...                    │
└────────────────────────────────────┘
```

---

## 7. 工程结构

```
HanziLiteracy/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml          ← 版本目录，集中管理
├── gradle.properties
├── app/
│   ├── libs/sherpa-onnx-1.13.8.aar     ← 可选功能，手动下载
│   ├── build.gradle.kts                ← 含 assets 合并任务
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── draw_all.json           ← 构建期生成
│       │   ├── explanation_all.json    ← 构建期生成
│       │   └── voice/*.mp3             ← 直接拷贝
│       ├── java/com/pupil/hanzi/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── StrokeRepository.kt      ← draw_all.json → 内存索引
│       │   │   ├── ExplanationRepository.kt ← 释义查询
│       │   │   ├── VoiceAssets.kt           ← mp3 存在性判断
│       │   │   └── SettingsStore.kt         ← DataStore
│       │   ├── domain/
│       │   │   ├── PinyinService.kt         ← houbb 封装 + 声韵母切分
│       │   │   ├── AudioPlayer.kt           ← MediaPlayer 串行队列
│       │   │   └── NaturalTts.kt            ← sherpa-onnx（可选）
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── MainScreen.kt             ← 开关区 + 状态机
│       │   │   ├── CharGrid.kt
│       │   │   ├── CharCell.kt
│       │   │   └── DetailSheet.kt
│       │   └── stroke/
│       │       ├── TianzigeCanvas.kt         ← 田字格 + 动画
│       │       └── StrokeTimeline.kt         ← 逐笔分解
│       └── res/
│           └── font/
│               └── pinyin_regular.ttf      ← 根目录 ttf 重命名（§3.5.4）
```

架构：MVVM。`HanziViewModel` 持有 `uiState: StateFlow`，开关走 DataStore，详情弹窗状态（选中字 + 选中读音）也在此。

---

## 8. 风险与未决项

| # | 项 | 影响 | 处理 |
|---|---|---|---|
| 1 | `draw_all.json` 31MB 首次解析耗时 | 首启卡顿 | 先测；超标则改 SQLite 分片或懒加载 |
| 2 | `voice/` 2055 个 mp3 在 `assets/` 的读取延迟 | 点字→出声延迟 | 先测 `MediaPlayer.create()` 单次耗时；超标改 `SoundPool` 预载 |
| 3 | houbb 读音多于 mp3 覆盖 | 部分读音无声 | §5.3 的三级兜底链，已设计 |
| 4 | ~~AGP 9 与 Kotlin 插件组合~~ | — | ✅ **已解决**：骨架仅用 `android.application` + `kotlin.compose` 即编译通过（§2.1） |
| 5 | sherpa-onnx AAR 体积（~50MB）+ 模型（115MB） | APK 过大 | 整条自然语音链路做成**可选**，不进默认构建 |
| 6 | sherpa 自定义 lexicon 生成逻辑 | 自然语音多音字 | Python 已验证可行，Android 侧需复刻 P4b 的"原地改写+去重" |
| 7 | 字体授权范围 | 分发受限 | 内嵌元数据仅 "All Rights Reserved"，无 OFL/Apache 声明。自用无碍；若将来分发需向印氪确认（§3.5.2） |

---

## 9. 验收标准

功能：
- [ ] 输入汉字 → 横排展示田字格 + 红蓝双色拼音
- [ ] 多音字可切换读音，切换后拼音、音频同步更新
- [ ] 标点符号正确占位、不参与朗读
- [ ] 单字朗读发音与所选读音一致（多音字必须 100% 准确）
- [ ] 整句朗读顺序正确、跳过标点
- [ ] 点击汉字弹出详情：大田字格 + 全部读音 + 释义 + 笔顺动画 + 笔顺分解
- [ ] 笔顺动画笔顺正确、循环播放
- [ ] 三个开关即时生效并持久化（杀进程重启后保持）
- [ ] 「编辑」按钮回到输入框且内容预填上次
- [ ] 空输入有提示不崩溃
- [ ] 无 mp3 的读音有兜底、不崩溃
- [ ] Android 12+ 平板上横竖屏均正常、edge-to-edge 不遮挡

质量：
- [ ] 无网络依赖（完全离线可用）
- [ ] 11k+ 资产访问无明显卡顿
- [ ] 无第三方绘图/播放库（除 sherpa-onnx 可选链路）
- [ ] 拼音使用内置字体 `res/font/pinyin_regular.ttf`，非系统字体
- [ ] 声调显示正确：`nǚ lǜ lüè yuán yǒu wèi shuǐ kuài zhuāng qióng` 全部为教材标准形（§3.5.5）

---

## 附：与已废弃草稿的差异

`IMPLEMENTING_ANDROID_PLAN.md` 中已被本文推翻或修正的内容：

| 草稿说法 | 实际情况 |
|---|---|
| "AGP 9 内置 Kotlin，不能 apply kotlin-android 插件" | ✅ **属实**，已由骨架编译验证（§2.1） |
| "用户已选只做汉字工具单页" | ✅ 采纳 |
| "用户已选全量读音" | ✅ 采纳（并已设计 mp3 兜底） |
| 拼音走 houbb | ✅ 结论正确，本文补充了 API 实测细节与声母/韵母的 SPI 坑 |
| 未涉及 sherpa-onnx | 本文新增，含 4 组实测结论 |
| 未涉及字体 | 本文新增，含字体元数据解析 + 穷举覆盖验证 + 声调转换规则 |
| 建议 Kotlin 2.4.21 但未指出模板锁在 2.2.10 | 已升级到最新稳定版，勿退回模板值（§2.1） |