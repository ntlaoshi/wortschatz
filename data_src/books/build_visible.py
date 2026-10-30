# -*- coding: utf-8 -*-
"""把用户提供的 4 张照片（教材第 27、51 页）中的词条导出成独立词单。

与 build_book.py 的区别：不按页码整页导出，而是只取照片里**实际出现**的条目，
逐条与 menschen_a1_data.py 对齐，对齐不上的会列出来、需在 SUPPLEMENT 里补录。

用法：python data_src/books/build_visible.py
输出：docs/wordbooks/menschen-a1-p27-51-visible.json
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

from menschen_a1_data import ALL_PAGES  # noqa: E402
from menschen_a1_p27_51 import VISIBLE_27_51  # noqa: E402
from build_book import build_payload, clean_word, write_payload  # noqa: E402

OUT_REL = "docs/wordbooks/menschen-a1-p27-51-visible.json"
BOOK_NAME = "Menschen A1 · 第 27/51 页"

# 既有词库中没有、需要单独补录的条目：(印刷词形, 释义, 词性, 复数)
# 上一轮整理按「加粗」筛选，这几条当时判为普通字重被漏掉，现按照片补录。
SUPPLEMENT = {
    "Und dir?": ("Und dir?", "那你呢？", "phr", None),
    "der Possessivartikel, -": ("der Possessivartikel, -", "物主冠词", "n", None),
    "die Familien-Cloud, -s": ("die Familien-Cloud, -s", "家庭云", "n", "die Familien-Clouds"),
}


def merge_zh(a: str, b: str) -> str:
    """合并同一词形的两条释义：der Mann 在第 27 页是「丈夫」、第 51 页是「男人」。

    App 导入时按词根去重，只保留一条，所以这里先把两处释义并起来，
    用「；」分隔并去掉重复项。
    """
    parts = []
    for chunk in (a, b):
        for piece in chunk.replace("，", "；").split("；"):
            piece = piece.strip()
            if piece and piece not in parts:
                parts.append(piece)
    return "；".join(parts)


def main():
    index = {}
    for entry in ALL_PAGES:
        index.setdefault(clean_word(entry[0]).lower(), entry)

    resolved = []
    placed = {}
    missing = []
    for raw, zh in VISIBLE_27_51:
        key = clean_word(raw).lower()
        hit = index.get(key)
        if hit is None:
            hit = SUPPLEMENT.get(raw)
        if hit is None:
            missing.append((raw, zh))
            continue

        if key in placed:
            # 同一词形在另一页出现且释义不同，合并到已收集的那一条
            pos = placed[key]
            old = resolved[pos]
            resolved[pos] = (old[0], merge_zh(old[1], hit[1] or zh), old[2], old[3])
            continue
        placed[key] = len(resolved)
        # 既有数据的释义可能与照片不同（der Mann 在词库里是「男人」，照片第 27 页写「丈夫」），
        # 取并集，避免丢掉其中一个义项。
        resolved.append((hit[0], merge_zh(hit[1] or zh, zh), hit[2], hit[3]))

    if missing:
        print("以下条目在既有数据中找不到，需要补录：")
        for raw, zh in missing:
            print(f"  ({raw!r}, {zh!r}),")
        return

    payload = build_payload(resolved, BOOK_NAME, "第 27/51 页照片中列出的全部词条")
    out_file = os.path.join(ROOT, OUT_REL)
    write_payload(payload, out_file)


if __name__ == "__main__":
    main()
