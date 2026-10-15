# 汉字识字

给小学生用的汉字学习平板应用。输入一段文字，看清每个字的拼音、笔顺动画和朗读。

原生 Android（Kotlin + Jetpack Compose），面向 Android 12 及以上的平板。全部数据随应用打包，**不需要联网，也不申请任何权限**。

---

## 功能

**输入文字** — 输入一整段中文，按「确定」，应用会为每个字查出拼音、加载笔顺，并显示成一个字卡网格。

**拼音** — 拼音由上下文推断，同一个字在不同词里读音不同时会自动选择正确的那个。比如「长江」读 cháng jiāng 而不是 cháng zhǎng。多音字下方会标出，可以点开切换成其他读音。

**笔顺动画** — 点任意字卡打开详情，笔顺按真实顺序逐笔演示，带虚线格子辅助线（田字格）。

**朗读** — 两种方式：

- **播放全部文本**：整句用同一个声音朗读，语气自然。这条路用内置的神经网络语音模型合成。
- **点单字喇叭**：只念当前这个字。

**设置** — 四个开关，都会被记住：

| 开关 | 作用 |
|---|---|
| 显示拼音 | 字卡上是否标注拼音 |
| 笔顺动画 | 关掉则直接显示完整字形，不演示 |
| 单字朗读 | 字卡上是否显示喇叭按钮 |
| 保持屏幕常亮 | 学习时不自动锁屏 |

---

## 安装

从源码构建需要一个 Android 开发环境（JDK 17、Android SDK 37、Gradle 由 wrapper 自动下载）。

```bash
git clone <本仓库>
cd hanzi-literacy-android

./gradlew :app:assembleDebug        # 构建
./gradlew :app:testDebugUnitTest    # 跑测试
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，用 Android Studio 打开项目直接点运行也可以。

> **首次构建会慢一些**，约 10 分钟。应用里有 63MB 的语音模型和 50MB 的笔顺数据，构建脚本还会跑一次资源合并（`tools/merge_assets.py`）。之后增量构建只要几秒。

**安装到设备**：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 已知限制

- **包体积大。** Debug 包约 286MB，因为语音引擎的原生库没有做 ABI 拆分，四种架构全打进去了。Release 包会小很多。数据本身（笔顺 50MB + 模型 63MB + 音节库 14MB）是无法再压缩的。
- **只覆盖常用字。** 笔顺和释义数据来自 cnchar，约 1.8 万个汉字。生僻字可能查不到，此时字卡仍会显示，但没有笔顺动画。
- **朗读是离线合成。** 整句朗读由内置模型实时生成，语速和音色是固定的，无法调整。点单字用的是预录的音节库。
- **无导入导出。** 输��的文本不会保存，关掉应用就没了。

---

## 致谢

- 汉字笔顺、释义数据：[cnchar](https://github.com/thekvinf-cn/cnchar)
- 上下文拼音：[HanLP](https://github.com/hankcs/HanLP)
- 语音合成：[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（`zh_CN-xiao_ya-medium` 模型）
