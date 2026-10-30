"""从 FreeDict 的 deu-eng + eng-zho 构建德汉查询词库。

数据来源与许可：
  - deu-eng (FreeDict+WikDict, CC BY-SA 3.0)
  - eng-zho (FreeDict+WikDict, CC BY-SA 3.0)
  均由 Wiktionary / DBnary 自动生成，释义为机器转译，仅供参考。

用法：
  python data_src/build_dict.py --sample 5000    # 抽样检查
  python data_src/build_dict.py                  # 全量生成
"""
import argparse
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
DEU_ENG = os.path.join(RAW, "deu-eng", "deu-eng.tei")
ENG_ZHO = os.path.join(RAW, "eng-zho", "eng-zho.tei")

TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")
# 德语词允许：字母（含变音）、连字符、空格
VALID_WORD = re.compile(r"^[A-Za-zÄÖÜäöüß\- ]{2,40}$")


def clean(text: str) -> str:
    text = TAG_RE.sub("", text)
    text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    text = text.replace("&quot;", '"').replace("&apos;", "'")
    return WS_RE.sub(" ", text).strip()


def load_eng_zho(path):
    """解析英汉词典 -> {英文词小写: [中文释义...]}"""
    mapping = {}
    if not os.path.exists(path):
        print(f"缺少文件 {path}")
        return mapping
    with open(path, encoding="utf-8") as fh:
        content = fh.read()
    for block in re.findall(r"<entry\b[^>]*>(.*?)</entry>", content, re.S):
        orth = re.search(r"<orth[^>]*>(.*?)</orth>", block, re.S)
        if not orth:
            continue
        head = clean(orth.group(1)).lower()
        if not head:
            continue
        zh_list = []
        for q in re.findall(
            r'<cit[^>]*type="trans"[^>]*>(.*?)</cit>', block, re.S
        ):
            m = re.search(r'<quote[^>]*xml:lang="zh"[^>]*>(.*?)</quote>', q, re.S) \
                or re.search(r"<quote[^>]*>(.*?)</quote>", q, re.S)
            if m:
                val = clean(m.group(1))
                if val and val not in zh_list:
                    zh_list.append(val)
        if zh_list:
            mapping.setdefault(head, [])
            for v in zh_list:
                if v not in mapping[head]:
                    mapping[head].append(v)
    return mapping


def iter_deu_entries(path, limit=None):
    """流式解析德英词典，逐条 yield 结构化数据。"""
    buf = []
    depth = 0
    count = 0
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if "<entry" in line:
                depth = 1
                buf = [line]
                continue
            if depth:
                buf.append(line)
                if "</entry>" in line:
                    depth = 0
                    count += 1
                    yield parse_entry("".join(buf))
                    if limit and count >= limit:
                        return


def parse_entry(block):
    orth = re.search(r"<orth[^>]*>(.*?)</orth>", block, re.S)
    if not orth:
        return None
    word = clean(orth.group(1))
    pos_m = re.search(r"<pos>(.*?)</pos>", block, re.S)
    pos = clean(pos_m.group(1)) if pos_m else ""
    gen_m = re.search(r"<gen>(.*?)</gen>", block, re.S)
    gen = clean(gen_m.group(1)) if gen_m else ""
    num_m = re.search(r"<number>(.*?)</number>", block, re.S)
    number = clean(num_m.group(1)) if num_m else ""

    defs = []
    for q in re.findall(r"<quote[^>]*xml:lang=\"en\"[^>]*>(.*?)</quote>", block, re.S):
        v = clean(q)
        if v and v not in defs:
            defs.append(v)
    if not defs:
        for d in re.findall(r"<def[^>]*>(.*?)</def>", block, re.S):
            v = clean(d)
            if v and v not in defs:
                defs.append(v)
    return {
        "word": word,
        "pos": pos,
        "gen": gen,
        "number": number,
        "defs": defs,
    }


def normalize_pos(pos: str) -> str:
    p = pos.lower()
    if p.startswith("n"):
        return "n"
    if p.startswith("v"):
        return "v"
    if p.startswith("adj"):
        return "adj"
    if p.startswith("adv"):
        return "adv"
    if p.startswith("prep"):
        return "prep"
    if p.startswith("pron"):
        return "pron"
    if p.startswith("conj"):
        return "conj"
    if p.startswith("num"):
        return "num"
    if p.startswith("art"):
        return "art"
    return p


def gender_of(gen: str, word: str):
    g = gen.lower()
    if "masc" in g:
        return "m"
    if "fem" in g:
        return "f"
    if "neut" in g:
        return "n"
    # 从冠词推断
    low = word.lower()
    if low.startswith("der "):
        return "m"
    if low.startswith("die "):
        return "f"
    if low.startswith("das "):
        return "n"
    return ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sample", type=int, default=0, help="只处理前 N 条")
    ap.add_argument("--report", type=int, default=20, help="打印抽样条数")
    args = ap.parse_args()

    print("加载英汉词典…")
    ez = load_eng_zho(ENG_ZHO)
    print(f"  英汉词条 {len(ez)} 个")

    stats = {"total": 0, "valid": 0, "with_zh": 0, "skipped": 0}
    out = []
    seen = set()

    limit = args.sample if args.sample else None
    for rec in iter_deu_entries(DEU_ENG, limit):
        stats["total"] += 1
        if not rec:
            stats["skipped"] += 1
            continue
        word = rec["word"]
        if not VALID_WORD.match(word):
            stats["skipped"] += 1
            continue
        if rec["number"] == "pl":
            stats["skipped"] += 1
            continue
        key = word.lower()
        if key in seen:
            stats["skipped"] += 1
            continue
        seen.add(key)
        stats["valid"] += 1

        zh = None
        for d in rec["defs"]:
            cand = d.lower().strip()
            for prefix in ("to ", "the ", "a ", "an "):
                if cand.startswith(prefix):
                    cand = cand[len(prefix):]
            cand = cand.strip(" .;,")
            if cand in ez and ez[cand]:
                zh = "；".join(ez[cand][:3])
                break
        if zh:
            stats["with_zh"] += 1

        out.append({
            "w": word,
            "pos": normalize_pos(rec["pos"]),
            "gen": gender_of(rec["gen"], word),
            "zh": zh or "",
            "en": " / ".join(rec["defs"][:2]),
        })

    print(f"扫描 {stats['total']} 条，有效 {stats['valid']}，"
          f"有中文 {stats['with_zh']}，跳过 {stats['skipped']}")
    if stats["valid"]:
        print(f"中文覆盖率 {stats['with_zh'] / stats['valid'] * 100:.1f}%")

    print("\n--- 抽样 ---")
    shown = 0
    for r in out:
        if r["zh"] and shown < args.report:
            print(f"  {r['w']:<20} [{r['pos']}] {r['zh']}   <- {r['en']}")
            shown += 1

    if args.sample == 0 and out:
        target = os.path.join(ROOT, "dict_raw.json")
        with open(target, "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False)
        print(f"\n已写出 {target}，共 {len(out)} 条")


if __name__ == "__main__":
    main()
