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
from . import memory as mem

logger = logging.getLogger(__name__)

RULE_HINTS = ("退票", "改签", "儿童", "学生", "票价", "携带", "行李", "证件", "规则", "能不能", "什么规定", "手续费")

# 动作词：命中则视为"办业务"而非"问规则"，避免规则词把下单请求吞进 RAG
ACTION_WORDS = ("买", "订", "购", "预约", "帮我查", "下单")

CONFIRM_WORDS = ("确认", "是的", "可以", "好的", "就买", "下单", "买吧")


def _event(kind: str, text: str, task: str = "agent") -> dict:
    return {"kind": kind, "text": text, "model": config.resolve_model(task)}


def _is_known_trip(rows, trip_id) -> bool:
    """tripId 是否来自最近的查票结果（防模型臆造车次）。"""
    if not rows:
        return False
    return any(r.get("tripId") == trip_id for r in rows)


def _history_messages(raw: list[dict]) -> list[dict]:
    """把记忆里的轮次清洗成可用的对话历史（去重角色/去空/限长），供注入上下文。"""
    out: list[dict] = []
    prev = None
    for m in raw:
        role = m.get("role")
        content = (m.get("content") or "").strip()
        if role not in ("user", "assistant") or not content or role == prev:
            continue
        out.append({"role": role, "content": content})
        prev = role
    if out and out[0]["role"] != "user":
        out = out[1:]
    if out and out[-1]["role"] == "user":
        out = out[:-1]  # 不落一条悬空 user（本轮会重发）
    return out[-20:]


def _system_prompt() -> str:
    return (
        "你是铁路购票助手。今天是%s。查询用 query_tickets（站名+日期），下单用 buy_ticket（tripId 来自查询结果），"
        "用户要查自己的订单用 my_orders。若必要参数缺失（如出发地/日期），直接向用户说明缺什么，不要编造参数。"
        "涉及退改签/儿童/学生等规定的问题不调用工具，直接回答。金额单位是分。说话简洁公文化。" % date.today().isoformat()
    )


