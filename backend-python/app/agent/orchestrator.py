"""对话编排。逐步产出事件（kind/text/model），由路由层转成 SSE。

流程（与 docs/技术方案-v2.md §7.2 一致）：
  意图识别 -> 查资源 -> 权限确定性校验(Java) -> 抢票(Java) -> 结果
权限结论一律由 Java 规则表给出，模型只做自然语言解释。
"""
import logging
import uuid
from typing import AsyncIterator, Optional

from .. import config, java_client, llm, tools
from ..llm import LlmError
from ..middleware import current_user
from ..rag.store import RagStore, rag_available

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "你是集团内部资源预约助手。只做预约、查询与规则解释，不编造权限结论。"
    "预约用 book_resource（参数 resourceId 必须来自给出的资源列表 id）；"
    "不确定资源是否可约时先 check_permission。回答简洁、公文化。"
)


def _event(kind: str, text: str, task: str = "agent") -> dict:
    return {"kind": kind, "text": text, "model": config.resolve_model(task)}


def _denied_text(reason: str) -> str:
    """Java 的原因自带句号，避免拼接后出现双句号。"""
    reason = (reason or "权限不足").strip()
    reason = reason.rstrip("。").rstrip(".")
    return f"无法为您预约：{reason}。"


class AgentService:
    def __init__(self) -> None:
        self._rag: Optional[RagStore] = None

    def _rag_store(self) -> Optional[RagStore]:
        if self._rag is None:
            self._rag = RagStore() if rag_available() else None
        return self._rag

    async def handle(self, user_text: str) -> AsyncIterator[dict]:
        user = current_user()
        yield _event("think", f"已收到您的请求，正在为您办理（{user.identity}）。")

        try:
            resources = await java_client.list_resources()
        except java_client.JavaError as e:
            yield _event("error", f"资源服务不可用：{e}")
            return
        except Exception as e:  # noqa: BLE001
            logger.exception("list_resources 失败")
            yield _event("error", f"资源服务连接失败：{e}")
            return

        if not resources:
            yield _event("error", "当前没有可预约资源。")
            return

        # 意图识别 + 工具决策（模型路由，dev=pro / 上线后 agent=flash）
        resource_lines = "\n".join(
            f"- id={r['id']} {r['name']}（类型 {r.get('type')}，余量 {r.get('totalStock')}）"
            for r in resources
        )
        try:
            out = await llm.chat(
                task="agent",
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": f"当前可预约资源：\n{resource_lines}\n\n用户请求：{user_text}"},
                ],
                tools=tools.all_schemas(),
            )
        except LlmError as e:
            yield _event("error", str(e))
            return

        # 优先执行下单（_book 自带权限二次校验）；只回权限查询则查询后顺延。
        book_tc = next((t for t in out.tool_calls if t.name == "book_resource"), None)
        check_tc = next((t for t in out.tool_calls if t.name == "check_permission"), None)
        if book_tc is not None:
            async for ev in self._book(resources, book_tc.arg_dict()):
                yield ev
            return
        if check_tc is not None:
            async for ev in self._permission_first(resources, check_tc.arg_dict()):
                yield ev
            return

        # 无工具调用：问答收尾（有 RAG 则带上下文，否则直接答）
        rag = self._rag_store()
        context = await rag.retrieve(user_text, department=user.department) if rag and rag.enabled else []
        try:
            if context:
                answer = await llm.chat(
                    task="gen",
                    messages=[
                        {"role": "system", "content": "严格依据资料回答：资料里有的数字/条件直接给出，不要模糊成“以系统为准”；资料里没有的明说未查到。"},
                        {"role": "user", "content": f"资料：\n{chr(10).join(context)}\n\n问题：{user_text}"},
                    ],
                )
            else:
                answer = await llm.chat(
                    task="gen",
                    messages=[
                        {"role": "system", "content": "你是集团资源助手，简述如何预约即可，不要编造细则。"},
                        {"role": "user", "content": user_text},
                    ],
                )
        except LlmError as e:
            yield _event("error", str(e))
            return
        yield _event("answer", answer.content, task="gen")

    async def _permission_first(self, resources, args) -> AsyncIterator[dict]:
        rtype = args.get("resourceType")
        if not rtype:
            yield _event("answer", "请说明要预约哪类资源。")
            return
        yield _event("check", "正在校验您的预约权限…")
        try:
            decision = await java_client.check_permission(rtype)
        except java_client.JavaError as e:
            yield _event("error", f"权限校验失败：{e}")
            return
        if not decision.get("allowed"):
            yield _event("denied", _denied_text(decision.get("reason")))
            return
        same_type = [r for r in resources if r.get("type") == rtype]
        if len(same_type) == 1:
            async for ev in self._book(resources, {"resourceId": same_type[0]["id"]}):
                yield ev
        else:
            names = "、".join(r["name"] for r in same_type) if same_type else "（无）"
            yield _event("answer", f"您有该类资源权限。当前该类资源有：{names}。请告知预约哪一项。")

    async def _book(self, resources, args) -> AsyncIterator[dict]:
        resource_id = args.get("resourceId")
        if not resource_id:
            yield _event("answer", "缺少资源信息，请说明要预约哪个资源。")
            return
        target = next((r for r in resources if r["id"] == resource_id), None)
        if target is None:
            yield _event("error", "所选资源不在可预约列表内。")
            return

        # 权限确定性校验（Java 规则表），放行结论不由模型定
        yield _event("check", f"正在校验您对「{target['name']}」的预约权限…")
        try:
            decision = await java_client.check_permission(target["type"])
        except java_client.JavaError as e:
            yield _event("error", f"权限校验失败：{e}")
            return
        if not decision.get("allowed"):
            yield _event("denied", _denied_text(decision.get("reason")))
            return

        # 抢票（Java 内部再走限流/幂等/扣库存）
        request_id = uuid.uuid4().hex[:32]
        yield _event("act", "权限通过，正在为您抢票…")
        try:
            result = await java_client.book_resource(target["id"], request_id)
        except java_client.JavaError as e:
            msg = e.msg if e.code in (409, 429, 400, 403) else f"预约处理失败（{e.code}）：{e.msg}"
            yield _event("error", msg)
            return
        yield _event("result", result.get("message", "预约已提交。"))
