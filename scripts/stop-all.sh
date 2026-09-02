#!/usr/bin/env bash
# 停止全部服务：前端/Java(Windows) + WSL Python/嵌入；保留 Redis/MySQL 容器。
# 用法：bash scripts/stop-all.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

stop_windows_port() {
  local pid
  pid=$(netstat -ano 2>/dev/null | grep ":$1 " | grep LISTENING | awk '{print $5}' | head -1)
  if [ -n "$pid" ]; then
    taskkill //PID "$pid" //F >/dev/null 2>&1 && echo "[stop] Windows :$1 已停 (pid $pid)"
  fi
}

# Windows 侧：前端 5173、Java 8080
stop_windows_port 5173
stop_windows_port 8080

# WSL 侧：Python 主服务 + 嵌入服务
wsl -e bash -lc 'pkill -f "uvicorn app.main" 2>/dev/null; pkill -f "uvicorn app.embed.server" 2>/dev/null; echo "[stop] WSL Python/嵌入已停"' 2>/dev/null || true

echo "Redis/MySQL 容器保留运行；如需一并停：cd infra && docker compose stop"
