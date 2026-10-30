# 单词本导入格式规范

Wortschatz 支持通过 JSON 文件导入自定义词表。本文是你需要遵守的全部规则。

## 一、最简可用格式

```json
{
  "words": [
    { "w": "der Hund", "zh": "狗" },
    { "w": "laufen", "zh": "跑" }
  ]
}
```

只要 `words` 数组存在、每条有 `w`（德语词形）和 `zh`（中文释义）就能导入。
其余字段都有默认值。

## 二、完整字段说明

```json
{
  "v": 1,
  "name": "我的词表",
  "count": 2,
  "words": [
    {
      "w": "der Hund",
      "l": "Hund",
      "p": "n",
      "g": "m",
      "pl": "die Hunde",
      "zh": "狗",
      "en": "dog",
      "ex": "Der Hund bellt.",
      "exZh": "狗在叫。",
      "lv": "A1",
      "tg": "动物"
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `w` | ✅ | 词形。名词建议带定冠词（`der Hund`），学习卡片会照此显示 |
| `zh` | ✅ | 中文释义，多个义项用 `；` 分隔 |
| `l` | | 词根/原形，用于检索与音频匹配。省略时自动从 `w` 去掉冠词 |
| `p` | | 词性代码，见下表。省略时为空字符串，不影响使用 |
| `g` | | 名词性别：`m` 阳性 / `f` 阴性 / `n` 中性。**只有 p=n 时需要** |
| `pl` | | 复数形式，建议写成 `die Hunde` |
| `en` | | 英文对照 |
| `ex` | | 德语例句 |
| `exZh` | | 例句的中文翻译 |
| `lv` | | 等级 `A1`–`B2`，省略时按 `A1` 处理 |
| `tg` | | 主题标签，如 `动物`、`工作` |
| `src` | | 来源标记，导入时会被强制设为 `custom` |

顶层除 `words` 外的字段（`v` / `name` / `count`）都是元信息，程序不读取，仅供你备注。

## 三、词性代码

| 代码 | 含义 | 代码 | 含义 |
|---|---|---|---|
| `n` | 名词 | `prep` | 介词 |
| `v` | 动词 | `conj` | 连词 |
| `adj` | 形容词 | `pron` | 代词 |
| `adv` | 副词 | `num` | 数词 |
| `int` | 感叹词 | `phr` | 短语/固定表达 |

短语和整句一律用 `phr`，比如 `Wie geht's?`。

## 四、重要约定：空字段请省略，不要写 `null`

```json
✅ { "w": "laufen", "zh": "跑" }
❌ { "w": "laufen", "zh": "跑", "pl": null, "g": null }
```

原因：App 用 Android 的 `optString()` 读取字段，它对 JSON 的 `null` 会返回字符串 `"null"`，
于是词条会显示成「复数 null」这种脏数据。省略字段则走默认值，安全。

## 五、导入时的去重规则

- 词库中**已存在相同词形**（按 `l` 或去除冠词的 `w` 小写比对）的条目会被**跳过**
- 因此重复导入同一份文件不会产生重复词条，可以放心多次导入
- 即便你的释义与内置词库不同，也不会覆盖，内置词条优先

导入成功后的提示会告诉你实际新增了多少条。

## 六、如何导入

1. 把 JSON 文件传到手机（任意位置，`Download` 目录最方便）
2. App → 设置 → 词库 → **导入自定义词表（JSON）**
3. 在系统文件选择器里选中该文件

## 七、示例文件

仓库内提供了两个可直接导入的文件：

| 文件 | 条数 | 说明 |
|---|---|---|
| `docs/wordbooks/menschen-a1-bold.json` | 464 | 教材《Menschen》A1 词汇表中印刷加粗的词条 |

生成脚本在 `data_src/books/`：

```bash
python data_src/books/build_book.py
```

数据源是 `menschen_a1_data.py` 里的 `(词形, 释义, 词性, 复数)` 四元组，
脚本负责拆冠词、推断性别、清理复数标注并输出 JSON。

## 八、批量制作建议

如果要从 Excel 或其他词表转换，只要最终产出符合上面的结构即可。一个最小转换脚本：

```python
import json

rows = [
    ("der Apfel", "苹果", "n", "m", "die Äpfel"),
    ("essen", "吃", "v", None, None),
]

words = []
for w, zh, p, g, pl in rows:
    item = {"w": w, "zh": zh, "p": p, "lv": "A1", "tg": "自定义"}
    if g:
        item["g"] = g
    if pl:
        item["pl"] = pl
    words.append(item)

json.dump({"words": words}, open("my-words.json", "w", encoding="utf-8"),
          ensure_ascii=False, indent=1)
```

注意用 `ensure_ascii=False`，否则德语变音字母会变成 `\u00e4` 这类转义（虽然仍能正确解析，但不利于人工检查）。
