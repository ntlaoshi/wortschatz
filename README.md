# Wortschatz · 德语单词

离线优先的德语词汇学习 App（Android / 原生 Kotlin + Jetpack Compose）。

## 一、功能

| 模块 | 说明 |
|---|---|
| 查词 | 德语拼写 + 中文释义双向检索；前缀匹配优先、完全匹配置顶；本地查不到时可走在线翻译兜底并一键存词库 |
| 背单词 | 基于 SM-2 间隔重复算法调度；四档评分（忘了/模糊/认识/太简单）；「忘了」的词本轮末尾重现一次 |
| 发音 | 调用系统 TTS 引擎的 de-DE 语音，装好德语语音包后完全离线；单词与例句都能朗读，语速 0.5x–1.3x 可调 |
| 生词本 | 任意词条一键收藏，集中回看 |
| 统计 | 连续打卡天数、今日已练、已学词条数、各等级词库进度 |
| 设置 | 学习等级开关、每日新词量、自动朗读、语速、TTS 状态检测、自定义词表导入 |

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
│   └── build_dict.py                从 FreeDict 构建大词库的实验脚本（未采用，见下文）
├── screenshots/                     验证截图
├── keystore.properties              签名口令（不入版本库）
└── wortschatz-release.jks           签名密钥库（不入版本库）
```

## 五、词库

当前 **1616 词**：A1 613 / A2 551 / B1 358 / B2 94。
每条含拼写、词性、名词性别与复数、中文释义、英文对照、德语例句及中文翻译、等级标签、主题标签。

### 扩充方式

1. 在 `data_src/` 下新增或编辑 `{等级}_{序号}.json`，格式：

```json
{"v":1,"level":"A1","words":[
  {"w":"der Hund","l":"Hund","p":"n","g":"m","pl":"die Hunde",
   "zh":"狗","en":"dog","ex":"Der Hund bellt.","exZh":"狗在叫。","lv":"A1","tg":"动物"}
]}
```

2. 运行 `python data_src/merge.py` 合并（自动跨等级去重，同拼写+同词性只保留首次出现）
3. 递增 `data/SeedLoader.kt` 中的 `SEED_VERSION`，重新构建即可让已安装的 App 增量补词

### 为什么不内置十万级词典

曾评估用 FreeDict 的 deu-eng（51.7 万条）+ eng-zho（2.6 万条）自动构建德汉词库，
抽样两万条的结果否定了该方案：

- 中文覆盖率仅 18.4%，八成词条拿不到中文
- 释义经「德语→英语→中文」二次转译，错误被逐层放大，实测 `Aasfresser`（食腐动物）被译为「跳水员」，`Aas fressen` 被译为「洗涤」
- 同源条目共享错误释义（`Ais`/`As`/`Aisis` 全部映射到「一个」）

此类数据会让词典的可信度低于「查不到」。因此选择精编词条 + 在线兜底的路线：
低频生僻词由在线翻译按需解决，离线词库保证学习场景的质量。

## 六、已知限制

- 在线兜底依赖 MyMemory 免费接口，匿名额度约 1000 次/天，无网络或超限时会提示失败
- 系统 TTS 若无德语语音包，需在「设置 → 语音设置」中安装德语语音数据
- release 包未开启 R8 混淆压缩（`isMinifyEnabled = false`），体积约 10.9 MB；如需更小可开启并回归测试
- 词库为个人学习用途整理，例句与释义建议与权威词典交叉验证

## 七、数据来源与许可

内置词条为项目自行整理。`data_src/raw/` 下的 FreeDict 数据仅供实验脚本使用，
未纳入 App 内置词库；如需再分发请注意其 CC BY-SA 3.0 许可要求。
