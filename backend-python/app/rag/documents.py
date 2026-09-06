"""多格式文档加载与切片：md / docx / pdf。

产出 Page(text, page_no, heading)：
- markdown：按 # / ## 标题分段；每段一个 Page
- word：按 Heading 样式分段（无标题则整体一段）
- pdf：按页提取，每页一个 Page（尽量识别标题行）

语义切片 chunk_sentences：按句切分后合并到目标长度，避免把长段落整段塞进小块。
"""
import logging
import re
from dataclasses import dataclass
from pathlib import Path

logger = logging.getLogger(__name__)

_SENT_SPLIT = re.compile(r"(?<=[。！？!?；;])\s*|\n+")


@dataclass
class Page:
    text: str
    page_no: int      # md/word 从 1 起；pdf 为该页号
    heading: str = ""


def _sentences(text: str) -> list[str]:
    parts = [p.strip() for p in _SENT_SPLIT.split(text) if p.strip()]
    return parts


def chunk_sentences(text: str, max_len: int = 200, min_len: int = 30) -> list[str]:
    """语义切片：按句切，再合并到接近 max_len 的小块。"""
    out: list[str] = []
    buf = ""
    for s in _sentences(text):
        if len(s) > max_len:  # 超长句硬切
            if buf:
                out.append(buf)
                buf = ""
            for i in range(0, len(s), max_len):
                out.append(s[i:i + max_len])
            continue
        if buf and len(buf) + len(s) > max_len:
            out.append(buf)
            buf = s
        else:
            buf = s if not buf else buf + s
    if buf and len(buf) >= min_len:
        out.append(buf)
    return out


def _load_markdown(path: Path) -> list[Page]:
    text = path.read_text(encoding="utf-8")
    pages: list[Page] = []
    cur_head = ""
    buf: list[str] = []
    for line in text.splitlines():
        m = re.match(r"^(#{1,3})\s+(.*)$", line.strip())
        if m:
            if buf:
                pages.append(Page("\n".join(buf).strip(), len(pages) + 1, cur_head))
                buf = []
            cur_head = m.group(2).strip()
        else:
            if line.strip():
                buf.append(line.strip())
    if buf:
        pages.append(Page("\n".join(buf).strip(), len(pages) + 1, cur_head))
    return [p for p in pages if p.text]


def _load_docx(path: Path) -> list[Page]:
    import docx  # python-docx

    doc = docx.Document(str(path))
    pages: list[Page] = []
    cur_head = ""
    buf: list[str] = []
    for para in doc.paragraphs:
        style = (para.style.name or "").lower()
        text = para.text.strip()
        if not text:
            continue
        if style.startswith("heading"):
            if buf:
                pages.append(Page("\n".join(buf).strip(), len(pages) + 1, cur_head))
                buf = []
            cur_head = text
        else:
            buf.append(text)
    if buf:
        pages.append(Page("\n".join(buf).strip(), len(pages) + 1, cur_head))
    return [p for p in pages if p.text]


def _load_pdf(path: Path) -> list[Page]:
    from pypdf import PdfReader

    reader = PdfReader(str(path))
    pages: list[Page] = []
    for i, page in enumerate(reader.pages, start=1):
        text = (page.extract_text() or "").strip()
        if not text:
            continue
        heading = ""
        lines = text.splitlines()
        if lines and 1 <= len(lines[0]) <= 40 and not lines[0].rstrip().endswith("。"):
            heading = lines[0].strip()
        pages.append(Page(text, i, heading))
    return pages


def render_page(path: Path, page_index: int, out_png: str, scale: float = 1.6) -> bool:
    """把 PDF 第 page_index(0 起) 页渲染成 PNG，返回是否成功（供页面图像入向量）。"""
    try:
        import pypdfium2 as pdfium

        pdf = pdfium.PdfDocument(str(path))
        page = pdf[page_index]
        bitmap = page.render(scale=scale)
        pil_img = bitmap.to_pil()
        pil_img.save(out_png)
        pdf.close()
        return True
    except Exception as e:  # noqa: BLE001
        logger.warning("页渲染失败 page=%s：%s", page_index + 1, e)
        return False


def load_pages(path: Path) -> list[Page]:
    suffix = path.suffix.lower()
    try:
        if suffix == ".md":
            return _load_markdown(path)
        if suffix in (".docx", ".doc"):
            return _load_docx(path)
        if suffix == ".pdf":
            return _load_pdf(path)
    except Exception as e:  # noqa: BLE001
        logger.warning("解析文档失败 %s：%s", path.name, e)
        return []
    logger.warning("不支持的文档类型：%s", path.name)
    return []
