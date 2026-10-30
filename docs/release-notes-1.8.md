## Wortschatz 1.8

德语名词的复数形式没有规律可循，必须与单数一起记。这一版把复数补到所有能看到词条的地方，并修掉了一个会显示「复数 null」的数据缺陷。

### 名词复数全场景显示

| 位置 | 展示形式 |
|---|---|
| 学习卡片正面 | 单词下方直接给出「复数 die Männer」，翻面前就能看到 |
| 学习卡片背面 | 「阳性 der · 复数 die Männer」 |
| 查词结果列表 | 释义下方一行浅灰「复数 …」 |
| 收藏列表 | 同上 |
| 书内单词管理 | 同上 |
| 学习记录（已学习 / 已掌握） | 同上 |
| 拼写测试判分后 | 在「拼写正确 / 正确答案」下方补一行复数 |

非名词不显示该行；复数缺失的条目（不可数名词、专有名词、仅复数词）自动省略，不占位。

### 修复：名词显示「复数 null」

旧解析器用 `JSONObject.optString()` 读字段，遇到 JSON 里的 `null` 会返回字符串 `"null"`，
导致形如 `der Hauser — 复数 null` 的错误展示。内置词库里有约 3.7 万处显式 `null`。

- 解析器改为显式判断 `isNull()`，字段缺失、`null`、字面量 `"null"` 一律按空处理
- 数据库升级到 v4，清理历史数据中的 `"null"` 字符串（只动这几个字段的脏值，学习进度不受影响）

### 教材词单补充复数

`docs/wordbooks/` 下两份词单补齐 4 条原本缺失的复数：die Welt → die Welten、
die Grammatik → die Grammatiken、Herr → die Herren、der Step → die Steps。

### 版本

versionCode 9 / versionName 1.8
