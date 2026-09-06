"""RAG 存储与检索（Small-to-Big，带来源元数据）。

- 格式：md / docx / pdf，按 页+语义切片 入库（见 documents.py）
- 每条记录保留 source / page_no / heading，检索可给出处引用
- 嵌入经本地 /v1/embeddings（WeMM-Embedding-2B int8 GPU 或 bge-m3 CPU），向量 1024 维
- 注意：本文件 schema 若升级，需删除 data/rag.db 目录后重灌
"""
import logging
from pathlib import Path
from typing import Optional

import httpx

from .. import config
from .documents import chunk_sentences, load_pages

logger = logging.getLogger(__name__)

_available: Optional[bool] = None
_DIM = 1024
_PARENT_MAX = 20000


def rag_available() -> bool:
    global _available
    if _available is None:
        try:
            import pymilvus  # noqa: F401
            _available = True
        except ImportError:
            _available = False
    return _available


async def embed_texts(texts: list[str]) -> list[list[float]]:
    url = f"{config.EMBED_BASE}/v1/embeddings"
    payload = {"model": config.EMBED_MODEL, "input": texts[:64]}
    async with httpx.AsyncClient(timeout=180) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return [item["embedding"] for item in data["data"]]


def _clean(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')[: _PARENT_MAX]


class RagStore:
    def __init__(self) -> None:
        self.enabled = rag_available()
        self._client = None
        self._collection = None
        if not self.enabled:
            logger.warning("未安装 pymilvus，RAG 关闭")
            return
        try:
            from pymilvus import MilvusClient

            config.RAG_DB.parent.mkdir(parents=True, exist_ok=True)
            self._client = MilvusClient(str(config.RAG_DB))
            self._ensure_collection()
        except Exception as e:  # noqa: BLE001
            logger.warning("RAG 初始化失败，已关闭：%s", e)
            self.enabled = False

    def _ensure_collection(self) -> None:
        from pymilvus import DataType

        name = "small"
        if self._client.has_collection(name):
            self._client.load_collection(name)
            self._collection = name
            return
        schema = self._client.create_schema(auto_id=True, enable_dynamic_field=False)
        schema.add_field("id", DataType.INT64, is_primary=True)
        schema.add_field("parent_id", DataType.INT64)
        schema.add_field("small_text", DataType.VARCHAR, max_length=512)
        schema.add_field("parent_text", DataType.VARCHAR, max_length=_PARENT_MAX)
        schema.add_field("dept_tags", DataType.VARCHAR, max_length=256, default_value="all")
        schema.add_field("source", DataType.VARCHAR, max_length=256)
        schema.add_field("page_no", DataType.INT64)
        schema.add_field("heading", DataType.VARCHAR, max_length=512)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=_DIM)
        index_params = self._client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
        self._client.create_collection(name, schema=schema, index_params=index_params)
        self._client.load_collection(name)
        self._collection = name

    async def ingest_dir(self) -> int:
        if not self.enabled:
            return 0
        if not config.DOCS_DIR.is_dir():
            return 0
        count = 0
        for path in sorted(config.DOCS_DIR.iterdir()):
            if path.suffix.lower() in (".md", ".docx", ".doc", ".pdf"):
                count += await self.ingest_file(path)
        return count

    async def ingest_file(self, path: Path) -> int:
        pages = load_pages(path)
        if not pages:
            return 0
        rows, pid = [], 0
        for page in pages:
            pid += 1
            smalls = chunk_sentences(page.text)
            if not smalls:
                continue
            vecs = await embed_texts(smalls)
            parent = _clean(page.text)
            for small, vec in zip(smalls, vecs):
                rows.append({
                    "parent_id": pid,
                    "small_text": small,
                    "parent_text": parent,
                    "dept_tags": "all",
                    "source": path.name,
                    "page_no": page.page_no,
                    "heading": (page.heading or "")[:500],
                    "vector": vec,
                })
        if rows:
            self._client.insert(collection_name=self._collection, data=rows)
        return len(rows)

    async def retrieve(self, query: str, department: str, top_k: int = 5) -> list[str]:
        """检索小块 -> 按 (source,page) 去重 -> 返回父块文本，并带出处前缀供引用。"""
        if not self.enabled:
            return []
        try:
            qv = (await embed_texts([query]))[0]
            expr = 'dept_tags in ["all", "%s"]' % _clean(department)
            res = self._client.search(
                collection_name=self._collection,
                data=[qv],
                limit=top_k * 4,
                output_fields=["parent_id", "parent_text", "source", "page_no", "heading"],
                filter=expr,
                search_params={"metric_type": "COSINE"},
            )
        except Exception as e:  # noqa: BLE001
            logger.warning("检索失败，走降级：%s", e)
            return []

        seen: set[int] = set()
        out: list[str] = []
        for hit in res[0]:
            entity = hit.get("entity") or {}
            pid = entity.get("parent_id")
            if pid in seen:
                continue
            seen.add(pid)
            source = entity.get("source") or ""
            pno = entity.get("page_no")
            head = entity.get("heading") or ""
            cite = source
            if pno:
                cite += f" 第{pno}页"
            if head:
                cite += f" · {head}"
            text = (entity.get("parent_text") or "").strip()
            if text:
                out.append(f"[来源 {cite}]\n{text}")
            if len(out) >= top_k:
                break
        return out
