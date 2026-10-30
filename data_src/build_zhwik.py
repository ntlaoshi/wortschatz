# -*- coding: utf-8 -*-
"""从中文维基词典提取德语词条，产出中间数据。

过滤规则：
  - 只保留实词词性，丢弃专有名词与词性未知项
  - 丢弃变位/变形条目（form-of、alt-of），避免 red / sing 这类屈折形式混入
  - 释义必须含中文
  - 只保留德语词频表前 N 名的词，保证是实际会用到的词
"""
import gzip
import json
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
GZ = os.path.join(HERE, "raw", "zhwik", "zhwik.jsonl.gz")
FREQ = os.path.join(HERE, "raw", "zhwik", "de_50k.txt")
OUT = os.path.join(HERE, "zhwik_extract.jsonl")

MAX_RANK = 22000

POS_MAP = {
    "noun": "n",
    "verb": "v",
    "adj": "adj",
    "adv": "adv",
    "prep": "prep",
    "conj": "conj",
    "pron": "pron",
    "num": "num",
    "intj": "int",
    "phrase": "phr",
}

TAG_MAP = {
    "n": "名词", "v": "动词", "adj": "形容词", "adv": "副词",
    "prep": "介词", "conj": "连词", "pron": "代词", "num": "数词",
    "int": "感叹词", "phr": "短语",
}

CJK = re.compile(r"[\u4e00-\u9fff]")
# 变形条目的释义特征（简体与繁体写法都要覆盖）
FORM_OF = re.compile(
    r"(的單數|的單数|的複數|的复数|的命令式|的過去式|的过去式|的過去分詞|的过去分词"
    r"|的現在時|的现在时|的變格|的变格|的比較級|的比较级|的最高級|的最高级"
    r"|的屬格|的属格|的與格|的与格|的賓格|的宾格|的屈折|的强变化|的弱变化"
    r"|的第一人称|的第二人称|的第三人称)"
)
# 释义开头的词性缩写，如 "pron." "adj."
POS_PREFIX = re.compile(r"^(?:pron|adj|adv|prep|conj|num|int|interj|n|v|vt|vi|m|f|A|N)\.\s*", re.I)
# "pl.Reaktoren反应堆" 这类把复数形式写进释义的条目
PL_PREFIX = re.compile(r"^pl\.[A-Za-zÄÖÜäöüß\-]*(?:\([^)]*\))?\s*", re.I)
# 释义开头的语法标记，如 〈中〉 ［阳］ 【口】
ANGLE_PREFIX = re.compile(r"^[〈\[［【(](中|阳|阴|中性|阳性|阴性|口|俚|方|旧|古|贬|褒|俗|罕)[〉\]］】)]\s*")
VALID_WORD = re.compile(r"^[A-Za-zÄÖÜäöüßẞ][A-Za-zÄÖÜäöüßẞ\-\s']*$")

# 词频排名 -> 等级
LEVELS = [(1500, "A1"), (4000, "A2"), (10000, "B1"), (MAX_RANK, "B2")]


def level_of(rank):
    for limit, lv in LEVELS:
        if rank <= limit:
            return lv
    return "B2"


def load_freq():
    ranks = {}
    with open(FREQ, encoding="utf-8") as fh:
        for i, line in enumerate(fh):
            parts = line.split()
            if parts:
                ranks[parts[0].lower()] = i + 1
    return ranks


def strip_prefix(text):
    """去掉释义开头的词性缩写与语法标记，如 "pron." "〈中〉" "［阳］"。"""
    s = (text or "").strip()
    for _ in range(4):
        nxt = PL_PREFIX.sub("", ANGLE_PREFIX.sub("", POS_PREFIX.sub("", s))).strip()
        if nxt == s:
            break
        s = nxt
    return s


def clean_gloss(text):
    """清理释义：去掉冗长的语法标注括号，规整标点。"""
    s = re.sub(r"\s+", "", text or "")
    if not s:
        return ""
    s = s.replace("（", "(").replace("）", ")")

    def drop_long(m):
        inner = m.group(1)
        # 括号内是较长的语法说明时直接删除，保留简短提示（如「非正式」）
        return "" if len(inner) > 6 else m.group(0)

    s = re.sub(r"\(([^()]*)\)", drop_long, s)
    s = re.sub(r"\[([^\[\]]*)\]", drop_long, s)
    s = s.replace("：", "，").strip("，、;； ")
    return s


def is_grammar_form(word, glosses):
    """识别「变格形式」这类伪词条：释义全篇在讲语法格变化。

    仅用于代词条目中明显的变格说明，避免误伤 ich / du 这类真实人称代词。
    """
    joined = "".join(glosses[:2])
    if "形式" in joined or "变格" in joined or "詞形" in joined:
        return True
    # 括号内列出多个「第X格」的是变格表，不是独立词义
    return joined.count("格") >= 3


def main():
    from opencc import OpenCC
    cc = OpenCC("t2s")
    ranks = load_freq()

    stats = Counter()
    out = []

    with gzip.open(GZ, "rt", encoding="utf-8") as fh:
        for line in fh:
            try:
                d = json.loads(line)
            except Exception:
                stats["bad_json"] += 1
                continue
            if d.get("lang_code") != "de":
                continue
            stats["de_total"] += 1

            word = (d.get("word") or "").strip()
            pos_raw = d.get("pos") or ""
            pos = POS_MAP.get(pos_raw)
            if not pos:
                stats["pos_skip"] += 1
                continue
            if not VALID_WORD.match(word) or len(word) > 40:
                stats["word_invalid"] += 1
                continue

            rank = ranks.get(word.lower())
            if not rank or rank > MAX_RANK:
                stats["rank_skip"] += 1
                continue

            # 收集有效释义，丢弃变形义项
            glosses = []
            for sense in d.get("senses", []):
                tags = sense.get("tags") or []
                if "form-of" in tags or "alt-of" in tags:
                    stats["form_of"] += 1
                    continue
                if "archaic" in tags or "obsolete" in tags:
                    stats["archaic"] += 1
                    continue
                for g in sense.get("glosses", []) or []:
                    if CJK.search(g) and not FORM_OF.search(g):
                        cleaned = strip_prefix(g)
                        if cleaned and CJK.search(cleaned):
                            glosses.append(cleaned)
            if not glosses:
                stats["no_gloss"] += 1
                continue
            if is_grammar_form(word, glosses):
                stats["grammar_form"] += 1
                continue

            zh = "；".join(dict.fromkeys(glosses[:3]))
            zh = cc.convert(clean_gloss(zh))
            if len(zh) > 80:
                zh = zh[:79] + "…"
            if not zh:
                stats["empty_zh"] += 1
                continue

            # 复数形式
            plural = None
            for f in d.get("forms", []):
                if "plural" in (f.get("tags") or []):
                    cand = (f.get("form") or "").strip()
                    if cand and cand != word:
                        plural = cand
                        break

            out.append({
                "w": word,
                "p": pos,
                "pl": plural,
                "zh": zh,
                "rank": rank,
                "lv": level_of(rank),
            })
            stats["kept"] += 1

    with open(OUT, "w", encoding="utf-8") as fh:
        for item in out:
            fh.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"提取完成，写入 {len(out)} 条 -> {os.path.basename(OUT)}")
    print("过滤统计:", dict(stats))
    lv = Counter(x["lv"] for x in out)
    print("等级分布:", dict(lv))
    ps = Counter(x["p"] for x in out)
    print("词性分布:", dict(ps))


if __name__ == "__main__":
    main()
