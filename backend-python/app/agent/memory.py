"""Agent 当前对话长期记忆：按用户存最近几轮（Redis + 进程内存兜底）。

只记用户原话 + 本轮最终答复的**摘要文本**（不存工具过程/状态），
避免上下文里回放 tool 噪音；每次请求后滚动更新并滑动续期。
"""
import json
import logging
import os

logger = logging.getLogger(__name__)

KEY_PREFIX = "mem:agent:"
MAX_MSGS = 24          # 最多保留 12 轮（24 条 user/assistant）
TTL_SEC = 3600         # 1 小时滑动过期

_fallback: dict[int, list] = {}  # 进程内存兜底（无 Redis 时）

try:
    import redis as _redis

    def _client():
        try:
            host = os.getenv("MEMORY_REDIS_HOST", "127.0.0.1")
            port = int(os.getenv("MEMORY_REDIS_PORT", "6379"))
            c = _redis.Redis(host=host, port=port, decode_responses=True, socket_timeout=1)
            c.ping()
            return c
        except Exception as e:  # noqa: BLE001
            logger.warning("Redis 不可用，记忆退回进程内存：%s", e)
            return None

    _redis_client = None
except Exception:  # noqa: BLE001
    _redis_client = False
    _redis = None


def _get() -> object:
    global _redis_client
    if _redis_client is None and _redis is not None:
        _redis_client = _client()
        if _redis_client is False:
            _redis_client = None
    return _redis_client


def load(user_id: int) -> list[dict]:
    key = f"{KEY_PREFIX}{user_id}"
    c = _get()
    if c:
        raw = c.get(key)
        if raw:
            try:
                return json.loads(raw)
            except Exception:  # noqa: BLE001
                return []
        return []
    return list(_fallback.get(user_id, []))


def append(user_id: int, user_text: str, reply: str):
    """追加一轮(user, assistant)，修剪并保存（滑动续期）。"""
    if not user_text or not reply:
        return
    msgs = load(user_id)
    # 避免连续两个 user（前一轮没有 assistant 答复）
    if msgs and msgs[-1].get("role") == "user":
        msgs.pop()
    msgs.append({"role": "user", "content": user_text[:800]})
    msgs.append({"role": "assistant", "content": reply[:1500]})
    if len(msgs) > MAX_MSGS:
        msgs = msgs[-MAX_MSGS:]
    key = f"{KEY_PREFIX}{user_id}"
    c = _get()
    if c:
        c.setex(key, TTL_SEC, json.dumps(msgs, ensure_ascii=False))
    else:
        _fallback[user_id] = msgs


def clear(user_id: int):
    c = _get()
    if c:
        c.delete(f"{KEY_PREFIX}{user_id}")
    _fallback.pop(user_id, None)
