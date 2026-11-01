## Wortschatz 1.19

本次为**版权合规整改**，不新增学习功能，但替换了两处关键素材。

### 一、语音换成可自由分发的本地模型

原内置音频由微软 Edge 神经网络语音批量合成，未取得再分发授权。现已用本地模型重新生成全部音频：

- 引擎：[Piper](https://github.com/rhasspy/piper)（MIT）
- 模型：`de_DE-thorsten-medium`，训练数据 Thorsten-Voice（CC0 公有领域）
- 覆盖：**9642 个词形**（原 5407 个，新增覆盖 B2 与低频词）
- 格式：单声道 22050Hz / 32kbps MP3，与旧文件同名，App 无需改动

APK 体积 45.1 MB（原 43.8 MB），覆盖词形增加 78%，体积仅增 1.3 MB。

### 二、示例词书改为自建

`docs/wordbooks/a1-core.json`（936 条）：以词库 A1 词条为主体，
仅并入词库缺失的标准 A1 高频词，顺序按固定种子打散，
不再保留任何教材页面结构与分组。生成脚本 `data_src/books/build_a1_core.py` 可复现。

### 三、署名与许可

- 新增 `NOTICE.md`，逐项列明词库、发音、图标、在线接口的来源与许可
- App「设置 → 数据来源与许可」同步展示六项来源
- README 许可章节改为表格并链向 NOTICE

### 验证

- `Speaker: speak=das Kind -> offline asset kind.mp3`，AudioTrack 正常输出
- 抽样检测：时长 0.6–0.8 秒，mean_volume −17dB，非静音
- 生成的 9642 条无失败、无异常小文件

### 版本

versionCode 20 / versionName 1.19
