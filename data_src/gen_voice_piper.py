# -*- coding: utf-8 -*-
"""批量生成德语单词离线发音（Piper 本地神经语音，多进程并行）。

相比原先基于微软 Edge 语音的方案，这里使用可自由再分发的本地模型：

- 引擎：Piper（MIT）https://github.com/rhasspy/piper
- 模型：de_DE-thorsten-medium（数据集 Thorsten-Voice，CC0 公有领域）
- 输出：单声道 22050Hz 32kbps mp3，与旧方案格式一致，可直接替换

生成结果可随 APK 自由分发，不再依赖任何在线语音服务的授权。

用法：
    python data_src/gen_voice_piper.py                    # 全量生成到 assets/voice_piper
    python data_src/gen_voice_piper.py --workers 6        # 指定并行进程数
    python data_src/gen_voice_piper.py --limit 20         # 先试生成 20 条
    python data_src/gen_voice_piper.py --levels A1 A2     # 只生成指定等级
    python data_src/gen_voice_piper.py --overwrite        # 已存在也重新生成
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
import wave
from multiprocessing import Pool

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
MODEL_DIR = os.path.join(ROOT, "data_src", "models")
DEFAULT_MODEL = os.path.join(MODEL_DIR, "de_DE-thorsten-medium.onnx")

# 生成到独立目录，校验通过后再替换 voice/，避免新旧音频混在一起
OUT = os.path.join(ASSETS, "voice_piper")

BITRATE = "32k"
SAMPLE_RATE = 22050
# 略慢于常速，便于跟读（对应 piper 的 length_scale，>1 更慢）
LENGTH_SCALE = 1.08

_voice = None
_cfg = None


def norm(name: str) -> str:
    """把德语词转换为安全文件名：变音转写，去除非法字符。须与旧脚本保持一致。"""
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
            lemma = re.sub(r"^(der|die|das)\s+", "", lemma)
            key = norm(lemma)
            if key and key not in words:
                words[key] = lemma
    return words


def _init_worker(model_path: str):
    """每个子进程各自加载一份模型；限制 BLAS 线程，避免多进程互相抢占。"""
    global _voice, _cfg
    for var in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
        os.environ.setdefault(var, "1")
    from piper import PiperVoice
    from piper.config import SynthesisConfig

    _voice = PiperVoice.load(model_path)
    _cfg = SynthesisConfig(length_scale=LENGTH_SCALE)


def _synth(item):
    """生成单条音频，返回 (key, 错误信息或 None)。"""
    key, text = item
    dst = os.path.join(OUT, key + ".mp3")
    wav = None
    try:
        fd, wav = tempfile.mkstemp(suffix=".wav", prefix=key + "_")
        os.close(fd)
        with wave.open(wav, "wb") as wf:
            _voice.synthesize_wav(text, wf, syn_config=_cfg)
        if os.path.getsize(wav) < 2000:
            raise RuntimeError("wav too small")
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
             "-ac", "1", "-ar", str(SAMPLE_RATE), "-b:a", BITRATE, dst],
            check=True,
        )
        return key, None
    except Exception as exc:  # noqa: BLE001
        return key, str(exc)[:80]
    finally:
        if wav and os.path.exists(wav):
            os.remove(wav)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=DEFAULT_MODEL, help="piper onnx 模型路径")
    ap.add_argument("--levels", nargs="*", default=None, help="只生成指定等级，如 A1 A2")
    ap.add_argument("--limit", type=int, default=0, help="只生成前 N 条，用于试跑")
    ap.add_argument("--workers", type=int, default=6, help="并行进程数")
    ap.add_argument("--overwrite", action="store_true", help="已存在也重新生成")
    args = ap.parse_args()

    if not os.path.exists(args.model):
        print(f"缺少模型文件：{args.model}", file=sys.stderr)
        print("可从 https://huggingface.co/rhasspy/piper-voices 下载 "
              "de/de_DE/thorsten/medium/de_DE-thorsten-medium.onnx(.json)", file=sys.stderr)
        sys.exit(2)

    try:
        import piper  # noqa: F401
    except ImportError:
        print("请先安装：pip install piper-tts", file=sys.stderr)
        sys.exit(2)

    words = collect_words(args.levels)
    if args.limit:
        words = dict(list(words.items())[: args.limit])

    os.makedirs(OUT, exist_ok=True)

    pending = []
    skip = 0
    for key, text in words.items():
        dst = os.path.join(OUT, key + ".mp3")
        if not args.overwrite and os.path.exists(dst) and os.path.getsize(dst) > 500:
            skip += 1
            continue
        pending.append((key, text))

    total = len(pending)
    print(f"待生成 {total} 条（跳过 {skip} 条），并行 {args.workers} 进程 → {OUT}", flush=True)
    if not total:
        print("没有需要生成的条目。")
        return

    ok = fail = 0
    failed = []
    with Pool(processes=args.workers, initializer=_init_worker, initargs=(args.model,)) as pool:
        for idx, (key, err) in enumerate(pool.imap_unordered(_synth, pending, chunksize=8), 1):
            if err:
                fail += 1
                failed.append((key, err))
            else:
                ok += 1
            if idx % 500 == 0 or idx == total:
                print(f"  进度 {idx}/{total}  成功 {ok}  失败 {fail}", flush=True)

    if fail:
        print("\n失败清单（前 20 条）：")
        for k, e in failed[:20]:
            print(f"  {k}: {e}")
        print(f"共 {fail} 条失败，未做目录替换。", file=sys.stderr)
        sys.exit(1)

    print(f"完成：成功 {ok}，跳过 {skip}，失败 {fail}")
    print(f"产物目录：{OUT}")
    print("确认无误后执行替换：\n"
          f"  rm -rf {os.path.join(ASSETS, 'voice')}\n"
          f"  mv {OUT} {os.path.join(ASSETS, 'voice')}")


if __name__ == "__main__":
    main()