class AgentService:
    def __init__(self) -> None:
        self._rag: Optional[RagStore] = None
        # 最近一次查票结果（按用户），用于约束 buy_ticket 的 tripId 必须来自查询
        self._rows: dict[int, list] = {}
        # 待确认的下单意向（按用户）：模型反向复述后，用户确认才真正下单
        self._intents: dict[int, dict] = {}
        # 刚下单的待支付订单 requestId（按用户），便于用户回“支付”完成付款
        self._orders: dict[int, str] = {}

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

        # 两段式确认：上一轮已"反向复述待确认"的意向/待支付订单，本轮回“确认/支付”即执行
        pay_rid = self._orders.get(user.user_id)
        if pay_rid and any(w in user_text for w in ("支付", "付款", "付钱")):
            self._orders.pop(user.user_id, None)
            try:
                r = await java_client.pay(pay_rid)
                yield _event("result", f"订单 {pay_rid} 已支付，状态 {r.get('status')}。")
            except java_client.JavaError as e:
                yield _event("error", e.msg)
            return
        intent = self._intents.get(user.user_id)
        if intent and any(w in user_text for w in CONFIRM_WORDS):
            # 用户确认：执行预填的下单
            self._intents.pop(user.user_id, None)
            async for ev in self._commit_buy(user, intent):
                yield ev
            return
        if intent:
            # 用户改了主意/新请求：作废旧意向，落到下面按新请求处理
            self._intents.pop(user.user_id, None)

        # 规则类问题直接走知识库（若含动作词则视为办业务，交给工具循环）
        if any(h in user_text for h in RULE_HINTS) and not any(a in user_text for a in ACTION_WORDS):
            async for ev in self._rule_answer(user_text):
                yield ev
            return

        history = _history_messages(mem.load(user.user_id))
        msgs = [{"role": "system", "content": _system_prompt()}]
        msgs.extend(history)  # 当前对话长期记忆（此前轮次）
        msgs.append({"role": "user", "content": f"已知车站：{station_line}\n\n用户请求：{user_text}"})

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
                    # 结果已由前面的 [result] 事件给出，这里不补无信息收尾；
                    # 若模型确有补充话术则照实给
                    if out.content and out.content.strip():
                        yield _event("answer", out.content)
                elif any(a in user_text for a in ACTION_WORDS):
                    # 办业务但模型没调工具（多半缺信息）：把模型的话还给用户，不再发占位/RAG
                    text = out.content or "请补充需要办理的内容，例如出发地/到达地/日期/席别，或要操作的订单。"
                    yield _event("answer", text)
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
                        self._rows[user.user_id] = rows
                        shown = True
                        acted = True
                        text = self._query_text(rows)
                        msgs.append({"role": "assistant", "content": f"查票结果：\n{text}"})
                        yield _event("result", text)
                    elif name == "buy_ticket":
                        # 防臆造 + 反向复述：tripId 必须来自查票结果，且下单前请用户确认
                        row = next((r for r in self._rows.get(user.user_id, [])
                                    if r.get("tripId") == args.get("tripId")), None)
                        if row is None:
                            yield _event("error", "请先使用 query_tickets 查询车次，再从结果中选择要购买的车次。")
                            return
                        intent = {
                            "tripId": row["tripId"],
                            "seatClass": row["seatClass"],
                            "from": row["from"],
                            "to": row["to"],
                            "date": row.get("travelDate"),
                            "trainCode": row.get("trainCode"),
                            "ticketType": (args.get("ticketType") or "ADULT").upper(),
                        }
                        self._intents[user.user_id] = intent
                        shown = True
                        acted = True
                        base = (f"请确认下单：{intent['trainCode']} {intent['date']} "
                                f"{intent['from']}→{intent['to']} {intent['seatClass']} "
                                f"({intent['ticketType']}) 回复“确认”即出票。")
                        if intent["ticketType"] in ("CHILD", "STUDENT"):
                            rule = await self._rule_snippet(
                                "儿童票" if intent["ticketType"] == "CHILD" else "学生票")
                            if rule:
                                base = f"下单前请核对规则：{rule[:180]}\n{base}"
                        confirm = base
                        msgs.append({"role": "assistant", "content": confirm})
                        yield _event("confirm", confirm)
                        return
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
        # 循环结束兜底：结果已由各 [result] 给出；只有完全没出结果才提示补充
        if not shown:
            yield _event("answer", "暂时没能自动办理，请补充需要办理的内容（车次/日期/席别或订单号）。")

    async def _commit_buy(self, user, intent) -> AsyncIterator[dict]:
        """用户确认后执行预填下单（确定性路径，不再让模型介入）。"""
        try:
            r = await java_client.buy_ticket(
                int(intent["tripId"]), intent["seatClass"], intent["from"], intent["to"],
                ticket_type=intent.get("ticketType", "ADULT"))
        except java_client.JavaError as e:
            yield _event("error", e.msg)
            return
        rid = r.get("requestId")
        if rid:
            self._orders[user.user_id] = rid
        line = (f"下单成功：{r.get('from')}->{r.get('to')} {r.get('seatClass')}，"
                f"金额 {r.get('priceCents')}分，状态 {r.get('status')}，订单号 {r.get('orderNo')}。"
                f"回复“支付”完成付款。")
        yield _event("result", line)

    async def _rule_snippet(self, keyword: str) -> str:
        """RAG 取一条规则摘要，供下单确认前置（A 层：信息前置）。"""
        rag = self._rag_store()
        if rag and rag.enabled:
            try:
                hits = await rag.retrieve(keyword, department=current_user().department, top_k=1)
                if hits:
                    return hits[0].strip()
            except Exception:  # noqa: BLE001
                pass
        return ""

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
