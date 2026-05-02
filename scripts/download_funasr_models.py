import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "models" / "funasr"
FFMPEG_DIR = ROOT / ".tools" / "ffmpeg"

MODEL_DIR.mkdir(parents=True, exist_ok=True)
os.environ["MODELSCOPE_CACHE"] = str(MODEL_DIR)
os.environ["PATH"] = str(FFMPEG_DIR) + os.pathsep + os.environ.get("PATH", "")

print(f"MODELSCOPE_CACHE={MODEL_DIR}", flush=True)
print("Loading FunASR AutoModel: paraformer-zh + fsmn-vad + ct-punc", flush=True)

from funasr import AutoModel

model = AutoModel(
    model="paraformer-zh",
    vad_model="fsmn-vad",
    punc_model="ct-punc",
    device="cpu",
    model_revision="master",
    vad_model_revision="master",
    punc_model_revision="master",
)

print("FunASR models loaded successfully.", flush=True)
print(f"Cache files under: {MODEL_DIR}", flush=True)

