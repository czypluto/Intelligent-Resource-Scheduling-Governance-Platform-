# frontend-vue

Vue 3 + Vite + Element Plus 前端（dev 端口 5173）。视觉按央企/国企信息化平台：政务蓝、信息密度高、少装饰。

## 运行

```bash
npm install
npm run dev
```

## 页面

- 登录页：调 Java `/java-api/auth/login`，Token 存 localStorage。
- 对话页：调 Python `/py-api/chat`（fetch 读 SSE 流），逐步显示 思考/权限校验/抢票/结果；每条消息附当前模型名，便于确认 dev=pro、prod=flash。

## 代理

`vite.config.js`：
- `/java-api/*` -> Java 8080（去前缀，Java 在 Windows 本机）
- `/py-api/*`   -> Python 8000（去前缀，Python 端在 WSL）

Python 端跑在 WSL2（NAT），`vite` 启动时自动用 `wsl hostname -I` 取 WSL 的 IP 作为 `/py-api` 目标；前端也跑在 WSL 内或想手工指定时，设 `PY_BASE=http://127.0.0.1:8000` 覆盖。联调细节见 `../scripts/README.md`。
