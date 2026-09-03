"""Agent 调度中心（铁路购票）：自然语言 -> Java 接口参数。

流程：意图识别 -> 参数补全 -> 查票 -> 展示并引导下单 -> 支付/退票。
纯规则问题（退改签/儿童票/学生票…）走 RAG 知识库；其余走工具调用 Java。
"""
import logging
import uuid
from datetime import date
from typing import AsyncIterator, Optional

from .. import config, java_client, llm, tools
from ..llm import LlmError
from ..middleware import current_user
from ..rag.store import RagStore, rag_available

logger = logging.getLogger(__name__)

RULE_HINTS = ("退票", "改签", "儿童", "学生", "票价", "携带", "证件", "规则", "能不能", "什么规定", "手续费")


def _event(kind: str, text: str, task: str = "agent") -> dict:
    return {"kind": kind, "text": text, "model": config.resolve_model(task)}


def _system_prompt() -> str:
    return (
        "你是铁路购票助手。今天是%s。查询用 query_tickets（站名+日期），下单用 buy_ticket（tripId 来自查询结果），"
        "用户要查自己的订单用 my_orders。若必要参数缺失（如出发地/日期），直接向用户说明缺什么，不要编造参数。"
        "涉及退改签/儿童/学生等规定的问题不调用工具，直接回答。金额单位是分。说话简洁公文化。" % date.today().isoformat()
    )


class AgentService:
    def __init__(self) -> None:
        self._rag: Optional[RagStore] = None

    def _rag_store(self) -> Optional[RagStore]:
        if self._rag is None:
            self._rag = RagStore() if rag_available() else None
        return self._rag

    async def handle(self, user_text: str) -> AsyncIterator[dict]:
        user = current_user()
        yield _event("think", f"已收到请求，正在为您办理（{user.identity}）。")

        try:
            stations = await java_client.stations()
        except Exception as e:  # noqa: BLE001
            logger.exception("车站服务不可用")
            stations = []
        station_line = "、".join(s["name"] for s in stations[:30])

        # 规则类问题直接走知识库，避免空转调用工具
        if any(h in user_text for h in RULE_HINTS):
            async for ev in self._rule_answer(user_text):
                yield ev
            return

        msgs = [
            {"role": "system", "content": _system_prompt()},
            {"role": "user", "content": f"已知车站：{station_line}\n\n用户请求：{user_text}"},
        ]

        rounds = 0
        shown = False
        while rounds < 4:
            rounds += 1
            try:
                out = await llm.chat(task="agent", messages=msgs, tools=tools.all_schemas())
            except LlmError as e:
                yield _event("error", str(e))
                return

            if not out.tool_calls:
                if shown:
                    # 已给出工具结果，直接收尾，不再当规则问题处理
                    yield _event("answer", "以上是办理结果。如需继续下单/支付/退票，直接告诉我即可。")
                else:
                    async for ev in self._rule_answer(user_text):
                        yield ev
                return

            acted = False
            for tc in out.tool_calls:
                name, args = tc.name, tc.arg_dict()
                try:
                    if name == "query_tickets":
                        rows = await java_client.query_tickets(
                            args.get("from", ""), args.get("to", ""),
                            args.get("date", date.today().isoformat()),
                            args.get("seatClass"))
                        shown = True
                        acted = True
                        text = self._query_text(rows)
                        msgs.append({"role": "assistant", "content": f"查票结果：\n{text}"})
                        yield _event("result", text)
                    elif name == "buy_ticket":
                        r = await java_client.buy_ticket(
                            int(args["tripId"]), args["seatClass"], args["from"], args["to"])
                        shown = True
                        acted = True
                        line = (f"下单成功：车票 {r.get('from')}->{r.get('to')} "
                                f"{r.get('seatClass')}，金额 {r.get('priceCents')}分，状态 {r.get('status')}，"
                                f"订单号 {r.get('orderNo')}。requestId={r.get('requestId')}")
                        msgs.append({"role": "assistant", "content": line})
                        yield _event("result", line)
                    elif name == "my_orders":
                        rows = await java_client.my_orders()
                        shown = True
                        acted = True
                        text = self._orders_text(rows)
                        msgs.append({"role": "assistant", "content": f"我的订单：\n{text}"})
                        yield _event("result", text)
                    elif name == "pay_ticket":
                        rid = args["requestId"]
                        r = await java_client.pay(rid)
                        shown = True
                        acted = True
                        line = f"订单 {rid} 已支付，状态 {r.get('status')}"
                        msgs.append({"role": "assistant", "content": line})
                        yield _event("result", line)
                    elif name == "cancel_ticket":
                        rid = args["requestId"]
                        r = await java_client.cancel(rid)
                        shown = True
                        acted = True
                        line = f"订单 {rid} 已退票，状态 {r.get('status')}，余票已回补"
                        msgs.append({"role": "assistant", "content": line})
                        yield _event("result", line)
                except java_client.JavaError as e:
                    yield _event("error", f"{e.msg}")
                    return

            if not acted:
                break

            # 是否还需要继续（如查完票再下单）：再问一次模型，让它决定收尾还是下单
            msgs.append({"role": "user",
                         "content": "基于以上结果：如果还需继续就调用下一个工具；已完事则仅用一句话回复用户。"})
        # 循环结束：给一句收尾（兜底）
        yield _event("answer", "已为您办理。需要支付或退票时告诉我订单号即可。")

    async def _rule_answer(self, user_text: str) -> AsyncIterator[dict]:
        rag = self._rag_store()
        context = await rag.retrieve(user_text, department=current_user().department) if rag and rag.enabled else []
        try:
            if context:
                answer = await llm.chat(
                    task="gen",
                    messages=[
                        {"role": "system", "content": "严格依据资料回答：有明确数字/条件就直说，没有的明说未查到。"},
                        {"role": "user", "content": f"资料：\n{chr(10).join(context)}\n\n问题：{user_text}"},
                    ],
                )
            else:
                answer = await llm.chat(
                    task="gen",
                    messages=[
                        {"role": "system", "content": "你是铁路售票助手，不知道的别编，引导用户查看官网或客服。"},
                        {"role": "user", "content": user_text},
                    ],
                )
        except LlmError as e:
            yield _event("error", str(e), "gen")
            return
        yield _event("answer", answer.content, "gen")

    @staticmethod
    def _query_text(rows: list[dict]) -> str:
        if not rows:
            return "该区间当日无可用车次或余票。"
        lines = []
        for r in rows:
            lines.append(f"- tripId={r['tripId']} {r['trainCode']} {r['from']}->{r['to']} "
                         f"{r['departTime']}-{r['arriveTime']} {r['seatClass']} "
                         f"{r['priceCents']}分 余{r['remaining']}张")
        return "\n".join(lines)

    @staticmethod
    def _orders_text(rows: list[dict]) -> str:
        if not rows:
            return "当前没有订单。"
        return "\n".join(
            f"- {o.get('orderNo')} {o.get('from')}->{o.get('to')} {o.get('seatClass')} "
            f"{o.get('priceCents')}分 {o.get('status')} requestId={o.get('requestId')}"
            for o in rows)
