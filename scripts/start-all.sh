#!/usr/bin/env bash
# 一键启动全部服务（Windows Git Bash 里运行）：
#   Redis/MySQL(容器) -> Java(8080) -> WSL 嵌入服务(8001) -> WSL Python 主服务(8000) -> 前端(5173)
# 自动处理：Docker 引擎未运行则拉起 Docker Desktop；终端缺 JAVA_HOME 则自动探测 JDK；MySQL 就绪后再启 Java。
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

# 0) Docker 引擎：未运行则自动拉起 Docker Desktop
ensure_docker() {
  docker info >/dev/null 2>&1 && { info "Docker 引擎就绪"; return 0; }
  info "Docker 引擎未运行，尝试启动 Docker Desktop…"
  local exe=""
  for c in \
    "$LOCALAPPDATA/Programs/DockerDesktop/Docker Desktop.exe" \
    "/c/Program Files/Docker/Docker/Docker Desktop.exe"; do
    [ -f "$c" ] && { exe="$c"; break; }
  done
  if [ -n "$exe" ]; then
    powershell -NoProfile -Command "Start-Process -FilePath '$exe'" >/dev/null 2>&1
    for _ in $(seq 1 60); do
      docker info >/dev/null 2>&1 && { info "Docker 引擎就绪"; return 0; }
      sleep 3
    done
  fi
  info "Docker 引擎仍不可用。请手动启动 Docker Desktop 后重跑。"
  return 1
}

# 0.5) JAVA_HOME：未设置时自动探测本机 JDK
ensure_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then return 0; fi
  local d
  for d in \
    "/c/Program Files/Java"/jdk-* \
    "/c/Program Files/Eclipse Adoptium"/jdk-* \
    "$LOCALAPPDATA/Programs/Eclipse Adoptium"/jdk-*; do
    if [ -x "$d/bin/java.exe" ]; then
      export JAVA_HOME="$d"
      info "探测到 JAVA_HOME=$d"
      return 0
    fi
  done
  info "未找到 JDK，请先设置 JAVA_HOME"
  return 1
}

# MySQL 就绪等待（容器起来后仍需初始化几秒）
wait_mysql() {
  for _ in $(seq 1 40); do
    docker exec resv-mysql mysqladmin ping -uresv -presv123 --silent >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}

[ -f .env ] || { info "缺少 .env，请先 cp .env.example .env 并填 DEEPSEEK_API_KEY"; exit 1; }
ensure_docker || exit 1

# 1) Redis + MySQL
(cd infra && docker compose up -d) >/dev/null 2>&1
if wait_mysql; then
  info "Redis/MySQL 就绪"
else
  info "MySQL 启动超时，见 infra 容器日志"
  exit 1
fi

# 2) Java(8080, Windows)
ensure_java_home || exit 1
if is_up http://127.0.0.1:8080/api/resources; then
  info "Java(8080) 已在运行，跳过"
else
  info "启动 Java(8080)…（首次会编译，稍等）"
  (cd backend-java && nohup mvn -q -DskipTests spring-boot:run > target/boot.log 2>&1 &)
  if wait_up 60 is_up http://127.0.0.1:8080/api/resources; then
    info "Java(8080) 就绪"
  else
    info "Java 启动失败，boot.log 末尾："
    tail -15 backend-java/target/boot.log
    exit 1
  fi
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
