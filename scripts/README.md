# scripts

## 一键脚本（Windows Git Bash / WSL2 通用）

| 脚本 | 作用 |
|---|---|
| `start-all.sh` | **一键启动全部服务**：Redis/MySQL → Java(8080) → WSL 嵌入服务(8001) → WSL Python 主服务(8000) → 前端(5173)。在 Windows Git Bash 里 `bash scripts/start-all.sh` 即可，幂等可重复跑 |
| `stop-all.sh` | 停全部（前端/Java + WSL Python/嵌入），保留容器 |
| `start-dev.sh` | 只拉起后端（Redis/MySQL + Java + Python），不启前端与嵌入；首次运行自动建 venv 装依赖 |
| `stop-dev.sh` | 停 Windows 侧 Java/Python（保留容器） |
| `reset-demo.sh` | 清订单 + 清 Redis，演示数据回到初始（无需重启 Java） |
| `e2e_smoke.py` | Java 端到端冒烟（登录/权限/越权拦截/抢票/幂等/落库） |
| `chat_e2e.py` | 对话链路联调：登录(Java) → Python SSE → 真实 DeepSeek；用法 `python chat_e2e.py <账号> <话>` |

示例：

```bash
bash scripts/start-dev.sh
bash scripts/chat_e2e.py zhanggong "帮我预约员工班车座位"
bash scripts/reset-demo.sh
```

## 手动静默步骤（对应 docs/技术方案-v2.md §9.4）

1. WSL2 就绪（`.wslconfig` 12GB，`wsl --shutdown` 后重启）
2. `cd infra && docker compose up -d`
3. `cd backend-java && mvn spring-boot:run`
4. `cd backend-python && uvicorn app.main:app --port 8000`
5. `cd frontend-vue && npm run dev`

## 说明

- Python 从根目录 `.env` 读配置（含 `DEEPSEEK_API_KEY` 与模型路由）。Java 用环境变量，默认值与 infra 一致即可。
- 模型路由：`/api/health` 会返回当前任务→模型映射。dev=pro，上线把 `MODEL_AGENT/MODEL_MULTIMODAL` 指到 `deepseekv4flash`。
- DeepSeek 官方 model id 带连字符（`deepseek-v4-pro` / `deepseek-v4-flash`），通过 `.env` 的 `MODEL_ID_*` 映射。
- 运行拓扑：Java 在 Windows（8080）；Python 主服务与嵌入服务(8001)在 WSL，`--host 0.0.0.0` 绑定，前端自动经 WSL IP 访问。停 WSL 里的 Python：`wsl -e pkill -f "uvicorn app.main"`（停嵌入：`pkill -f app.embed.server`）。`stop-dev.sh` 只停 Windows 侧端口。
