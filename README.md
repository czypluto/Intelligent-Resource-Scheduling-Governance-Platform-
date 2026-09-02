# 基于AI大模型的集团智能资源预约与管控中台

面向央国企内部的资源预约与管控平台：员工通过自然语言对话预约班车座位、会议室、福利等资源，后端由高并发交易引擎（Java）与 RAG+Agent 智能链路（Python）共同支撑。

## 结构

| 目录 | 说明 |
|---|---|
| `docs/` | 方案文档（先读 `docs/技术方案-v2.md`） |
| `backend-java/` | Spring Boot 3：认证/JWT、权限规则校验、秒杀核心、限流幂等 |
| `backend-python/` | FastAPI：JWT 中间件、模型路由、Agent、RAG、SSE |
| `frontend-vue/` | Vue 3 + Element Plus 前端 |
| `infra/` | Redis/MySQL 编排与初始化 |
| `scripts/` | 启动与联调脚本 |

## 环境要求

- Windows + WSL2（`C:\Users\<user>\.wslconfig` 给 12GB 内存）
- Docker（WSL2 内）、JDK 17、Node 18+、Python 3.11+
- NVIDIA 显卡 + 支持 CUDA in WSL 的驱动（vLLM 用）
- DeepSeek 官网 API key

详见 `CLAUDE.md` 与 `docs/技术方案-v2.md`。
