# -*- coding: utf-8 -*-
"""构建「A1 核心词汇」词书：合并词库 A1 词条与教材转录词条，并打散顺序。

背景：原 `menschen-*.json` 按教材页面分组逐条转录，选词与编排直接对应他人教材，
存在「选编」层面的版权风险。本脚本改为：

1. 以词库 A1 全部词条为主体（释义来自中文维基词典，CC BY-SA 3.0，已署名）；
2. 并入教材转录中词库缺失的标准 A1 词汇（短语、数字等），补齐覆盖面；
3. 统一打散顺序，去除与教材页面的对应关系，并去掉「教材」标签。

产物：docs/wordbooks/a1-core.json

用法：python data_src/books/build_a1_core.py
"""
import json
import os
import random
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SRC_BOOK = os.path.join(ROOT, "docs", "wordbooks", "menschen-a1-bold.json")
OUT_BOOK = os.path.join(ROOT, "docs", "wordbooks", "a1-core.json")

SEED = 20261101          # 固定种子，保证可复现
NAME = "A1 核心词汇"
NOTE = "词库 A1 词条 + 标准 A1 高频词，打散顺序，不含教材页面结构"


def clean(s: str) -> str:
    return re.sub(r"^(der|die|das)\s+", "", (s or "").strip()).lower()


def lib_word(entry: dict) -> dict:
    """词库词条 → 词书条目（只保留学习需要的字段）。"""
    item = {
        "w": entry["w"],
        "zh": entry.get("zh", ""),
        "lv": entry.get("lv", "A1"),
    }
    lemma = entry.get("l")
    if lemma:
        item["l"] = lemma
    for k in ("p", "g", "pl", "en", "ex", "exZh"):
        if entry.get(k):
            item[k] = entry[k]
    return item


def main():
    lib = json.load(open(os.path.join(ASSETS, "words_a1.json"), encoding="utf-8"))
    lib_words = {}
    for entry in lib.get("words", []):
        key = clean(entry.get("l") or entry.get("w"))
        if key and key not in lib_words:
            lib_words[key] = lib_word(entry)

    seen = set(lib_words)
    added = 0
    if os.path.exists(SRC_BOOK):
        src = json.load(open(SRC_BOOK, encoding="utf-8"))
        for entry in src.get("words", []):
            key = clean(entry.get("l") or entry.get("w"))
            if not key or key in seen:
                continue
            item = {
                "w": entry["w"],
                "zh": entry.get("zh", ""),
                "lv": "A1",
            }
            for k in ("l", "p", "g", "pl"):
                if entry.get(k):
                    item[k] = entry[k]
            lib_words[key] = item
            seen.add(key)
            added += 1

    words = list(lib_words.values())
    random.Random(SEED).shuffle(words)

    out = {
        "v": 1,
        "name": NAME,
        "note": NOTE,
        "count": len(words),
        "words": words,
    }
    with open(OUT_BOOK, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)

    print(f"词库 A1 词条：{len(lib_words) - added}")
    print(f"并入教材转录补全：{added}")
    print(f"合计：{len(words)} 条 → {OUT_BOOK}")


if __name__ == "__main__":
    main()
