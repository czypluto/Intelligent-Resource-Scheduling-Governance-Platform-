"""调用 Java（铁路购票）的 REST 客户端。执行只走 REST，透传当前用户 Token。

对应 Java 能力：
  查车站 /api/rail/stations
  查票   /api/ticket/query
  下单   /api/ticket/buy
  我的订单/支付/退票  /api/ticket/orders/**
"""
import json
import logging
from typing import Any, Optional
from urllib.parse import urlencode

import httpx

from . import config
from .middleware import raw_token

logger = logging.getLogger(__name__)

_client: Optional[httpx.AsyncClient] = None


def _get_client() -> httpx.AsyncClient:
    """连接复用：进程内共享一个 AsyncClient，避免每请求新建。"""
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=20)
    return _client


class JavaError(Exception):
    def __init__(self, code: int, msg: str):
        super().__init__(msg)
        self.code = code


async def _call(method: str, path: str, body: Optional[dict] = None) -> Any:
    url = f"{config.JAVA_BASE}{path}"
    headers = {"Authorization": f"Bearer {raw_token()}"}
    resp = await _get_client().request(method, url, json=body, headers=headers)
    try:
        payload = resp.json()
    except json.JSONDecodeError:
        raise JavaError(500, f"Java 返回非 JSON：{resp.status_code}")
    code = payload.get("code", 0)
    if code != 0:
        raise JavaError(code, payload.get("msg", "服务处理失败"))
    return payload.get("data")


async def stations(kw: Optional[str] = None) -> list[dict]:
    qs = "" if not kw else "?" + urlencode({"kw": kw})
    return await _call("GET", f"/api/rail/stations{qs}") or []


async def query_tickets(from_station: str, to_station: str, date: str, seat_class: Optional[str] = None) -> list[dict]:
    """按站名/日期查余票（Java 内部按站 id 过滤停站顺序）。"""
    from_id, to_id = await _resolve_station(from_station), await _resolve_station(to_station)
    params = {"from": from_id, "to": to_id, "date": date}
    if seat_class:
        params["seatClass"] = seat_class
    rows = await _call("GET", "/api/ticket/query?" + urlencode(params)) or []
    return rows


async def buy_ticket(trip_id: int, seat_class: str, from_station: str, to_station: str,
                     ticket_type: str = "ADULT") -> dict:
    from_id, to_id = await _resolve_station(from_station), await _resolve_station(to_station)
    body = {
        "tripId": trip_id,
        "seatClass": seat_class,
        "fromStationId": from_id,
        "toStationId": to_id,
        "ticketType": ticket_type or "ADULT",
    }
    return await _call("POST", "/api/ticket/buy", body)


async def my_orders() -> list[dict]:
    return await _call("GET", "/api/ticket/orders/my") or []


async def pay(request_id: str) -> dict:
    return await _call("POST", f"/api/ticket/orders/{request_id}/pay")


async def cancel(request_id: str) -> dict:
    return await _call("POST", f"/api/ticket/orders/{request_id}/cancel")


async def _resolve_station(name: str) -> int:
    rows = await stations(kw=name)
    if not rows:
        raise JavaError(400, f"未找到车站：{name}")
    return rows[0]["id"]
