# infra

Redis 与 MySQL 编排，统一在 WSL2 内运行。

## .wslconfig（先配）

`C:\Users\<user>\.wslconfig`：

```ini
[wsl2]
memory=12GB
processors=4
swap=4GB
```

改完执行 `wsl --shutdown` 再重启 WSL2。

## 启动

```bash
cd infra
docker compose up -d
```

- Redis：127.0.0.1:6379（AOF 持久化）
- MySQL：127.0.0.1:3306，库 `resv`，账号 `resv/resv123`
- 首启会执行 `init/` 下的 SQL（建库表）

## 初始化 SQL

`init/` 目录放建表脚本，按文件名顺序执行。表结构随 Java 端落地补充。
