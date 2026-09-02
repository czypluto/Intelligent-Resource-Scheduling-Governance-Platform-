"""本地嵌入服务：加载 bge-m3，提供 OpenAI 兼容的 /v1/embeddings。

用法（WSL，CPU/GPU 皆可）：
    EMBED_MODEL_DIR=/home/cpluto/models/bge-m3 python3 -m uvicorn app.embed.server:app --port 8001

说明：RAG 侧只认 EMBED_BASE 的 /v1/embeddings；换嵌入模型只需换本地目录，主流程代码不变。
"""
import logging
import os
from contextlib import asynccontextmanager
from typing import Optional, Union

import torch
from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoModel, AutoTokenizer

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

MODEL_DIR = os.getenv("EMBED_MODEL_DIR", "/home/cpluto/models/bge-m3")
MODEL_NAME = os.getenv("EMBED_MODEL", "bge-m3")
MAX_LEN = int(os.getenv("EMBEDDING_MAX_LEN", "2048"))

_tokenizer = None
_model = None


class EmbedRequest(BaseModel):
    model: Optional[str] = None
    input: Union[str, list[str]]


def _embed(texts: list[str]) -> list[list[float]]:
    enc = _tokenizer(texts, padding=True, truncation=True, max_length=MAX_LEN, return_tensors="pt")
    with torch.no_grad():
        last_hidden = _model(**enc).last_hidden_state
    mask = enc["attention_mask"].unsqueeze(-1).to(last_hidden.dtype)
    summed = (last_hidden * mask).sum(dim=1)
    counts = mask.sum(dim=1).clamp(min=1e-9)
    vectors = summed / counts
    vectors = torch.nn.functional.normalize(vectors, p=2, dim=1)
    return vectors.cpu().tolist()


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _tokenizer, _model
    logger.info("加载嵌入模型：%s", MODEL_DIR)
    _tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
    _model = AutoModel.from_pretrained(MODEL_DIR)
    _model.eval()
    logger.info("嵌入模型就绪（device=cpu）")
    yield


app = FastAPI(title="resv-embed", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_NAME, "dir": MODEL_DIR}


@app.post("/v1/embeddings")
async def embeddings(req: EmbedRequest):
    texts = [req.input] if isinstance(req.input, str) else req.input
    vectors = _embed(texts)
    data = [
        {"object": "embedding", "index": i, "embedding": v}
        for i, v in enumerate(vectors)
    ]
    return {"object": "list", "data": data, "model": req.model or MODEL_NAME}
