# 后端文档 · `backend/`

> 追加轮十六新增文档。范围：`backend/`（原 `bank-notification-backend` 仓库，Spring Boot 3.1.6 / Java 17 多模块 Maven + docker-compose）。
> 口径：只写代码里真实存在的东西；端口、主题、原因码均经运行中容器与库实测核对。
> 相关：整体流程见 `PROJECT_OVERVIEW.md`，前端见 `FRONTEND_DOCUMENTATION.md`。

---

## 1. 模块与端口

| 模块 | 端口 | 职责 | Java 文件 / 行 |
| --- | --- | --- | --- |
| `api-gateway` | 8080 | 唯一对外入口：JWT 校验、角色判定、注入 `X-User-Id`/`X-User-Role`、路由 | 3 / 162 |
| `customer-service` | 8081 | 注册登录、客户档案、通知偏好、送达面预检、内部联系人查询 | 29 / 1400 |
| `notification-service` | 8082 | 事件派发（偏好/模板/免打扰/去重）、通知落库、状态回写、SSE | 17 / 1581 |
| `template-service` | 8083 | 模板 CRUD + 启动时探一次 Redis 连通性（`RedisWarmupRunner` 只 set/delete 一个 `_warmup` 键，**不是**缓存预热） | 7 / 470 |
| `channel-service` | 8084 | 渠道投递：SMS / Email / Push；回执 `sent_logs` | 12 / 988 |
| `event-adapter` | 8085 | `POST /api/events` → 归一化 `BankEvent` → 投 Kafka | 4 / 258 |
| `common` | — | 实体、枚举、DTO、常量、Jackson、内部接口鉴权过滤器 | 18 / 759 |

`docker-compose.yml` 就在本目录（11 个容器：zookeeper、kafka、postgres、redis、6 个服务、frontend）。
**端口暴露**：除网关 `8080:8080` 与前端 `1111:80` 外，所有服务与中间件都绑到 `127.0.0.1`（如 `127.0.0.1:8081:8081`、`127.0.0.1:15432:5432`），外部网络不可达（PRD-01）。

构建：各服务 Dockerfile 都是 `COPY target/*.jar`，所以 **`mvn -B package -DskipTests` 先跑，再 `docker compose build`** 才快。

---

## 2. 数据模型

无 Flyway：DDL 由 `ddl-auto: update` 生成；4 个业务服务都 `@EntityScan("com.bank.common.entity")`，所以**每个服务都会建全套 5 张表**（谁先起谁建）。
⚠️ 实测 `pg_constraint` 返回 0 行：**库里没有任何外键约束**，删父行不会级联，子行必须显式删（本轮清库就是按这个前提做的）。

| 实体（`common/entity`） | 表 | 关键列 |
| --- | --- | --- |
| `Customer` | `customers` | name, email, phone, password, role(USER/ADMIN), timezone, locale, **push_token**, account |
| `Notification` | `notifications` | event_id, customer_id, channel, recipient, subject, content, status, **reason**, read_at, created_at |
| `NotificationPreference` | `notification_preferences` | customer_id, event_type, channels(逗号串，`StringListConverter`), enabled, quiet_start/end |
| `NotificationTemplate` | `notification_templates` | event_type, channel, locale, subject, body；库内 54 套 |
| `SentLog` | `sent_logs` | 渠道回执流水（**无查询接口**，只能进库看） |

枚举（`common/enums`）：`EventType` = TRANSACTION / RISK_ALERT / BILL / LOGIN / SECURITY / PROMOTION；`ChannelType` = SMS / EMAIL / PUSH；`SendStatus` = PENDING / SENT / FAILED / SKIPPED / FAILED_VALIDATION。
`AppConstants.SUPPRESSED_CHANNEL = "NONE"`：事件被整体抑制时 `channel` 列的占位值（该列 NOT NULL 且参与 `(event_id, customer_id, channel)` 唯一键）。

---

## 3. REST 接口（对外，均经网关）

