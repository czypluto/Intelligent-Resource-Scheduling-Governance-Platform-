"""本地嵌入服务：提供 OpenAI 兼容的 /v1/embeddings（文本），并支持图片多模态。

WeMM-Embedding-2B（官方路径）：AutoProcessor + AutoModel(WeMMEmbedding).embedding()
  文本/图片都先过对话模板 + qwen_vl_utils 预处理，取 <embedding> 位置 hidden，L2 归一化，MRL 截 EMBED_OUTPUT_DIM。
bge-m3（纯文本 CPU）：transformers mean-pool。
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
_model = None   # bge: transformers model
_proc = None    # wemm: AutoProcessor
_auto = None    # wemm: AutoModel (WeMMEmbedding, int8 GPU)


class EmbedRequest(BaseModel):
    model: Optional[str] = None
    input: Optional[Union[str, list[str]]] = None
    images: Optional[list[str]] = Field(default=None, description="本地图片路径列表（WSL 可达）")


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _tokenizer, _model, _proc, _auto
    if USE_WEMM:
        import torch  # noqa: F401
        from transformers import AutoModel, AutoProcessor, BitsAndBytesConfig

        logger.info("加载 WeMM-2B（int8 GPU）：%s", MODEL_DIR)
        quant = BitsAndBytesConfig(load_in_8bit=True)
        _proc = AutoProcessor.from_pretrained(MODEL_DIR, trust_remote_code=True)
        _auto = AutoModel.from_pretrained(
            MODEL_DIR, trust_remote_code=True,
            quantization_config=quant, device_map="cuda:0", low_cpu_mem_usage=True)
        _auto.eval()
        logger.info("WeMM-2B 就绪（int8 GPU）")
    else:
        from transformers import AutoModel, AutoTokenizer

        logger.info("加载 bge-m3（CPU）：%s", MODEL_DIR)
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
    vec = ((last_hidden * mask).sum(1) / mask.sum(1).clamp(min=1e-9))
    vec = torch.nn.functional.normalize(vec, p=2, dim=1)
    return vec.cpu().tolist()


def _wemm_encode(item_text: Optional[str], item_image: Optional[str]) -> list[float]:
    """官方路径：对话模板 + process_vision_info -> processor -> model.embedding -> MRL 截取。"""
    import torch
    import torch.nn.functional as F
    from qwen_vl_utils import process_vision_info

    content = []
    if item_image:
        content.append({"type": "image", "image": item_image})
    if item_text:
        content.append({"type": "text", "text": item_text})
    elif not item_image:
        content.append({"type": "text", "text": ""})
    messages = [{"role": "user", "content": content}]

    prompt = _proc.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)
    images, videos, video_kwargs = process_vision_info(
        messages, image_patch_size=16,
        return_video_kwargs=True, return_video_metadata=True)
    inputs = _proc(text=prompt, images=images, videos=videos,
                   video_metadata=None, return_tensors="pt", **video_kwargs)
    inputs = {k: v.to(_auto.device) if hasattr(v, "to") else v for k, v in inputs.items()}
    with torch.inference_mode():
        emb = _auto.embedding(**inputs).float()
    emb = F.normalize(emb[..., :OUTPUT_DIM], dim=-1)
    return emb[0].cpu().tolist()


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_NAME, "dir": MODEL_DIR, "dim": OUTPUT_DIM}


@app.post("/v1/embeddings")
def embeddings(req: EmbedRequest):
    out = []
    if req.images:
        for p in req.images:
            out.append(_wemm_encode(None, p))
        return {"object": "list", "data": [{"index": i, "embedding": v} for i, v in enumerate(out)],
                "model": req.model or MODEL_NAME}
    texts = [req.input] if isinstance(req.input, str) else (req.input or [])
    if USE_WEMM:
        for t in texts:
            out.append(_wemm_encode(t, None))
    else:
        out = _bge_embed(texts)
    return {"object": "list", "data": [{"index": i, "embedding": v} for i, v in enumerate(out)],
            "model": req.model or MODEL_NAME}
