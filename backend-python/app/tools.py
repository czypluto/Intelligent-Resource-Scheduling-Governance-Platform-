"""Agent 可见的铁路购票能力定义（schema 与 Java REST 一一对应）。

执行路径只有一条：Python 收到工具调用 -> REST 调 Java。
与 backend-java 铁路接口保持一致；参数用站名（Java 侧模糊匹配），降低大模型臆造 id 的风险。
"""


def _fn(name: str, desc: str, props: dict, required: list[str]) -> dict:
    return {
        "type": "function",
        "function": {"name": name, "description": desc, "parameters": {
            "type": "object", "properties": props, "required": required}},
    }


def query_tickets_schema() -> dict:
    return _fn(
        "query_tickets",
        "按出发地、目的地、日期查询火车票（可指定席别）。返回车次、时间、票价与余票。",
        {
            "from": {"type": "string", "description": "出发站名，如 北京南"},
            "to": {"type": "string", "description": "到达站名，如 上海虹桥"},
            "date": {"type": "string", "description": "乘车日期 YYYY-MM-DD"},
            "seatClass": {"type": "string", "description": "可选席别：二等座/一等座/商务座"},
        },
        ["from", "to", "date"],
    )


def buy_ticket_schema() -> dict:
    return _fn(
        "buy_ticket",
        "为当前用户购买指定车次的一张票（购票前应先查票确认车次与余票）。",
        {
            "tripId": {"type": "integer", "description": "车次记录 id（来自 query_tickets）"},
            "seatClass": {"type": "string", "description": "席别：二等座/一等座/商务座"},
            "from": {"type": "string", "description": "出发站名"},
            "to": {"type": "string", "description": "到达站名"},
        },
        ["tripId", "seatClass", "from", "to"],
    )


def my_orders_schema() -> dict:
    return _fn("my_orders", "查询当前用户的全部订单（含状态）。", {}, [])


def pay_ticket_schema() -> dict:
    return _fn(
        "pay_ticket",
        "对指定 requestId 的待支付订单完成支付。",
        {"requestId": {"type": "string", "description": "订单 requestId"}},
        ["requestId"],
    )


def cancel_ticket_schema() -> dict:
    return _fn(
        "cancel_ticket",
        "对指定 requestId 的订单退票（已支付/待支付均可，退票会释放余票）。",
        {"requestId": {"type": "string", "description": "订单 requestId"}},
        ["requestId"],
    )


def all_schemas() -> list[dict]:
    return [query_tickets_schema(), buy_ticket_schema(), my_orders_schema(),
            pay_ticket_schema(), cancel_ticket_schema()]