| 方法 + 路径 | 服务 | 角色 |
| --- | --- | --- |
| `POST /api/auth/register`、`/api/auth/login` | customer | 公开 |
| `GET`/`PATCH /api/customers/me` | customer | 登录 |
| `GET /api/customers` | customer | **ADMIN**（用户目录，事件发送页用） |
| `GET /api/preferences`、`PUT /api/preferences` | customer | 登录 |
| `GET /api/preferences/reach?eventType=` | customer | ADMIN（送达面预检，PRD-41） |
| `GET /api/templates`、`POST /api/templates`、`PUT`/`DELETE /api/templates/{id}` | template | ADMIN |
| `GET /api/notifications`、`/unread-count`、`/stats` | notification | 登录（本人范围） |
| `GET /api/notifications/stats/all` | notification | **ADMIN** |
| `PATCH /api/notifications/{id}/read`、`/read-all` | notification | 登录 |
| `POST /api/notifications/{id}/retry` | notification | ADMIN（手动重试，上限 `MAX_RETRY_ATTEMPTS=3`） |
| `GET /api/notifications/stream` | notification | 登录（SSE，`text/event-stream`） |
| `POST /api/events` | event-adapter | **ADMIN**（PRD-51；普通用户 403） |
| `POST /api/callback/{channel}/{provider}` | channel | ⚠️ 网关只路由未鉴权（N11 遗留） |

内部接口 `/internal/customers/{id}/contact` 等在 8081/8082 上，走 `common/config/InternalApiAuthFilter` + `INTERNAL_API_TOKEN`，且端口不对公网暴露。

错误契约：`{code, message}`，`code` 与前端 `error.code.<CODE>` 一一对应。

---

## 4. 事件 → 通知 全链路

```
POST /api/events (ADMIN)
  → EventTransformer 归一化为 BankEvent
  → EventProducer → topic bank.events        【同步确认：投不进 Kafka 直接 5xx，PRD-05①】
  → BankEventListener → NotificationDispatchService.dispatch
        1. 查偏好（Feign PreferenceClient，Redis `pref:`）
        2. 抑制判定 → NOT_SUBSCRIBED / PREFERENCE_DISABLED / NO_CHANNEL
        3. 免打扰时段（按 customer.timezone，IANA 名）→ SKIPPED + QUIET_PERIOD
        4. 查联系人（Feign ContactClient /internal/customers/{id}/contact）
             → CONTACT_UNAVAILABLE / NO_PHONE / NO_EMAIL / NO_PUSH_TOKEN
        5. 渲染模板（TemplateRenderService：Redis `template:` → 回落 REST，
             按 customers.locale 取，缺语言回退默认 zh_CN）
             → TEMPLATE_MISSING / VARIABLE_MISSING(FAILED_VALIDATION)
        6. 去重窗口 Redis `dedup:`（1 小时）
  → 每个渠道一行 notifications(PENDING) → SendCommandProducer → topic notification.send.command
  → channel-service：SmsSender / EmailSender / PushSender
  → StatusProducer → topic notification.status
  → StatusConsumer 回写终态（SENT / FAILED / *_MOCK_SEND 等）
  → NotificationStreamService.publish → SSE（只推终态）
```

**Kafka 主题与 DLT**（`AppConstants`）：`bank.events`、`notification.send.command`、`notification.status`；脏消息由 `DeadLetterPublishingRecoverer` 显式解析到 `<topic>.DLT`（PRD-42：加了 `ErrorHandlingDeserializer`，毒丸不再卡分区）。

**直发路径**（`dispatchDirect`）：刻意**不查偏好、不查档案、不判免打扰** —— 查了就会出现"地址填对了但被 NOT_SUBSCRIBED 挡掉"。缺原因码 `DIRECT_CHANNEL_MISSING` / `DIRECT_RECIPIENT_MISSING`。

⚠️ Redis 偏好缓存 + `customers.id` 序列会跳号：手改 SQL 之后再测容易命中旧缓存或错 id（本轮清库前先 `DEL pref:*` 的习惯保留）。

---

## 5. 渠道实现要点（channel-service）

| 渠道 | 真发 | 模拟 | provider 标签 |
| --- | --- | --- | --- |
| SMS | 阿里云 SendSms（`SmsSender`） | `SMS_MOCK_SEND` | 如实标 `MOCK` / 真实厂商（N53） |
| Email | Spring JavaMailSender，465 隐式 TLS 自动对齐（`MailSocketModeConfig`，N58） | `MAIL_MOCK_SEND` | 同上 |
| Push | 无真实网关 | `PUSH_MOCK_SEND` | `MOCK` |

