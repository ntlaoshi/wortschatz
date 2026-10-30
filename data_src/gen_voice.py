# -*- coding: utf-8 -*-
"""批量生成德语单词离线发音。

使用微软 Edge 神经网络语音（de-DE-KatjaNeural）合成，转码为低码率单声道 mp3，
作为 assets 随 APK 分发，安装后无需任何系统 TTS 组件即可发音。
"""
import argparse
import asyncio
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "voice")
TMP = os.path.join(ROOT, ".tmpvoice")

VOICE = "de-DE-KatjaNeural"   # 女声，吐字清晰；男声备选 de-DE-ConradNeural
RATE = "-8%"                  # 略慢于常速，便于跟读
BITRATE = "32k"               # mp3 码率，可用 --bitrate 覆盖


def norm(name: str) -> str:
    """把德语词转换为安全文件名：变音转写，去除非法字符。"""
    s = name.strip()
    s = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    s = s.replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"[^A-Za-z0-9_\-]+", "_", s).strip("_")
    return s.lower()


def collect_words(levels=None):
    """从词库 JSON 中收集需要发音的唯一词形。"""
    words = {}
    for path in sorted(glob.glob(os.path.join(ASSETS, "words_*.json"))):
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        for entry in data.get("words", []):
            if levels and entry.get("lv") not in levels:
                continue
            lemma = (entry.get("l") or entry.get("w") or "").strip()
            if not lemma:
                continue
            # 部分词条 l 字段可能为空或等于带冠词形式，回退清洗
            lemma = re.sub(r"^(der|die|das)\s+", "", lemma)
            key = norm(lemma)
            if key and key not in words:
                words[key] = lemma
    return words


async def synth_one(sem, idx, total, key, text, stats):
    import edge_tts
    raw = os.path.join(TMP, key + ".raw.mp3")
    dst = os.path.join(OUT, key + ".mp3")
    if os.path.exists(dst) and os.path.getsize(dst) > 500:
        stats["skip"] += 1
        return
    async with sem:
        for attempt in range(3):
            try:
                comm = edge_tts.Communicate(text, VOICE, rate=RATE)
                await comm.save(raw)
                if os.path.getsize(raw) < 300:
                    raise RuntimeError("audio too small")
                # 转码：单声道 22.05kHz 32kbps，体积约为原始的 1/3
                subprocess.run(
                    ["ffmpeg", "-y", "-loglevel", "error", "-i", raw,
                     "-ac", "1", "-ar", "22050", "-b:a", BITRATE, dst],
                    check=True,
                )
                # 中间文件留在 TMP 目录，由调用方在收尾时统一清理
                stats["ok"] += 1
                if stats["ok"] % 50 == 0:
                    print(f"  进度 {stats['ok']}/{total}", flush=True)
                return
            except Exception as exc:  # noqa: BLE001
                if attempt == 2:
                    stats["fail"] += 1
                    stats.setdefault("failed", []).append((key, str(exc)[:80]))
                else:
                    await asyncio.sleep(1.2 * (attempt + 1))


async def run(words, concurrency):
    os.makedirs(TMP, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    sem = asyncio.Semaphore(concurrency)
    stats = {"ok": 0, "skip": 0, "fail": 0}
    total = len(words)
    tasks = [
        synth_one(sem, i, total, k, v, stats)
        for i, (k, v) in enumerate(sorted(words.items()))
    ]
    await asyncio.gather(*tasks)
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--concurrency", type=int, default=8)
    ap.add_argument("--limit", type=int, default=0, help="仅生成前 N 个，用于试跑")
    ap.add_argument("--levels", default="", help="只生成指定等级，如 A1,A2,B1")
    ap.add_argument("--bitrate", default="32k", help="转码码率，默认 32k")
    args = ap.parse_args()

    global BITRATE
    BITRATE = args.bitrate
    levels = {x.strip().upper() for x in args.levels.split(",") if x.strip()} or None

    words = collect_words(levels)
    items = sorted(words.items())
    if args.limit:
        items = items[: args.limit]
    print(f"待生成 {len(items)} 个词，语音 {VOICE}，码率 {BITRATE}，并发 {args.concurrency}")

    stats = asyncio.run(run(dict(items), args.concurrency))

    files = glob.glob(os.path.join(OUT, "*.mp3"))
    size = sum(os.path.getsize(f) for f in files)
    print(f"\n完成：新增 {stats['ok']}，跳过 {stats['skip']}，失败 {stats['fail']}")
    print(f"音频文件 {len(files)} 个，合计 {size / 1024 / 1024:.2f} MB，平均 "
          f"{(size / max(len(files), 1)) / 1024:.1f} KB/个")
    if stats.get("failed"):
        for k, e in stats["failed"][:10]:
            print("  失败:", k, e)


if __name__ == "__main__":
    main()
