"""PDF 图文双通道入库：每页「文字小块 + 整页渲染图向量」。

用法（WSL）：python3 scripts/ingest_pdf_pages.py "<pdf路径>" [页数，默认5]
"""
import asyncio
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend-python"))

from app.rag import documents  # noqa: E402
from app.rag import store as st  # noqa: E402
from app.rag.store import RagStore  # noqa: E402


async def main():
    if len(sys.argv) < 2:
        print("用法：ingest_pdf_pages.py <pdf路径> [页数]")
        return
    path = Path(sys.argv[1])
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    store = RagStore()
    if not store.enabled:
        print("RAG 不可用")
        return
    pages = documents.load_pages(path)
    total_text = 0
    total_img = 0
    with tempfile.TemporaryDirectory() as td:
        for idx, page in enumerate(pages[:limit]):
            # 文字通道
            smalls = documents.chunk_sentences(page.text)
            if smalls:
                vecs = await st.embed_texts(smalls)
                store._client.insert(collection_name=store._collection, data=[{
                    "parent_id": (idx + 1) * 1000,
                    "small_text": s,
                    "parent_text": st._clean(page.text),
                    "dept_tags": "all",
                    "source": path.name,
                    "page_no": idx + 1,
                    "heading": (page.heading or "")[:500],
                    "vector": v,
                } for s, v in zip(smalls, vecs)])
                total_text += len(smalls)
            # 图像通道：整页渲染 -> WeMM 视觉向量
            png = Path(td) / f"p{idx}.png"
            if documents.render_page(path, idx, str(png)):
                ivec = (await st.embed_texts_images([str(png)]))[0]
                caption = (page.heading or "")[:80]
                store._client.insert(collection_name=store._collection, data=[{
                    "parent_id": (idx + 1) * 1000 + 1,
                    "small_text": ("[页图] " + caption)[:500],
                    "parent_text": st._clean(f"{path.name} 第{idx + 1}页整页图像，含图表。标题：{caption}"),
                    "dept_tags": "all",
                    "source": path.name,
                    "page_no": idx + 1,
                    "heading": "[页图]" + (f" {caption}" if caption else ""),
                    "vector": ivec,
                }])
                total_img += 1
            if (idx + 1) % 2 == 0:
                print(f"page {idx + 1} 完成 text+{total_text} img+{total_img} total={store.count()}", flush=True)
    print(f"DONE pages={min(limit, len(pages))} text_chunks={total_text} page_images={total_img} total={store.count()}")


if __name__ == "__main__":
    asyncio.run(main())