- 失败率可配：`MOCK_FAILURE_RATE`（默认 0.1）。要可信成功率数字时设 0。
- 厂商返回码经 `ProviderRejectException` 映射为 `SMS_*` / `MAIL_*` 原因码写进 `notifications.reason`（N52）：`SMS_AMOUNT_ENOUGH 类`、`SMS_CONTENT_TOO_LONG`、`MAIL_CREDENTIAL_INVALID` 等。
- **当前实测约束（PRD-50）**：阿里云单变量模板上限 20 字，而通知正文最短 22 字 ⇒ 真实 SMS 100% 记 `SMS_CONTENT_TOO_LONG`。已通过多变量模板方案绕开，但要真发短信仍需厂商侧模板对齐。
- 库内现存原因码分布（清理后 633 行）：`TEMPLATE_MISSING` 84、`NOT_SUBSCRIBED` 53、`SMS_CONTENT_TOO_LONG` 51、`PREFERENCE_DISABLED` 22、`MAIL_SEND_FAILED` 22、`NO_PUSH_TOKEN` 22、`MAIL_CREDENTIAL_INVALID` 14、`QUIET_PERIOD` 11、`PUSH_MOCK_SEND` 9、`MAIL_MOCK_SEND` 6，另有零星 `VARIABLE_MISSING`/`NO_CHANNEL`/`SMS_RECIPIENT_INVALID`。

---

## 6. SSE 实时通道（notification-service）

`NotificationStreamService`：
- **只推终态**（SENT / FAILED / SKIPPED / FAILED_VALIDATION），不推 PENDING —— 派发行落库即 PENDING，实测 7~25 ms 后才回写 SENT；推 PENDING 会让提示音在"消息还没发出去"那一刻响。
- 负载只带 `notification_id / status / channel / event_type / reason`，不带正文：界面仍以 REST 为唯一口径。
- 15 秒心跳注释帧（`@Scheduled(fixedRate=15000)`）防 nginx/网关掐连接；前端 `location = /api/notifications/stream` 单独配 `proxy_buffering off` + 1 小时读超时。
- ⚠️ 订阅表在进程内存 ⇒ **只能单实例**（PRD-49）；横向扩容要换 Redis 发布订阅。
- 客户端断开是常态，走 `log.debug` + 注销，不刷 WARN。

---

## 7. 配置契约

| 来源 | 内容 | 备注 |
| --- | --- | --- |
| `docker-compose.yml` | 只剩 `${VAR:?说明}` / `${VAR:默认}` 引用，**没有任何字面量密钥**；另有 `DB_*`/`REDIS_*`/`KAFKA_*` 这类容器网络内部地址 | 顶部钉了 `name: bank-notification-backend`：命名卷前缀就是项目名，删这行等于换一套空卷（表现是"库被清空"） |
| `backend/.env`（**不入仓**） | `JWT_SECRET`、`INTERNAL_API_TOKEN`、`POSTGRES_PASSWORD` + `MAIL_*`、`SMS_*`、`MOCK_FAILURE_RATE` | 本轮 PRD-27 关闭：三个服务密钥从 compose 搬到这里。渠道凭据**只有这里能生效**（N51） |
| `.env.example` | 上述键的**名字**模板（值一律留空） | 本轮补齐了三个服务密钥一节，含"必须换成自己的随机串"与换口令的坑 |
| 各服务 `application.yml` | 结构 + `${VAR:default}` | 不再放凭据字面量；`internal.api.token` 的默认值只在容器外开发时生效，与 `.env` 不一致会让内部接口一律 401 |

本轮补的接线缺陷：`SMS_TIMEOUT_MS` 在 yml 里读、compose 却没传 ⇒ 文档里的旋钮一直空转；现已在 `channel-service` 环境加 `SMS_TIMEOUT_MS: ${SMS_TIMEOUT_MS:-8000}`。

为什么用 `${VAR:?说明}` 而不是 `${VAR:-默认}`：JWT 密钥一旦为空，签发和校验都用空密钥，
任何人都能自签一张合法 ADMIN 令牌。用 `:?` 让这种部署错误**在启动前**炸掉，而不是带着空密钥起来。
实测两条：`docker compose config` 三个键都能插值；`--env-file` 指向空文件时 compose 按预期失败并报出中文说明。

