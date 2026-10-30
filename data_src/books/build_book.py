# -*- coding: utf-8 -*-
"""把教材加粗词条整理成 App 可导入的单词本 JSON。

用法：
    全量导出：  python data_src/books/build_book.py
    只导某几页：python data_src/books/build_book.py --pages 51,28,27 \
                    --name "Menschen A1 · 第 27/28/51 页" \
                    --out docs/wordbooks/menschen-a1-p27-28-51.json
"""
import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = os.path.join(ROOT, "docs", "wordbooks")

sys.path.insert(0, HERE)
from menschen_a1_data import ALL_PAGES, PAGES  # noqa: E402

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
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", default="", help="只导出指定页码，逗号分隔，如 51,28,27")
    ap.add_argument("--name", default="", help="写入 JSON 的书名")
    ap.add_argument("--out", default="", help="输出路径（相对仓库根目录）")
    args = ap.parse_args()

    if args.pages:
        wanted = [int(p.strip()) for p in args.pages.split(",") if p.strip()]
        missing = [p for p in wanted if p not in PAGES]
        if missing:
            print("这些页码没有数据:", missing)
            print("可用页码:", sorted(PAGES))
            return
        source = []
        for p in wanted:
            source.extend(PAGES[p])
        page_tag = "/".join(str(p) for p in wanted)
        default_name = f"Menschen A1 · 第 {page_tag} 页"
        default_out = "docs/wordbooks/menschen-a1-p" + "-".join(str(p) for p in wanted) + ".json"
        note_pages = page_tag
    else:
        source = ALL_PAGES
        default_name = "Menschen A1 · 加粗词条"
        default_out = "docs/wordbooks/menschen-a1-bold.json"
        note_pages = "全部页面"

    out_rel = args.out or default_out
    out_file = out_rel if os.path.isabs(out_rel) else os.path.join(ROOT, out_rel)
    os.makedirs(os.path.dirname(out_file), exist_ok=True)
    book_name = args.name or default_name

    seen = set()
    words = []

    for raw, zh, pos, plural in source:
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
        "name": book_name,
        "note": "摘自教材词汇表（%s）中印刷加粗的条目，共 %d 条" % (note_pages, len(words)),
        "count": len(words),
        "words": words,
    }

    with open(out_file, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)

    size = os.path.getsize(out_file) / 1024
    print(f"书名：{book_name}")
    print(f"生成 {len(words)} 条 -> {os.path.relpath(out_file, ROOT)} ({size:.1f} KB)")

    from collections import Counter
    print("词性分布:", dict(Counter(w["p"] for w in words)))
    print("无冠词的名词:", sum(1 for w in words if w["p"] == "n" and not w.get("g")))
    print("\n样例:")
    for w in words[:6]:
        print(" ", w["w"], "|", w["zh"], "|", w["p"], "|", w.get("g"), "|", w.get("pl"))


if __name__ == "__main__":
    main()
