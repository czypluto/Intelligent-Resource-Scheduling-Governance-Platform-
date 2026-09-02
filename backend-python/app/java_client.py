"""调用 Java 交易服务的 REST 客户端。只经 REST，不带工具协议。

每个调用都透传当前用户 Token，Java 侧二次校验身份与权限。
"""
import json
import logging
from typing import Any, Optional

import httpx

from . import config
from .middleware import raw_token

logger = logging.getLogger(__name__)


class JavaError(Exception):
    def __init__(self, code: int, msg: str):
        super().__init__(msg)
        self.code = code


async def _call(method: str, path: str, body: Optional[dict] = None) -> Any:
    url = f"{config.JAVA_BASE}{path}"
    headers = {"Authorization": f"Bearer {raw_token()}"}
    async with httpx.AsyncClient(timeout=20) as client:
        resp = await client.request(method, url, json=body, headers=headers)
    try:
        payload = resp.json()
    except json.JSONDecodeError:
        raise JavaError(500, f"Java 返回非 JSON：{resp.status_code}")
    code = payload.get("code", 0)
    if code != 0:
        raise JavaError(code, payload.get("msg", "服务处理失败"))
    return payload.get("data")


async def list_resources() -> list[dict]:
    return await _call("GET", "/api/resources") or []


async def check_permission(resource_type: str) -> dict:
    """确定性权限校验，Java 给结论（allowed/reason）。"""
    return await _call("POST", "/api/perms/check", {"resourceType": resource_type})


async def book_resource(resource_id: int, request_id: str) -> dict:
    """抢票/预约。内部再做限流、幂等、扣库存。"""
    return await _call("POST", "/api/seckill", {"resourceId": resource_id, "requestId": request_id})


async def get_order(request_id: str) -> Optional[dict]:
    return await _call("GET", f"/api/orders/{request_id}")
