"""把 PDF 灌入向量库（可选只灌前 N 页）。
用法（WSL）：python3 scripts/ingest_pdf_cli.py "/mnt/c/.../JavaEE.pdf" [N页]
"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend-python"))

from app.rag import documents  # noqa: E402
from app.rag.store import RagStore  # noqa: E402


async def main():
    if len(sys.argv) < 2:
        print("用法：ingest_pdf_cli.py <pdf路径> [最大页数]")
        return
    path = Path(sys.argv[1])
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    store = RagStore()
    if not store.enabled:
        print("RAG 不可用")
        return
    pages = documents.load_pages(path)[:limit]
    print(f"解析页数：{len(pages)}（前 {limit} 页）")
    rows = 0
    for page in pages:
        from app.rag.documents import chunk_sentences  # noqa: PLC0415
        smalls = chunk_sentences(page.text)
        if not smalls:
            continue
        import httpx  # noqa: PLC0415
        from app.rag import store as st  # noqa: PLC0415
        vecs = await st.embed_texts(smalls)
        data = [{
            "parent_id": page.page_no,
            "small_text": s,
            "parent_text": st._clean(page.text),
            "dept_tags": "all",
            "source": path.name,
            "page_no": page.page_no,
            "heading": (page.heading or "")[:500],
            "vector": v,
        } for s, v in zip(smalls, vecs)]
        store._client.insert(collection_name=store._collection, data=data)
        rows += len(smalls)
        if (page.page_no % 10) == 0:
            print(f"page {page.page_no} done, chunks {rows}, total {store.count()}", flush=True)
    print(f"DONE pages={len(pages)} chunks={rows} total={store.count()}")


if __name__ == "__main__":
    asyncio.run(main())
