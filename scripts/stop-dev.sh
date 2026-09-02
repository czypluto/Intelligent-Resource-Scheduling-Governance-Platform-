#!/usr/bin/env bash
# 停止本地开发服务（Java/Python），保留 Redis/MySQL 容器
# 用法：bash scripts/stop-dev.sh
for port in 8080 8000; do
  PID=$(netstat -ano 2>/dev/null | grep ":$port " | grep LISTENING | awk '{print $5}' | head -1)
  if [ -n "$PID" ]; then
    taskkill //PID "$PID" //F >/dev/null 2>&1 && echo "已停止 :$port (pid $PID)"
  else
    echo ":$port 未在运行"
  fi
done
echo "Redis/MySQL 容器保留运行；如需一并停：cd infra && docker compose stop"
