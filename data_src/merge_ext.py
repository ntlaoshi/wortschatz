# -*- coding: utf-8 -*-
"""合并词库：中文维基释义 + 英文维基名词性别 + 现有精编词条。

优先级：现有精编词条（含例句、人工校订）> 维基词条补充。
输出单一 words_ext.json，等级字段沿用，App 端按等级筛选用。
"""
import glob
import json
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets")
EXTRACT = os.path.join(HERE, "zhwik_extract.jsonl")
DE_FULL = os.path.join(HERE, "raw", "de_full.jsonl")
OUT = os.path.join(ASSETS, "words_ext.json")

GENDER_WORD = {"m": "der", "f": "die", "n": "das"}
POS_TAG = {
    "n": "名词", "v": "动词", "adj": "形容词", "adv": "副词",
    "prep": "介词", "conj": "连词", "pron": "代词", "num": "数词",
    "int": "感叹词", "phr": "短语",
}
GENDER_TAG = {"masculine": "m", "feminine": "f", "neuter": "n"}


def load_existing():
    """现有精编词条，键为 (lemma 小写, 词性)。"""
    seen = {}
    total = 0
    for path in sorted(glob.glob(os.path.join(ASSETS, "words_*.json"))):
        # 排除本脚本自己的输出，否则二次运行会把扩充词全判为已存在
        if os.path.basename(path) == os.path.basename(OUT):
            continue
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        for w in data.get("words", []):
            total += 1
            key = ((w.get("l") or w.get("w") or "").strip().lower(), w.get("p", ""))
            seen[key] = w
    return seen, total


def load_gender():
    """从英文维基词典提取德语名词的性别与复数。"""
    info = {}
    if not os.path.exists(DE_FULL):
        print("警告：英文维基数据缺失，名词性别将为空")
        return info
    with open(DE_FULL, encoding="utf-8") as fh:
        for line in fh:
            if '"pos": "noun"' not in line:
                continue
            try:
                d = json.loads(line)
            except Exception:
                continue
            if d.get("lang_code") != "de" or d.get("pos") != "noun":
                continue
            word = (d.get("word") or "").strip()
            if not word:
                continue
            key = word.lower()
            if key in info:
                continue
            gender = None
            for s in d.get("senses", []):
                for t in s.get("tags") or []:
                    if t in GENDER_TAG:
                        gender = GENDER_TAG[t]
                        break
                if gender:
                    break
            plural = None
            cands = [
                ((f.get("form") or "").strip(), set(f.get("tags") or []))
                for f in d.get("forms", [])
                if "plural" in (f.get("tags") or [])
            ]

            def ok(cand, tags):
                """排除与格复数与同形重复：Wundern ← Wunder 不是主格复数。"""
                if not cand or cand.lower() == key:
                    return False
                if {"dative", "genitive", "accusative"} & tags:
                    return False
                if cand.lower() == key + "n" and key.endswith(("er", "el", "en", "chen")):
                    return False
                return True

            for cand, tags in cands:
                if ok(cand, tags):
                    plural = cand
                    break
            if gender or plural:
                info[key] = (gender, plural)
    return info


def main():
    existing, existing_total = load_existing()
    print(f"现有精编词条 {existing_total} 条（去重后 {len(existing)} 组）")

    gender_info = load_gender()
    print(f"英文维基名词性别数据 {len(gender_info)} 条")

    rows = []
    stats = Counter()
    with open(EXTRACT, encoding="utf-8") as fh:
        for line in fh:
            d = json.loads(line)
            word = d["w"]
            pos = d["p"]
            key = (word.lower(), pos)
            if key in existing:
                stats["dup_existing"] += 1
                continue

            g = None
            plural = d.get("pl")
            if pos == "n":
                extra = gender_info.get(word.lower())
                if extra:
                    g = extra[0] or g
                    plural = extra[1] or plural
                if not g:
                    # 没有性别就不收录，德语名词缺冠词无法使用
                    stats["noun_no_gender"] += 1
                    continue

            display = f"{GENDER_WORD[g]} {word}" if g else word
            pl_text = None
            if plural:
                pl_text = f"die {plural}" if pos == "n" else plural

            rows.append({
                "w": display,
                "l": word,
                "p": pos,
                "g": g,
                "pl": pl_text,
                "zh": d["zh"],
                "en": None,
                "ex": None,
                "exZh": None,
                "lv": d["lv"],
                "tg": POS_TAG.get(pos, "词语"),
            })
            stats["added"] += 1
            existing[key] = True  # 防止同键重复

    out = {"v": 1, "count": len(rows), "words": rows}
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))

    size = os.path.getsize(OUT) / 1024 / 1024
    print(f"\n新增 {len(rows)} 条 -> words_ext.json ({size:.2f} MB)")
    print("统计:", dict(stats))
    print("等级分布:", dict(Counter(r["lv"] for r in rows)))
    print("词性分布:", dict(Counter(r["p"] for r in rows)))
    print("有复数的名词:", sum(1 for r in rows if r["p"] == "n" and r["pl"]))
    print("\n样例:")
    for r in rows[:5]:
        print(" ", r["w"], "|", r["zh"][:30], "|", r["lv"], "| pl:", r["pl"])


if __name__ == "__main__":
    main()
