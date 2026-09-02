#!/usr/bin/env bash
# 一键拉起本地开发环境：Redis/MySQL -> Java(8080) -> Python(8000, dev)
# 用法：bash scripts/start-dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "已生成 .env，请填入 DEEPSEEK_API_KEY 后重跑。"
  exit 1
fi

up() { curl -s -o /dev/null "$1"; }

# 1) Redis + MySQL
if docker info >/dev/null 2>&1; then
  (cd infra && docker compose up -d)
  echo "[1/3] Redis + MySQL 已拉起"
else
  echo "[1/3] 警告：Docker 引擎未运行，跳过 Redis/MySQL。请先启动 Docker Desktop（或 WSL2 内 docker）。"
fi

# 2) Java
if up http://127.0.0.1:8080/api/resources; then
  echo "[2/3] Java 已在 8080 运行，跳过启动"
else
  echo -n "[2/3] 启动 Java (8080)…"
  (cd backend-java && mvn -q -DskipTests spring-boot:run > target/boot.log 2>&1 &)
  for _ in $(seq 1 60); do
    up http://127.0.0.1:8080/api/resources && break
    sleep 2
  done
  if up http://127.0.0.1:8080/api/resources; then
    echo " 就绪"
  else
    echo " 失败，见 backend-java/target/boot.log"
    exit 1
  fi
fi

# 3) Python（venv 不存在则创建）
if [ ! -x backend-python/.venv/Scripts/python ] && [ ! -x backend-python/.venv/bin/python ]; then
  echo "[3/3] 首次运行，创建 venv 并安装依赖…"
  (cd backend-python && python -m venv .venv)
  PY="backend-python/.venv/Scripts/python"
  [ -x "$PY" ] || PY="backend-python/.venv/bin/python"
  (cd backend-python && "$PY" -m pip install -q -r requirements.txt)
else
  PY="backend-python/.venv/Scripts/python"
  [ -x "$PY" ] || PY="backend-python/.venv/bin/python"
fi

if up http://127.0.0.1:8000/api/health; then
  echo "[3/3] Python 已在 8000 运行，跳过启动"
else
  echo -n "[3/3] 启动 Python (8000)…"
  (cd backend-python && "$PY" -m uvicorn app.main:app --port 8000 > target-uvicorn.log 2>&1 &)
  for _ in $(seq 1 30); do
    up http://127.0.0.1:8000/api/health && break
    sleep 2
  done
  up http://127.0.0.1:8000/api/health && echo " 就绪" || { echo " 失败，见 backend-python/target-uvicorn.log"; exit 1; }
fi

echo
echo "Java    http://localhost:8080   日志 backend-java/target/boot.log"
echo "Python  http://localhost:8000   日志 backend-python/target-uvicorn.log（模型路由以 /api/health 为准）"
echo "前端：cd frontend-vue && npm run dev   ->  http://localhost:5173"
echo "停止：bash scripts/stop-dev.sh；重置演示数据：bash scripts/reset-demo.sh"
