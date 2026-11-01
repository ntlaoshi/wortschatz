# Wortschatz · 德语单词

离线优先的德语词汇学习 App（Android / 原生 Kotlin + Jetpack Compose）。

## 一、功能

| 模块 | 说明 |
|---|---|
| 查词 | 德语拼写 + 中文释义双向检索；前缀匹配优先、完全匹配置顶；本地查不到时可走在线翻译兜底并一键存词库 |
| 背单词 | 一轮里每个词需**连续认识 3 次**才算过：卡片上以三个圆点显示当前进度，评「模糊」或「忘了」会把计数清零并随机插回队列稍后重判；底层仍由 SM-2 间隔重复算法调度复习日期。三档评分（忘了/模糊/认识）既可用底部按钮也可用滑动手势——**左滑认识、右滑忘了、上滑模糊、下滑收藏** |
| 发音 | **内置德语离线音频**（微软神经网络语音合成），不依赖系统 TTS 组件，飞行模式可读；词库外的新词联网时自动下载并缓存；例句优先系统德语语音，缺失时走在线合成；语速 0.5x–1.3x 可调 |
| 生词本 | 任意词条一键收藏，集中回看；背单词时下滑卡片可直接收藏 |
| 统计 | 连续打卡天数、今日已练、已学词条数、各等级词库进度 |
| 设置 | 学习等级开关、每日新词量、自动朗读、语速、发音状态检测、自定义词表导入 |

## 二、技术栈

- Kotlin 2.0.21 + Jetpack Compose（Material 3）
- 原生 SQLite（未引入 Room，避免注解处理器依赖，构建更快）
- minSdk 26 / targetSdk 35 / compileSdk 35
- AGP 8.7.3 + Gradle 8.9 + JDK 17
- 无第三方网络库（在线兜底用 `HttpURLConnection` + 内置 `org.json`）

## 三、构建

```bash
# 环境变量
export JAVA_HOME=D:/Apps/jdk17
export ANDROID_HOME=D:/Apps/Android/Sdk
export GRADLE_USER_HOME=D:/Gradle/home    # 避免占用 C 盘

cd D:/Projects/GermanVocab

gradle assembleDebug      # 调试包（包名 de.wortschatz.app.debug）
gradle assembleRelease    # 正式包，用本地 keystore 签名
```

产物路径：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

