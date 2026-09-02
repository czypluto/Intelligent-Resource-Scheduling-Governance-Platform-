# backend-python

Python 智能链路（FastAPI，端口 8000）。三合一：JWT 校验中间件 + Agent 调度 + RAG 检索；模型路由见 `app/config.py`。

## 目录

```
app/
  config.py       环境与模型路由（铁律：模型名只在这里）
  middleware.py   全局 JWT 校验，失败 401，不进入 RAG 不调 Java
  security.py     JWT 解析（与 Java 共用 JWT_SECRET，HS256）
  llm.py          模型路由层：chat(task, ...)，统一 OpenAI 兼容协议
  java_client.py  REST 调 Java（只此一条执行通道）
  tools.py        Agent 工具 schema（与 backend-java ToolCatalog 对齐）
  agent/          编排：意图识别 -> 决策 -> 权限校验 -> 抢票
  rag/            Small-to-Big 检索（可选，需 pymilvus + 本地 vLLM）
  routers/chat.py POST /api/chat，SSE 流式
  main.py         入口 + /api/health
```

## 运行

在 WSL2 内（建议 Python 3.11+）：

```bash
pip install -r requirements.txt
# 如需 RAG：pip install pymilvus numpy，并确保本地 vLLM 起 Qwen3-Embedding（EMBED_BASE）
uvicorn app.main:app --port 8000
```

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/chat | 对话（带 Token，SSE 流式返回 stage 事件） |
| GET  | /api/health | 健康 + 当前模型路由（观察 dev/prod 切换是否生效） |

## 模型路由

- `ENV=dev`：`MODEL_AGENT/MODEL_MULTIMODAL/MODEL_GEN` 默认 `deepseekv4pro`。
- 上线后：把前两者设为 `deepseekv4flash` 即可，代码零改动；每个 SSE 事件带 `model` 字段。
- `MODEL_ID_*` 可在官方模型标识与别名不同时覆盖。

## 说明

- 权限结论以 Java 规则表为准（`/api/perms/check`），模型只做解释。
- RAG 依赖未就绪时自动降级，不影响预约主流程。
