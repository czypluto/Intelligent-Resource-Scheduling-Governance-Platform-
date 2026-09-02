#!/usr/bin/env bash
# 重置演示数据：清空订单 + 清 Redis（库存/座位号回到初始）。
# 无需重启 Java：下次抢票时服务会对缺库存的资源自动预热。
# 用法：bash scripts/reset-demo.sh
set -euo pipefail

if ! docker info >/dev/null 2>&1; then
  echo "Docker 引擎未运行，无法重置。"
  exit 1
fi

docker exec resv-mysql mysql -uresv -presv123 -e "use resv; delete from reservation_order;" 2>/dev/null
docker exec resv-redis redis-cli FLUSHDB >/dev/null
echo "演示数据已重置（订单清空，库存与座位号归零）"
