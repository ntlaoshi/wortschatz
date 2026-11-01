# NOTICE — 第三方数据与素材许可

本文件列出 Wortschatz（德语单词）使用的第三方数据、素材及其许可条款。
本项目自有代码与人工精编词条的版权归项目作者所有；下列内容按其各自许可使用。

## 一、词库数据

### 1. 中文维基词典（中文释义）

- 来源：https://zh.wiktionary.org
- 许可：CC BY-SA 3.0（https://creativecommons.org/licenses/by-sa/3.0/）
- 使用范围：`words_ext.json` 及由此合并进 `words_a1..b2.json` 的释义字段（约 8697 条）
- 修改说明：去除了维基标记与模板，释义做了简体化与精简；名词缺失性别的不予收录，
  变位形式条目（form-of）已过滤。

### 2. 英文维基词典 / Wiktextract（名词性别与复数）

- 来源：https://en.wiktionary.org ，结构化数据经 https://kaikki.org 的 Wiktextract 提取
- 许可：CC BY-SA 3.0
- 使用范围：名词条目的 `gender`（词性）与 `plural`（复数形式）字段

### 3. FrequencyWords（词频分级）

- 来源：https://github.com/hermitdave/FrequencyWords （OpenSubtitles 德语词频表）
- 许可：MIT
- 使用范围：`data_src/build_zhwik.py` 中的词频过滤与 A1–B2 分级阈值

## 二、发音音频

`app/src/main/assets/voice/` 下 9642 个 MP3 由本地神经语音模型离线合成，
生成脚本见 `data_src/gen_voice_piper.py`：

- 引擎：[Piper](https://github.com/rhasspy/piper)，MIT License
- 模型：`de_DE-thorsten-medium`，训练数据为 [Thorsten-Voice](https://github.com/thorstenMueller/Thorsten-Voice)，CC0 1.0（公有领域）
- 输出：单声道 22050Hz、32kbps MP3

MIT 与 CC0 均允许商用与再分发，因此这些音频可随 APK 自由分发。

> 历史说明：1.18 及更早版本的音频由微软 Edge 神经网络语音（`de-DE-KatjaNeural`）
> 批量合成，未取得再分发授权，已全部重新生成替换。

## 三、其他

### MyMemory 翻译 API

- 来源：https://mymemory.translated.net
- 用途：本地词库查不到时的在线兜底翻译
- 匿名免费额度约 1000 次/天，超出或离线时功能不可用

### Material Icons

- 来源：Jetpack Compose `material-icons-extended`
- 许可：Apache License 2.0

## 四、示例词书

`docs/wordbooks/a1-core.json`（936 条）由 `data_src/books/build_a1_core.py` 生成：

- 主体为词库自身的 A1 词条（释义来自中文维基词典，见第一节）；
- 仅并入词库缺失的标准 A1 高频词汇（短语、数字等）；
- 顺序经固定种子打散，不保留任何教材页面结构。

早期版本曾附带按《Menschen A1》（Hueber Verlag）教材页面逐条转录的词表，
因选词与编排对应他人教材存在版权风险，已从当前版本移除，仅保留在 git 历史中。

## 五、免责

本项目为个人学习项目，词库释义未经逐条校对，可能存在义项偏差。引用维基数据时请遵守
CC BY-SA 3.0 的署名与相同方式共享要求。
