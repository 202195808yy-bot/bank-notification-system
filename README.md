# 银行客户自动通知服务 · Bank Notification System

事件驱动的银行客户通知系统：按 **事件类型 × 客户偏好 × 模板语言** 决定投递，渠道为 SMS / Email / Push，
并在网页通知中心（铃铛）里可见、可听、可重试。课程设计（КП）项目，前后端同仓。

需求基线：`Сервис автоматического уведомления клиентов банка - КП.docx`（§2.1/§2.2/§3/§4 + 附录 В）。

## 文档

| 文件 | 内容 |
| --- | --- |
| [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) | 总览：系统做什么、架构图、端口与启动、端到端验证配方、已知限制（按 PRD 号）、逐轮改动台账 |
| [`docs/FRONTEND_DOCUMENTATION.md`](docs/FRONTEND_DOCUMENTATION.md) | 前端：路由/角色矩阵、页面、状态与 SSE/声音链路、两套语言体系、i18n 约定 |
| [`docs/BACKEND_DOCUMENTATION.md`](docs/BACKEND_DOCUMENTATION.md) | 后端：模块与端口、实体与表、REST 契约、事件→通知全链路、Kafka 主题与 DLT、配置契约 |

## 目录结构

```
web/       React 18 + antd 5 + Vite 8 单页应用（ru/zh/en，默认 ru），nginx 起在 :1111
backend/   Spring Boot 3.1.6 / Java 17 多模块 Maven + docker-compose（11 个容器）
docs/      上面三份文档
```

## 快速启动

```bash
cp backend/.env.example backend/.env     # 填 JWT_SECRET / INTERNAL_API_TOKEN / POSTGRES_PASSWORD（+ 需要真发时填 MAIL_*、SMS_*）
cd backend
mvn -B package -DskipTests               # 后端 Dockerfile 只 COPY target/*.jar，必须先在宿主打包
docker compose up -d --build             # 11 个容器
# 界面 http://localhost:1111 · API 入口 http://localhost:8080
```

只改前端：`cd web && npm run build`，再 `cd ../backend && docker compose build frontend && docker compose up -d --no-deps --force-recreate frontend`。

## 端口

| 宿主端口 | 组件 | 说明 |
| --- | --- | --- |
| 1111 | frontend (nginx) | 界面入口，`/api` 反代到网关，`/api/notifications/stream` 不缓冲（SSE） |
| 8080 | api-gateway | 唯一对外 API 入口：JWT 校验、角色判定、注入 `X-User-Id`/`X-User-Role` |
| 127.0.0.1:8081~8085 | customer / notification / template / channel / event-adapter | 只绑本机，内部接口另需 `X-Internal-Token` |
| 127.0.0.1:15432 / 16379 / 9092 | postgres / redis / kafka | 只绑本机 |

## 配置与安全约定

- **`backend/docker-compose.yml` 里没有任何密钥字面量。** 三个服务密钥一律写 `${VAR:?说明}`（库口令由 `x-db-pass` 锚点
  从 `POSTGRES_PASSWORD` 同源分发），值只在 `backend/.env`（已 gitignore）；缺键时 compose 直接报错退出，
  而不是带着空 JWT 密钥起来（空密钥 = 任何人都能自签 ADMIN 令牌）。
- ⚠️ 例外：容器外开发（IDEA / `mvn spring-boot:run`）走的 `application.yml` 里，`JWT_SECRET`、`internal.api.token`
  与 `DB_PASS` 仍带 **dev 默认值**。用 compose 起服务时这三个键一定被 `.env` 覆盖，不受影响；但如果绕开 compose 直接跑服务，
  必须自己把这三个环境变量设成非默认值，否则等于用仓库里公开可查的密钥签发令牌、用公开口令连库。
- 渠道凭据（`MAIL_*`、阿里云 `SMS_*`）**只有 `.env` 这一处能生效**，写进 yml / compose 都不会被读到。
- `backend/docker-compose.yml` 顶部钉了 `name: bank-notification-backend`：命名卷前缀就是 compose 项目名，
  删掉这一行等于换一套空数据卷（表现是"库被清空"）。
- 事件入口 `POST /api/events` 与直发能力仅 ADMIN；SSE 订阅表在进程内存，所以 notification-service 只能单实例。

## 本地开发注意

`backend/` 的历史与 `web/` 的历史都保留在同一个仓库里（合并时用的是 `merge -s ours` + `read-tree --prefix`，没有压缩、没有改写）：

```bash
git log --oneline --graph          # 两条线都在
git log -- backend/docker-compose.yml
```
