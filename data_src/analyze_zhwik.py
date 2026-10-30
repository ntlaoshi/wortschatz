# -*- coding: utf-8 -*-
"""分析中文维基词典中的德语词条，评估可入库规模。"""
import gzip
import json
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
GZ = os.path.join(HERE, "raw", "zhwik", "zhwik.jsonl.gz")
FREQ = os.path.join(HERE, "raw", "zhwik", "de_50k.txt")

CJK = re.compile(r"[\u4e00-\u9fff]")


def load_freq():
    ranks = {}
    with open(FREQ, encoding="utf-8") as fh:
        for i, line in enumerate(fh):
            parts = line.split()
            if parts:
                ranks[parts[0].lower()] = i + 1
    return ranks


def main():
    ranks = load_freq()
    pos_counter = Counter()
    total_de = 0
    with_zh = 0
    in_freq = {1000: 0, 3000: 0, 8000: 0, 15000: 0, 50000: 0}
    samples = []
    seen_words = set()

    with gzip.open(GZ, "rt", encoding="utf-8") as fh:
        for line in fh:
            try:
                d = json.loads(line)
            except Exception:
                continue
            if d.get("lang_code") != "de":
                continue
            total_de += 1
            word = (d.get("word") or "").strip()
            pos = d.get("pos") or ""
            pos_counter[pos] += 1

            glosses = []
            for s in d.get("senses", []):
                for g in s.get("glosses", []) or []:
                    if CJK.search(g):
                        glosses.append(g.strip())
            if not glosses:
                continue
            with_zh += 1

            low = word.lower()
            rank = ranks.get(low)
            if rank:
                for lim in in_freq:
                    if rank <= lim:
                        in_freq[lim] += 1

            if len(samples) < 12 and rank and rank < 6000 and low not in seen_words:
                seen_words.add(low)
                samples.append({
                    "word": word, "pos": pos, "rank": rank,
                    "glosses": glosses[:2],
                    "forms": [f.get("form") for f in d.get("forms", [])][:6],
                    "senses_raw": [
                        {k: s.get(k) for k in ("glosses", "tags", "topics") if s.get(k)}
                        for s in d.get("senses", [])[:2]
                    ],
                })

    print(f"德语词条总数: {total_de}")
    print(f"含中文释义: {with_zh} ({with_zh / max(total_de, 1) * 100:.1f}%)")
    print("\n词性分布 (前12):")
    for p, c in pos_counter.most_common(12):
        print(f"  {p or '(空)':12} {c}")
    print("\n在德语词频表内的数量:")
    for lim in sorted(in_freq):
        print(f"  rank <= {lim:6}: {in_freq[lim]}")
    print("\n样本:")
    for s in samples:
        print(json.dumps(s, ensure_ascii=False)[:400])


if __name__ == "__main__":
    main()
