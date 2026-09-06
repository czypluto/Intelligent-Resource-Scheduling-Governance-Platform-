"""对话档案接口：列表 / 详情 / 新对话（清记忆） / 删除单会话。

返回统一 {code, msg, data}，与前端 jfetch 及 Java ApiResult 约定一致。
"""
from fastapi import APIRouter

from ..agent import history as hist
from ..middleware import current_user

router = APIRouter(prefix="/api/chat/history", tags=["对话档案"])


@router.get("")
def list_history():
    return {"code": 0, "msg": "ok", "data": {"sessions": hist.list_sessions(current_user().user_id)}}


@router.get("/{session}")
def detail(session: str):
    return {"code": 0, "msg": "ok", "data": {"messages": hist.get_session(current_user().user_id, session)}}


@router.post("/reset")
def reset():
    """开新对话：新会话 + 清当前对话记忆。"""
    s = hist.new_session(current_user().user_id)
    return {"code": 0, "msg": "ok", "data": {"session": s}}


@router.delete("/{session}")
def delete(session: str):
    hist.delete_session(current_user().user_id, session)
    return {"code": 0, "msg": "ok", "data": {"deleted": session}}