`.env.example` 覆盖：三个服务密钥 + `MAIL_*`（含 `MAIL_STARTTLS_ENABLE`/`MAIL_SSL_ENABLE`/`MAIL_FROM`）、
`SMS_*`（含 6 个 `SMS_EVENT_TEMPLATE_<TYPE>`、`SMS_MAX_VARIABLE_LENGTH`、`SMS_TIMEOUT_MS`）、`MOCK_FAILURE_RATE`。
⚠️ `POSTGRES_PASSWORD` 只在数据卷**第一次创建**时生效：卷已存在时改它不会同步进数据库，只会连不上；
确实要换就进容器 `ALTER USER postgres PASSWORD '...'`。


---

## 8. 追加轮十六清理记录（后端）

| 项 | 动作 | 依据 |
| --- | --- | --- |
| `event-adapter/cli/BankEventSimulator.java` | 删（213 行） | 无引用；硬编码 `localhost:9092`；带 `spring.json.trusted.packages=*` |
| `event-adapter/src/test/.../EventController.java` | 删（4 行空类） | 与生产类同名，遮蔽 |
| `scripts/send-event.sh` | 删 | 不带 `X-Internal-Token`，今天必然 401 |
| `api-gateway/.../application.properties` | 删（1 行） | 与 yml 重复 |
| `template-service/config/RedisWarmupConfig.java` | 删 | 与 `RedisWarmupRunner` 同一件事的两份实现（都是启动时探一次 Redis）。**更正**：对应提交信息里写的"预热从未执行"说重了——被删的那份确实不生效，但留下来的 Runner 会执行，只是它做的只是连通性探测、不是模板缓存预热，所以"预热"这个名字本身言过其实，功能上可留可删 |
| `notification-service/config/ObjectMapperConfig.java` | 删 | 与 `common/JacksonConfig` 逐字节相同；改由 `@Import({JacksonConfig.class, ...})` |
| `NotificationStreamService.openConnections()` | 删 | 无 actuator，无消费者 |
| 3 处未用 import（`CustomerController`、`CustomerDirectoryController`、`InternalClientConfig`） | 删 | 全仓扫"import 的类型名在该文件中只出现一次"，命中即这三处（扫描脚本没留存，判据可复述） |
| `JwtTokenProviderTest` | 重写 | 原为空的常绿 `@Test`；现为 3 个真用例（签发校验 / 过期 / 换密钥），3/3 通过 |
| `api-gateway` yml `jwt.expiration: 86400000` | 删 | 无处读取（真实值来自 `AppConstants.JWT_EXPIRATION_MS`） |
| `customer-service/pom.xml` | `spring-boot-starter-test` → `<scope>test</scope>` | 此前把测试库漏进运行期类路径 |
| `template-service/pom.xml` | `postgresql` → `<scope>runtime</scope>` | 同上 |
| `template-service/Dockerfile` | `EXPOSE 8081` → `8083` | 与 `server.port` 对齐；6 个 Dockerfile 已全部核对 |
| `common/.../AppConstants.java` | **保留** | Kafka/REST 线格式字段、compose/env 键、Spring 反射装配点属契约层冗余，删了是破坏契约且零功能收益 |

**验证**：`mvn -B test` 全模块 BUILD SUCCESS（含新 JWT 3/3）；6 个镜像重建；11 容器 healthy；`POST /api/events` 普通用户 403 仍成立。
⚠️ 早先那份"逐轮台账"`PROJECT_DOCUMENTATION.md` 在当前磁盘与两个仓库的 git 历史里都查不到，
所以它第 816 行关于 `notification-service/ObjectMapperConfig`（现已并入 `common/JacksonConfig`）的说法
无法就地标注 —— 这条前向说明就写在这里，不再指向那个不存在的位置。

**N74（本轮已修）**：`AuthService` 原先抛 `该邮箱已注册` / `邮箱或密码错误` 这种硬编码中文，
且 `AuthController` 把它塞进 `message` 字段，前端只能原样显示 → 俄文界面弹中文。
现在后端只回**错误码**（`EMAIL_ALREADY_USED` / `INVALID_CREDENTIALS`），响应体是 `{code}`，
译文由前端 `i18n/messages.js` 的 `error.code.*` 按当前语言解析（实测俄文界面显示 «Неверный e-mail или пароль»）。
两个刻意的设计：
1. **口令错与账号不存在共用一个码** —— 分成 `USER_NOT_FOUND` / `BAD_PASSWORD` 就等于开了一个
   "这个邮箱注册过吗"的枚举口，所以宁可合并。
2. `IllegalArgumentException` 的 message 位只放码，不放人类可读文案；文案的唯一来源是前端语料。

