"""合并词库片段 -> assets 输出，并做基本校验。

用法：python data_src/merge.py
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(ROOT)
ASSETS = os.path.join(PROJECT, "app", "src", "main", "assets")

LEVELS = ["A1", "A2", "B1", "B2"]
REQUIRED = {"w", "zh", "p"}


def load_words(pattern):
    words = []
    for path in sorted(glob.glob(os.path.join(ROOT, pattern))):
        with open(path, encoding="utf-8") as fh:
            try:
                data = json.load(fh)
            except json.JSONDecodeError as exc:
                print(f"  !! JSON 解析失败 {os.path.basename(path)}: {exc}")
                sys.exit(1)
        for idx, w in enumerate(data.get("words", [])):
            missing = REQUIRED - set(w.keys())
            if missing:
                print(f"  !! {os.path.basename(path)} 第{idx+1}条缺字段 {missing}: {w}")
                sys.exit(1)
            words.append(w)
    return words


def main():
    os.makedirs(ASSETS, exist_ok=True)
    total = 0
    # 跨等级去重：同一个词只保留首次出现（低等级优先）
    seen_global = set()
    for level in LEVELS:
        words = load_words(f"{level.lower()}_*.json")
        seen = set()
        uniq = []
        for w in words:
            # 同拼写 + 同词性视为同一条，保留首次出现
            key = (w["w"].strip().lower(), w.get("p", ""))
            if key in seen:
                continue
            seen.add(key)
            # 跨等级重复：同拼写 + 同词性，只保留第一次出现
            cross_key = (w["w"].strip().lower(), w.get("p", ""))
            if cross_key in seen_global:
                continue
            seen_global.add(cross_key)
            uniq.append(w)
        out = {"v": 1, "level": level, "count": len(uniq), "words": uniq}
        target = os.path.join(ASSETS, f"words_{level.lower()}.json")
        with open(target, "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
        size = os.path.getsize(target)
        total += len(uniq)
        print(f"{level}: {len(uniq):>4} 词  ({size/1024:.1f} KB)  -> {os.path.basename(target)}")
    print(f"合计 {total} 词")


if __name__ == "__main__":
    main()
