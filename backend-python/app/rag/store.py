"""RAG 存储与检索（Small-to-Big）。

骨架实现说明：
- 依赖 pymilvus（Milvus Lite）与本地 vLLM 的 /v1/embeddings（Qwen3-Embedding-0.5B）。
- 依赖未装或模型不可达时，rag_available() 返回 False，Agent 自动走降级，不影响主流程。
- 数据文件在 backend-python/data/。向量维度写死 1024（Qwen3-Embedding-0.5B），如模型更换需重建集合。
"""
import logging
import re
from pathlib import Path
from typing import Optional

import httpx

from .. import config

logger = logging.getLogger(__name__)

_available: Optional[bool] = None
_DIM = 1024


def rag_available() -> bool:
    global _available
    if _available is None:
        try:
            import pymilvus  # noqa: F401
            _available = True
        except ImportError:
            _available = False
    return _available


class EmbedError(Exception):
    pass


async def embed_texts(texts: list[str]) -> list[list[float]]:
    """调本地 vLLM 的 OpenAI 兼容 embeddings 接口。"""
    url = f"{config.EMBED_BASE}/v1/embeddings"
    payload = {"model": config.EMBED_MODEL, "input": texts[:64]}
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(url, json=payload)
        resp.raise_for_status()
        data = resp.json()
    return [item["embedding"] for item in data["data"]]


def _split_paragraphs(text: str) -> list[str]:
    parts = [p.strip() for p in re.split(r"\n{1,}", text) if p.strip()]
    return parts


def _small_of(paragraph: str) -> str:
    return paragraph if len(paragraph) <= 256 else paragraph[:256]


def _prepare(text: str) -> str:
    return text.replace("\\", "\\\\").replace('"', '\\"')


class RagStore:
    """Milvus Lite 集合：记录含小块(small_text)与父块(parent_text)，检索按部门标签过滤。"""

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
            self._collection = name
            return
        schema = self._client.create_schema(auto_id=True, enable_dynamic_field=False)
        schema.add_field("id", DataType.INT64, is_primary=True)
        schema.add_field("parent_id", DataType.INT64)
        schema.add_field("small_text", DataType.VARCHAR, max_length=512)
        schema.add_field("parent_text", DataType.VARCHAR, max_length=20000)
        schema.add_field("dept_tags", DataType.VARCHAR, max_length=256, default_value="all")
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=_DIM)
        index_params = self._client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
        self._client.create_collection(name, schema=schema, index_params=index_params)
        self._client.load_collection(name)
        self._collection = name

    async def ingest_dir(self) -> int:
        """把 data/docs/*.md 灌入向量库。仅管理用，不随服务启动。"""
        if not self.enabled:
            return 0
        docs_dir = config.DOCS_DIR
        if not docs_dir.is_dir():
            return 0
        count = 0
        for path in sorted(docs_dir.glob("*.md")):
            count += await self.ingest_markdown(path)
        return count

    async def ingest_markdown(self, path: Path) -> int:
        text = path.read_text(encoding="utf-8")
        paragraphs = _split_paragraphs(text)
        rows, pid = [], 0
        for para in paragraphs:
            pid += 1
            small = _small_of(para)
            if not small:
                continue
            vec = (await embed_texts([small]))[0]
            rows.append(
                {
                    "parent_id": pid,
                    "small_text": small,
                    "parent_text": _prepare(para),
                    "dept_tags": "all",
                    "vector": vec,
                }
            )
        if rows:
            self._client.insert(collection_name=self._collection, data=rows)
        return len(rows)

    async def retrieve(self, query: str, department: str, top_k: int = 5) -> list[str]:
        """检索子块 -> 按 parent_id 去重 -> 返回父块全文。越权文档在标量过滤层就不出现。"""
        if not self.enabled:
            return []
        try:
            qv = (await embed_texts([query]))[0]
            expr = 'dept_tags in ["all", "%s"]' % _prepare(department)
            res = self._client.search(
                collection_name=self._collection,
                data=[qv],
                limit=top_k * 3,
                output_fields=["parent_id", "parent_text", "dept_tags"],
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
            out.append(entity.get("parent_text", ""))
            if len(out) >= top_k:
                break
        return out
