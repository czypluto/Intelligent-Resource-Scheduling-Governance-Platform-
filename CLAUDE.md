# 工程约定

面向「基于AI大模型的集团智能资源预约与管控中台」。完整方案见 `docs/技术方案-v2.md`，改代码前先读它。

## 技术栈

- 前端：Vue 3 + Vite + Element Plus
- Java 后端：Spring Boot 3（内嵌 Tomcat）、JDK 17、Maven
- Python 后端：FastAPI + vLLM（仅本地 embedding）+ Milvus Lite + 模型路由（DeepSeek 官网 API）
- 中间件：Redis（缓存/分布式锁/Stream）、MySQL 8
- 运行时：全部服务在 WSL2 内，Windows 只跑 IDE 与浏览器

## 目录结构

```
docs/            方案文档（技术方案-v2.md）
infra/           docker-compose、初始化 SQL、.wslconfig 说明
backend-java/    Spring Boot 3 服务
backend-python/  FastAPI 服务
frontend-vue/    Vue 3 前端
scripts/         启动/联调脚本
```

## 端口约定

| 服务 | 端口 |
|---|---|
| Java | 8080 |
| Python | 8000 |
| 前端 dev | 5173 |
| Redis | 6379 |
| MySQL | 3306 |

## 模型路由（铁律）

- 业务代码**禁止写死模型名**，只认配置键：`MODEL_AGENT` / `MODEL_MULTIMODAL` / `MODEL_GEN`。
- dev：三者皆 `deepseekv4pro`；上线后（prod）：agent/multimodal 切 `deepseekv4flash`，gen 保持 `deepseekv4pro`。切换只改配置，不改代码。
- 接入 DeepSeek 官网 API，key 走环境变量 `DEEPSEEK_API_KEY`；`JWT_SECRET`（HS256）Java/Python 共用。
- SSE 推送与后端日志必须带当前 `model`，便于确认 dev=pro、prod=flash。
- 模型名若与官方 API 标识不符，以官网为准，只改配置键值。

## 后端约定

- Java base package：`com.group.resv`；接口统一 `/api/**`。
- 权限放行只由 Java 规则表做确定性判定（不经过 LLM）；模型只负责把结论解释给用户。
- 工具能力注册与 REST 单一来源：Python 只经 REST 调 Java；`@Tool`/MCP 仅登记能力元数据，启动时校验"注册的工具都有对应 REST 接口"，防双契约漂移。
- Python：Token 校验失败即 401，不进入 RAG、不调 Java。

## 前端与文案口味

- 视觉参考央企/国企信息化平台：正式、克制、信息密度高、少装饰、政务蓝一类主色。不做互联网产品的花哨动效。
- 界面文案与注释平实、公文式清晰，不堆空话套话，不写成 AI 腔。

## 启动顺序

见 `docs/技术方案-v2.md` §9.4 与 `scripts/`。核心：WSL2（`.wslconfig` 12GB）→ `infra/` 起 Redis+MySQL → Java → Python → 前端。

## 提交

未另定规范前：单模块一次提交，中文 message 一句话讲清改动。
