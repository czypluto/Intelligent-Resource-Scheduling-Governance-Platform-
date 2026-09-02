#!/usr/bin/env bash
# 一键启动全部服务（在 Windows Git Bash 里运行）：
#   Redis/MySQL(容器) -> Java(8080) -> WSL 嵌入服务(8001) -> WSL Python 主服务(8000) -> 前端(5173)
# 用法：bash scripts/start-all.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

info() { printf '[start-all] %s\n' "$*"; }
is_up()  { curl -s -o /dev/null "$1"; }
wsl_up() { wsl -e bash -lc "curl -s -o /dev/null http://127.0.0.1:$1$2" >/dev/null 2>&1; }
wait_up() { # wait_up <秒> <命令...>
  local n=$1; shift
  for _ in $(seq 1 "$n"); do "$@" && return 0; sleep 2; done
  return 1
}

[ -f .env ] || { info "缺少 .env，请先 cp .env.example .env 并填 DEEPSEEK_API_KEY"; exit 1; }

# 1) Redis + MySQL
if docker info >/dev/null 2>&1; then
  (cd infra && docker compose up -d) && info "Redis/MySQL 就绪"
else
  info "警告：Docker 引擎未运行，跳过 Redis/MySQL"
fi

# 2) Java(8080, Windows)
if is_up http://127.0.0.1:8080/api/resources; then
  info "Java(8080) 已在运行，跳过"
else
  info "启动 Java(8080)…（首次会编译，稍等）"
  (cd backend-java && nohup mvn -q -DskipTests spring-boot:run > target/boot.log 2>&1 &)
  wait_up 60 is_up http://127.0.0.1:8080/api/resources \
    && info "Java(8080) 就绪" \
    || { info "Java 启动失败，见 backend-java/target/boot.log"; exit 1; }
fi

# 3) WSL 嵌入服务(8001, bge-m3)
if wsl_up 8001 /health; then
  info "嵌入服务(WSL:8001) 已在运行，跳过"
else
  info "启动嵌入服务(WSL:8001)…"
  (nohup wsl -e bash -lc 'cd backend-python && EMBED_MODEL_DIR=/home/cpluto/models/bge-m3 EMBED_MODEL=bge-m3 exec python3 -m uvicorn app.embed.server:app --host 0.0.0.0 --port 8001' > backend-python/wsl-embed.log 2>&1 &)
  wait_up 60 wsl_up 8001 /health \
    && info "嵌入服务(WSL:8001) 就绪" \
    || { info "嵌入服务启动失败，见 backend-python/wsl-embed.log"; exit 1; }
fi

# 4) WSL Python 主服务(8000)
if wsl_up 8000 /api/health; then
  info "Python 主服务(WSL:8000) 已在运行，跳过"
else
  info "启动 Python 主服务(WSL:8000)…"
  (nohup wsl -e bash -lc 'gw=$(ip route | awk "/default/{print \$3}"); cd backend-python && JAVA_BASE="http://$gw:8080" exec python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000' > backend-python/wsl-uvicorn.log 2>&1 &)
  wait_up 60 wsl_up 8000 /api/health \
    && info "Python 主服务(WSL:8000) 就绪" \
    || { info "Python 主服务启动失败，见 backend-python/wsl-uvicorn.log"; exit 1; }
fi

# 5) 前端(5173)
if is_up http://localhost:5173/; then
  info "前端(5173) 已在运行，跳过"
else
  info "启动前端(5173)…"
  (cd frontend-vue && nohup npm run dev > target-vite.log 2>&1 &)
  wait_up 30 is_up http://localhost:5173/ \
    && info "前端(5173) 就绪" \
    || { info "前端启动失败，见 frontend-vue/target-vite.log"; exit 1; }
fi

info "全部就绪：打开 http://localhost:5173  （演示账号 zhanggong / wangzong，密码 123456）"
info "停止所有：bash scripts/stop-all.sh"
