"""
mysns-ocr: EasyOCR HTTP sidecar (PyTorch 기반, ARM64/AMD64 모두 호환).

Single endpoint: POST /ocr (multipart, field=file).
Returns:
  {
    "lines": [{"text": "...", "confidence": 0.97}, ...],
    "rawText": "joined\nlines",
    "confidence": 0.92
  }
"""
import logging
import os
from typing import Any

import cv2
import easyocr
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("mysns-ocr")

app = FastAPI(title="mysns-ocr", version="1.0")

LANGS_ENV = os.getenv("OCR_LANGS", "ko,en")
LANGS = [s.strip() for s in LANGS_ENV.split(",") if s.strip()]
log.info("loading EasyOCR langs=%s ...", LANGS)
READER = easyocr.Reader(LANGS, gpu=False, verbose=False)
log.info("EasyOCR ready")


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "ok", "langs": LANGS}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)) -> dict[str, Any]:
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty file")

    arr = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise HTTPException(status_code=400, detail="unable to decode image")

    detections = READER.readtext(img, detail=1, paragraph=False)
    lines: list[dict[str, Any]] = [
        {"text": d[1], "confidence": float(d[2])} for d in detections
    ]
    raw_text = "\n".join(line["text"] for line in lines)
    avg_conf = (sum(line["confidence"] for line in lines) / len(lines)) if lines else 0.0
    return {"lines": lines, "rawText": raw_text, "confidence": avg_conf}
