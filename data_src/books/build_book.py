# -*- coding: utf-8 -*-
"""把教材加粗词条整理成 App 可导入的单词本 JSON。

用法：python data_src/books/build_book.py
输出：docs/wordbooks/menschen-a1-bold.json
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = os.path.join(ROOT, "docs", "wordbooks")
OUT_FILE = os.path.join(OUT_DIR, "menschen-a1-bold.json")

sys.path.insert(0, HERE)
from menschen_a1_data import ALL_PAGES  # noqa: E402

ARTICLES = {"der": "m", "die": "f", "das": "n"}
# 复合词里带连字符或大写的专有名词不再拆冠词

# ", -er" / ", ¨-e" / ", -" 这类复数标注
PLURAL_MARK = re.compile(r",\s*[¨\-][^\s]*\s*$")


def clean_word(raw: str) -> str:
    """把词条整理成干净的德语词形。"""
    s = raw.strip()

    # "steht→stehen"：取原形
    if "→" in s:
        s = s.split("→")[-1].strip()

    # "die EU = die Europäische Union"：只留缩写
    if " = " in s:
        s = s.split(" = ")[0].strip()

    # "zählen weiter→weiterzählen" 已在上一步处理
    # "mein/mein-"：取第一个
    if "/" in s:
        parts = [p.strip() for p in s.split("/") if p.strip()]
        if parts and not parts[0].endswith("-"):
            s = parts[0]
        else:
            s = parts[0].rstrip("-")

    # 去掉 "(er hilft)" "(Plural)" 之类的括注
    s = re.sub(r"\s*\([^)]*\)", "", s)

    # 去掉复数标注 ", -er"
    s = PLURAL_MARK.sub("", s)

    return s.strip().strip(",").strip()


def split_article(w: str):
    """返回 (冠词性别, 纯词形)。"""
    for art, gender in ARTICLES.items():
        if w.lower().startswith(art + " "):
            return gender, w[len(art) + 1:].strip()
    return None, w


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    seen = set()
    words = []

    for raw, zh, pos, plural in ALL_PAGES:
        w = clean_word(raw)
        if not w:
            continue
        gender, lemma = split_article(w)

        # 名词统一补上冠词，便于 App 里显示性别
        if pos == "n" and gender is None:
            # 专有名词/国名没有冠词，保持原样
            pass

        # 词根去掉首尾标点：词库里 "Auf Wiedersehen" 不带感叹号，
        # 教材写成 "Auf Wiedersehen!"，不统一会导致导入时判为两个不同的词。
        lemma = lemma.strip().strip(".!?。！？,，;；")

        key = (lemma.lower(), pos)
        if key in seen:
            continue
        seen.add(key)

        # 只写出有值的字段：Android 的 optString 会把 JSON null 读成字符串 "null"，
        # 那样复数会显示成「复数 null」，因此空值一律省略。
        entry = {
            "w": w,
            "l": lemma,
            "p": pos,
            "zh": zh,
            "lv": "A1",
            "tg": "教材",
        }
        if gender:
            entry["g"] = gender
        if plural:
            entry["pl"] = plural
        words.append(entry)

    payload = {
        "v": 1,
        "name": "Menschen A1 · 加粗词条",
        "note": "摘自教材词汇表中印刷加粗的条目，共 %d 条" % len(words),
        "count": len(words),
        "words": words,
    }

    with open(OUT_FILE, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)

    size = os.path.getsize(OUT_FILE) / 1024
    print(f"生成 {len(words)} 条 -> {os.path.relpath(OUT_FILE, ROOT)} ({size:.1f} KB)")

    from collections import Counter
    print("词性分布:", dict(Counter(w["p"] for w in words)))
    print("无冠词的名词:", sum(1 for w in words if w["p"] == "n" and not w["g"]))
    print("\n样例:")
    for w in words[:6]:
        print(" ", w["w"], "|", w["zh"], "|", w["p"], "|", w["g"], "|", w["pl"])


if __name__ == "__main__":
    main()
