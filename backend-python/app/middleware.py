"""全局鉴权中间件：Token 校验失败即 401，不进入 RAG、不调用 Java。"""
import logging
from contextvars import ContextVar

from fastapi import Request
from fastapi.responses import JSONResponse

from .security import TokenInvalidError, UserCtx, parse_token

logger = logging.getLogger(__name__)

_current_user: ContextVar = ContextVar("current_user", default=None)

# 无需登录即可访问的路径
_WHITELIST = {"/api/health", "/docs", "/openapi.json", "/redoc"}

# 每个请求结束时清理上下文变量，避免串用户
_user_token_var: ContextVar = ContextVar("_auth_token", default=None)


def current_user() -> UserCtx:
    """请求处理内取当前用户（中间件注入）。"""
    user = _current_user.get()
    if user is None:
        raise PermissionError("未登录或登录已失效")
    return user


def raw_token() -> str:
    return _user_token_var.get() or ""


async def auth_middleware(request: Request, call_next):
    # 预检直接放行
    if request.method == "OPTIONS":
        return await call_next(request)
    path = request.url.path
    if path in _WHITELIST:
        return await call_next(request)

    header = request.headers.get("Authorization", "")
    if header.startswith("Bearer "):
        token = header[7:]
        try:
            user = parse_token(token)
            _current_user.set(user)
            _user_token_var.set(token)
            return await call_next(request)
        except TokenInvalidError as e:
            return JSONResponse(status_code=401, content={"code": 401, "msg": str(e), "data": None})
    return JSONResponse(status_code=401, content={"code": 401, "msg": "缺少登录凭证", "data": None})
