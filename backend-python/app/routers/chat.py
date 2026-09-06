"""对话入口：POST /api/chat，以 SSE 流式返回编排事件。"""
import json
import logging

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ..agent.orchestrator import AgentService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["智能助手"])

_agent = AgentService()


class ChatBody(BaseModel):
    message: str = Field(min_length=1, description="用户自然语言请求")


async def _stream(body: ChatBody):
    """逐步推送：思考中 -> 检索/校验 -> 执行 -> 结果。每条事件带当前 model。"""
    try:
        async for ev in _agent.handle(body.message):
            yield f"data: {json.dumps(ev, ensure_ascii=False)}\n\n"
    except PermissionError as e:
        ev = {"kind": "error", "text": str(e), "model": ""}
        yield f"data: {json.dumps(ev, ensure_ascii=False)}\n\n"
    except Exception as e:  # noqa: BLE001
        logger.exception("编排异常")
        ev = {"kind": "error", "text": f"处理出错：{e}", "model": ""}
        yield f"data: {json.dumps(ev, ensure_ascii=False)}\n\n"
    finally:
        yield "data: [DONE]\n\n"


@router.post("/chat", summary="对话", description="自然语言 -> 意图识别 -> 调 Java 查票/购票/订单，SSE 流式返回阶段事件")
async def chat(body: ChatBody):
    return StreamingResponse(
        _stream(body),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
