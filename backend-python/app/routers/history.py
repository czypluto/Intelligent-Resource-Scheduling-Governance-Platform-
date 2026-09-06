"""对话档案接口：列表 / 详情 / 开新对话（清记忆）。"""
from fastapi import APIRouter

from ..agent import history as hist
from ..middleware import current_user

router = APIRouter(prefix="/api/chat/history", tags=["对话档案"])


@router.get("")
def list_history():
    return {"sessions": hist.list_sessions(current_user().user_id)}


@router.get("/{session}")
def detail(session: str):
    return {"messages": hist.get_session(current_user().user_id, session)}


@router.post("/reset")
def reset():
    """开新对话：新会话 + 清当前对话记忆。"""
    s = hist.new_session(current_user().user_id)
    return {"session": s}
