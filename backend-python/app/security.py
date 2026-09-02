"""JWT 校验。与 Java 共用密钥与算法，职责只在身份提取，不做业务判断。"""
from dataclasses import dataclass

from jose import jwt, JWTError

from .config import JWT_ALG, JWT_SECRET


@dataclass
class UserCtx:
    user_id: int
    username: str
    name: str
    department: str
    position: str
    role: str

    @property
    def identity(self) -> str:
        """给 Agent/日志用的一句话身份。"""
        return f"{self.name}（{self.department}，{self.position}）"


class TokenInvalidError(Exception):
    pass


def parse_token(token: str) -> UserCtx:
    """解析并校验 JWT，失败抛 TokenInvalidError（调用方转 401）。"""
    try:
        claims = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALG])
    except JWTError as e:
        raise TokenInvalidError("Token 无效或已过期，请重新登录") from e
    return UserCtx(
        user_id=int(claims["sub"]),
        username=claims.get("username", ""),
        name=claims.get("name", ""),
        department=claims.get("department", ""),
        position=claims.get("position", ""),
        role=claims.get("role", ""),
    )
