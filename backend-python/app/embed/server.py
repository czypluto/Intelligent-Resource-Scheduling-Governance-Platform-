"""本地嵌入服务：提供 OpenAI 兼容的 /v1/embeddings。

按 EMBED_MODEL 分派：
- WeMM-Embedding-2B（多模态，int8 GPU）：sentence-transformers 加载，`<embedding>` token + L2，MRL 截 1024
- bge-m3（纯文本，CPU）：transformers mean-pool（原逻辑）

用法（WSL）：
    WeMM: EMBED_MODEL=WeMM-Embedding-2B EMBED_MODEL_DIR=/home/cpluto/models/WeMM-Embedding-2B python3 -m uvicorn app.embed.server:app --port 8001
    bge : EMBED_MODEL=bge-m3 EMBED_MODEL_DIR=/home/cpluto/models/bge-m3 ...
"""
import logging
import os
from contextlib import asynccontextmanager
from typing import Optional, Union

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

MODEL_NAME = os.getenv("EMBED_MODEL", "bge-m3").lower()
MODEL_DIR = os.getenv("EMBED_MODEL_DIR", "/home/cpluto/models/bge-m3")
OUTPUT_DIM = int(os.getenv("EMBED_OUTPUT_DIM", "1024"))
USE_WEMM = "wemm" in MODEL_NAME

_tokenizer = None
_model = None      # bge: transformers model
_st_model = None   # wemm: sentence-transformers


class EmbedRequest(BaseModel):
    model: Optional[str] = None
    input: Union[str, list[str]] = Field(..., description="文本或文本列表")


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _tokenizer, _model, _st_model
    if USE_WEMM:
        from transformers import BitsAndBytesConfig
        from sentence_transformers import SentenceTransformer

        logger.info("加载 WeMM 嵌入模型（int8, GPU）：%s", MODEL_DIR)
        quant = BitsAndBytesConfig(load_in_8bit=True)
        _st_model = SentenceTransformer(
            MODEL_DIR, device="cuda", trust_remote_code=True,
            model_kwargs={"quantization_config": quant})
        logger.info("WeMM 就绪（int8, GPU）")
    else:
        from transformers import AutoModel, AutoTokenizer

        logger.info("加载 bge-m3 嵌入模型（CPU）：%s", MODEL_DIR)
        _tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
        _model = AutoModel.from_pretrained(MODEL_DIR)
        _model.eval()
        logger.info("bge-m3 就绪（CPU）")
    yield


app = FastAPI(title="resv-embed", lifespan=lifespan)


def _bge_embed(texts: list[str]) -> list[list[float]]:
    import torch

    enc = _tokenizer(texts, padding=True, truncation=True, max_length=2048, return_tensors="pt")
    with torch.no_grad():
        last_hidden = _model(**enc).last_hidden_state
    mask = enc["attention_mask"].unsqueeze(-1).to(last_hidden.dtype)
    summed = (last_hidden * mask).sum(dim=1)
    counts = mask.sum(dim=1).clamp(min=1e-9)
    vec = summed / counts
    vec = torch.nn.functional.normalize(vec, p=2, dim=1)
    return vec.cpu().tolist()


def _wemm_embed(texts: list[str]) -> list[list[float]]:
    vec = _st_model.encode(texts, normalize_embeddings=True, truncate_dim=OUTPUT_DIM)
    arr = np.asarray(vec)
    return arr.tolist()


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_NAME, "dir": MODEL_DIR, "dim": OUTPUT_DIM}


# 同步 handler：FastAPI 在线程池运行，避免大模型推理阻塞事件循环
@app.post("/v1/embeddings")
def embeddings(req: EmbedRequest):
    texts = [req.input] if isinstance(req.input, str) else req.input
    vectors = _wemm_embed(texts) if USE_WEMM else _bge_embed(texts)
    data = [{"object": "embedding", "index": i, "embedding": v} for i, v in enumerate(vectors)]
    return {"object": "list", "data": data, "model": req.model or MODEL_NAME}