安装到已连接的设备或模拟器：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n de.wortschatz.app/de.wortschatz.app.MainActivity
```

## 四、目录结构

```
D:/Projects/GermanVocab/
├── app/src/main/
│   ├── java/de/wortschatz/app/
│   │   ├── MainActivity.kt          入口
│   │   ├── AppViewModel.kt          状态管理与业务编排
│   │   ├── data/                    词条模型、SM-2 算法、SQLite、词库导入、在线查询
│   │   ├── tts/                     系统 TTS 封装
│   │   └── ui/                      各页面与通用组件
│   ├── assets/words_*.json          内置词库（构建产物，勿手改）
│   └── res/                         图标、主题、字符串
├── data_src/                        词库源数据与构建脚本
│   ├── a1_*.json a2_*.json ...      按等级分片的词条源文件（手工维护）
│   ├── merge.py                     合并分片 → assets/words_*.json
│   ├── analyze_zhwik.py             统计中文维基词典的德语词条规模
│   ├── build_zhwik.py               提取并清洗维基词条 → zhwik_extract.jsonl
│   ├── merge_ext.py                 补性别/复数、去重合并 → assets/words_ext.json
│   ├── gen_voice_piper.py           批量合成离线发音（Piper 本地模型）→ assets/voice/*.mp3
│   └── build_dict.py                从 FreeDict 构建大词库的实验脚本（未采用，见下文）
├── screenshots/                     验证截图
├── keystore.properties              签名口令（不入版本库）
└── wortschatz-release.jks           签名密钥库（不入版本库）
```

## 五、词库

当前 **10313 词**：A1 1278 / A2 1623 / B1 2953 / B2 4474。

由两部分合成：

| 来源 | 条数 | 特点 |
|---|---|---|
| 人工精编 | 1616 | 含例句及中文翻译、英文对照、主题标签，名词性别与复数经人工校订 |
| 中文维基词典 | 8697 | 释义为社区人工编纂，简体化后入库；名词性别与复数取自英文维基词典数据 |

### 精编词条的扩充方式

1. 在 `data_src/` 下新增或编辑 `{等级}_{序号}.json`，格式：

```json
{"v":1,"level":"A1","words":[
  {"w":"der Hund","l":"Hund","p":"n","g":"m","pl":"die Hunde",
   "zh":"狗","en":"dog","ex":"Der Hund bellt.","exZh":"狗在叫。","lv":"A1","tg":"动物"}
]}
```

2. 运行 `python data_src/merge.py` 合并（自动跨等级去重，同拼写+同词性只保留首次出现）
3. 递增 `data/SeedLoader.kt` 中的 `SEED_VERSION`，重新构建即可让已安装的 App 增量补词

### 维基词条的构建流程

```bash
python data_src/analyze_zhwik.py   # 统计规模与词性分布
python data_src/build_zhwik.py     # 从中文维基提取，清洗释义、过滤变位形式、按词频分级
python data_src/merge_ext.py       # 补名词性别与复数，与精编词条去重合并
```

词条筛选规则：只保留德语词频表前 22000 名的词；丢弃 `form-of` 变位条目
（如 `red` = reden 的命令式）；名词若无性别则不入库（缺冠词的德语名词无法使用）。

### 为什么不用 FreeDict 自动构建

曾评估 FreeDict 的 deu-eng（51.7 万条）+ eng-zho（2.6 万条）组合，抽样两万条后否定：
中文覆盖率仅 18.4%，且经「德→英→中」二次转译后错误被逐层放大——
`Aasfresser`（食腐动物）被译为「跳水员」，`Aas fressen` 被译为「洗涤」，
`Ais`/`As`/`Aisis` 全部映射到「一个」。

中文维基词典的释义覆盖率是 **99.7%**（173792 / 174278 条），且是人工编纂而非机器翻译，
因此改用该数据源。

## 六、导入自己的单词本

App 支持导入 JSON 词表。**完整格式规范见 [docs/wordbook-format.md](docs/wordbook-format.md)**。

最小可用格式只要两个字段：

```json
{ "words": [ { "w": "der Hund", "zh": "狗" } ] }
```

仓库内附带可直接导入的示例：

| 文件 | 条数 | 内容 |
|---|---|---|
| `docs/wordbooks/a1-core.json` | 936 | A1 核心词汇：词库 A1 全部词条 + 标准 A1 高频词，顺序已打散 |

导入后会自动新建一本同名单词书，学习范围随之切换。

> 说明：早期版本曾附带按教材页面转录的词表，因选词与编排对应他人教材存在版权风险，
> 已改为由 `data_src/books/build_a1_core.py` 生成：以词库自身 A1 词条为主体，
> 仅并入词库缺失的标准 A1 词汇，并统一打散顺序。旧文件保留在 git 历史中。

导入入口：设置 → 词库 → 导入自定义词表（JSON）。
导入时按词形去重，词库里已有的词会被跳过，重复导入不会产生重复条目。

## 七、已知限制

- 在线兜底依赖 MyMemory 免费接口，匿名额度约 1000 次/天，无网络或超限时会提示失败
- 内置发音由本地 Piper 模型预先生成，覆盖词库全部 9642 个词形；模型不可达时仍可回退在线合成
- 例句朗读没有内置音频，设备未装德语语音包时需要联网（单词发音不受影响）
- 维基来源的释义未经逐条校对，个别词条存在义项偏窄或词频定级偏差，建议与权威词典交叉验证

## 八、数据来源与许可

精编词条为项目自行整理。扩充词条涉及第三方数据，完整署名与条款见 **[NOTICE.md](NOTICE.md)**：

| 内容 | 来源 | 许可 |
|---|---|---|
| 中文释义 | [中文维基词典](https://zh.wiktionary.org) | CC BY-SA 3.0 |
| 名词性别与复数 | 英文维基词典，经 [Wiktextract](https://kaikki.org) 提取 | CC BY-SA 3.0 |
| 词频分级 | [FrequencyWords](https://github.com/hermitdave/FrequencyWords) | MIT |
| 离线发音 | [Piper](https://github.com/rhasspy/piper) + Thorsten-Voice 模型 | MIT / CC0 |
| 在线兜底 | [MyMemory](https://mymemory.translated.net) | 免费 API，需署名 |
| 图标 | Material Icons | Apache 2.0 |

维基数据按 CC BY-SA 3.0 使用：本项目对其释义做了简体化与精简整理，
衍生数据同样以 CC BY-SA 3.0 提供；应用内「设置 → 数据来源与许可」页同步展示。

发音音频由本地 Piper 模型（Thorsten-Voice 数据集，CC0）离线合成，
9642 个词形全部内置，不与任何在线语音服务绑定，可随 APK 自由分发。

`data_src/raw/` 下的原始数据（约 1.3 GB）仅用于本地构建，不入版本库。
