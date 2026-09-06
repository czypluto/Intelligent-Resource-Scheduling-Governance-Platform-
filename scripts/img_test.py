"""图片多模态入库+检索验证。用法（WSL）：python3 scripts/img_test.py"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend-python"))

from app.rag.store import RagStore  # noqa: E402

PNG = Path("/home/cpluto/models/test_img.png")
CAP = Path("/home/cpluto/models/test_img.txt")


async def main():
    if not CAP.exists():
        CAP.write_text("红色背景示意图：代表列车高级别优先区域", encoding="utf-8")
    store = RagStore()
    print("rag.enabled", store.enabled)
    n = await store.ingest_images([PNG])
    print("ingested images:", n, " total:", store.count())
    hits = await store.retrieve("红色的高级别区域示意图", "技术部", top_k=3)
    print("hits", len(hits))
    for h in hits:
        print("-", h[:150])


if __name__ == "__main__":
    asyncio.run(main())
