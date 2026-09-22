# 项目总览 · 银行客户自动通知服务

> **总文档**。仓库合并为前后端同仓后，文档一共三份，都在 `docs/`：
> - 本文件 = 系统是什么、怎么跑起来、端到端怎么验、还带着哪些已知限制、逐轮改了什么
> - `FRONTEND_DOCUMENTATION.md` = `web/` 细节（页面/状态/i18n/SSE/声音）
> - `BACKEND_DOCUMENTATION.md` = `backend/` 细节（模块/表/主题/渠道/配置契约）
>
> ⚠️ 早先的逐轮台账 `PROJECT_DOCUMENTATION.md`（需求 + §6 缺陷记录 + §7 验证清单）与
> `PRODUCT_CODE_REVIEW.md`（PRD 判定）在写本文件时按路径引用过，但**当前磁盘上并不存在**，
> 两个仓库的 git 历史里也从没有过它们（`git log --all --name-only` 查无此文件）。
> 也就是说那两份文件不是"没写全"而是"没留下"。PRD-01~PRD-55 的判定依据仍在
> 需求基线（上级目录 `Сервис автоматического уведомления клиентов банка - КП.docx`
> §2.1/§2.2/§3/§4 + 附录 В），逐轮改动记录改由本文件 §6 承接。

---

## 1. 系统做什么

给银行客户按**事件 + 偏好 + 模板**自动发通知，渠道为 SMS / Email / Push，并在网页通知中心（铃铛）里可见、可听、可管理。

| 能力 | 落点 |
| --- | --- |
| 6 类事件（交易/风控/账单/登录/安全/促销）驱动的自动通知 | `event-adapter` → `notification-service` |
| 按事件类型 × 渠道的订阅开关、免打扰时段（按客户 IANA 时区） | `customer-service` 偏好 |
| 模板按 `(event_type, channel, locale)` 三元组渲染，54 套入库，缺语言回退默认 `zh_CN` | `template-service`（读缓存 + 写侧失效；两份"Redis 预热"里删掉了不生效的那份，留下的一份只是启动探活） |
| 渠道投递 + 失败原因码化（`notifications.reason`，30+ 码，三语言文案） | `channel-service` |
| 通知中心：列表/未读数/已读/重试/实时（SSE + 15 秒轮询兜底）/提示音 | 前端 + `NotificationStreamService` |
| 管理员：模板管理、事件发送（批量勾选客户 / 手填地址直发）、全行统计 | `/admin/templates`、`/admin/events` |
| 手动重试（上限 3 次）、毒丸消息进 DLT 不卡分区 | `POST /api/notifications/{id}/retry`、`*.DLT` |

**角色**：`USER` 只能看/管自己的通知与偏好；`ADMIN` 另有模板、事件发送、全行统计、用户目录，且**只有 ADMIN 能 `POST /api/events`**（PRD-51）。

---

## 2. 一图看懂

```
浏览器 ──:1111── nginx（SPA 静态 + /api 反代；/api/notifications/stream 不缓冲）
                     │
                 :8080 api-gateway ── JWT 校验、角色判定、注入 X-User-Id / X-User-Role
                     │
   ┌─────────────────┼──────────────────────┬───────────────────┐
:8081 customer    :8082 notification     :8083 template     :8085 event-adapter
 档案/偏好/预检     派发/落库/状态/SSE        模板 CRUD         /api/events → Kafka
                     │                                        │
                     └────── Feign ── customer(/internal)      │
                                                                ▼
                            Kafka  bank.events → 派发 → notification.send.command
                                                      → channel-service :8084
                                                        (SMS 阿里云 / SMTP / Push-mock)
                                                      → notification.status → 终态回写 → SSE
                     │
              postgres(:15432) 5 张表 · redis(:16379) pref:/template:/dedup:
```

**异步边界即用户可见边界**：`POST /api/events` 走同步确认，Kafka 投不进去直接返 5xx（PRD-05①），不会"界面说成功、其实没排队"。

---

## 3. 端口与启动

| 宿主端口 | 容器 | 说明 |
| --- | --- | --- |
| **1111** | frontend(nginx) | 界面入口 |
| **8080** | api-gateway | 唯一对外 API 入口 |
| 127.0.0.1:8081~8085 | 5 个业务服务 | 只绑本机，便于排障 |
| 127.0.0.1:15432 / 16379 / 9092 | postgres / redis / kafka | 只绑本机 |

```bash
cp backend/.env.example backend/.env    # 首次：填 JWT_SECRET / INTERNAL_API_TOKEN / POSTGRES_PASSWORD
cd backend
mvn -B package -DskipTests              # Dockerfile 只 COPY target/*.jar，必须先在宿主打包
docker compose up -d --build            # 11 个容器（compose 文件在 backend/，前端构建上下文是 ../web）
# 界面 http://localhost:1111 · 网关 http://localhost:8080
docker exec bank-postgres psql -U postgres -d bank_notifications -c 'select ...'
```

只改前端：`cd web && npm run build && cd ../backend && docker compose build frontend && docker compose up -d --no-deps --force-recreate frontend`。
渠道凭据与服务密钥都只从 `backend/.env` 读（键名清单与填写要求见 `backend/.env.example`）；
compose 里不留任何字面量密钥，缺键时 `docker compose up` 直接报错退出（PRD-27 本轮关闭）。

⚠️ 两个"看着像 bug"的坑，都在这里记一笔：
1. **compose 顶部钉了 `name: bank-notification-backend`**。命名卷的前缀就是项目名，
   现存数据卷叫 `bank-notification-backend_pgdata`。删掉这一行（或改项目名）会让 compose
   另起一个空卷，表现是"库被清空了"——旧卷其实还在，只是没挂上。
2. **`POSTGRES_PASSWORD` 只在卷第一次创建时生效**。卷已存在时改 `.env` 里的口令不会同步进
   数据库，只会连不上；确实要换就进容器 `ALTER USER postgres PASSWORD '...'`。


---

## 4. 端到端验证配方（最短路径）

1. **注册 + 提管理员**（`role` 写在 JWT 里，提升后**必须重新登录**，否则仍是 USER，`POST /api/events` 会 403）
   ```bash
   curl -s -XPOST localhost:1111/api/auth/register -H 'Content-Type: application/json' \
     -d '{"name":"QA","email":"qa@bank.com","phone":"+79000000001","password":"Qa135246@","timezone":"Europe/Moscow","locale":"ru_RU"}'
   docker exec bank-postgres psql -U postgres -d bank_notifications -c "update customers set role='ADMIN' where email='qa@bank.com';"
   ```
   ⚠️ QA 邮箱用 `@bank.com` 占位域，别投到真实邮箱。
2. **建偏好**（`PUT /api/preferences`，多渠道可任意组合；空渠道会 400）
3. **发事件**：`POST /api/events`（ADMIN）→ 3~5 秒内按渠道各落一行 `PENDING → SENT/FAILED`
4. **看结果**：`select id,channel,status,reason,recipient from notifications order by id desc limit 10;`
5. **看界面**：1440×900 以上视口才测得到桌面布局缺陷（内置浏览器面板 532px 会漏判），
   统一用 Playwright chromium 探针。探针的两条前提：
   ① `ctx.route('**/api/events', stub)` 拦掉写入，这样 UI 全流程走完而不落库
   （前后对比 `select max(id), count(*) from notifications` 必须一模一样）；
   ② 不猜用户口令——用 `backend/.env` 里的 `JWT_SECRET` 离线自签一张 ADMIN 令牌注入
   `localStorage`，登录页那一路则保留一次"真填错密码"来验 401 文案。
   `page.evaluate` 里避免链式箭头表达式（本会话里它被 Windows 端的引号处理截断过一次，
   报 `Unexpected token ')'`），改成普通 `for` 循环。
6. **验直发权限**：客户令牌 `POST /api/events` 必须 403，再演示直发
7. 逐轮改动记录见本文件 §6；缺陷编号 N##、需求编号 PRD## 在三份文档里通用


---

## 5. 已知限制（按 PRD 号，勿当 bug 重复报）

| PRD | 现状 | 影响 |
| --- | --- | --- |
| PRD-27 | compose 已无任何字面量密钥，三个服务密钥改为 `${VAR:?}` 从 `backend/.env` 读 | 已关闭（本轮）。但**git 历史里仍留有 dev 值**（`my-secret-key-for-bank-notification`、`123456`、`dev-internal-api-token-2026`），推到公网仓库后建议换掉 `backend/.env` 里这三个值；真实的阿里云/SMTP 凭据从头到尾只在 `.env`，从未入库 |
| PRD-49 | SSE 订阅表在进程内存 | notification-service **只能 1 副本**；扩容要换 Redis 发布订阅 |
| PRD-50 | 阿里云单变量模板 20 字 < 最短正文 22 字 | 真实 SMS 目前 100% `SMS_CONTENT_TOO_LONG`；PUSH/EMAIL 不受影响 |
| PRD-51 | 直发仅 ADMIN；`/api/events` 客户 403 | 演示前重测 403 这条 |
| N11 | `POST /api/callback/**` 网关只路由未鉴权 | 遗留，需补 internal token 或收口 |
| — | `sent_logs` 无查询接口 | 只能进库看 |
| — | 无 Flyway，`ddl-auto: update`，且**库内 0 外键** | 表结构靠实体推导；删父行必须显式删子行 |
| — | `SMS_EVENT_TEMPLATE_<TYPE>` 6 个键默认留空 | 设计如此，等厂商模板号 |
| N74 | 登录/注册错误改回 `{code}`，前端按当前语言解析 | 本轮已修：俄文界面不再弹「邮箱或密码错误」，实测显示 «Неверный e-mail или пароль» |
| — | `web/deploy.ps1` 是 CRA 时代的手工部署脚本（端口 3000、找 `build/`），与 compose 流程冲突且必然失败 | 被 compose 取代但文件仍在，未被任何流程引用；删不删等你点头（不是代码，删文件不可逆） |


---

## 6. 追加轮十六（本轮）干了什么

| 事项 | 结果 |
| --- | --- |
| 读完两个仓库代码 | 前端 `src/` 7039 行 33 文件；后端 7 模块 4620 行 Java。逐文件盘点冗余 |
| 清前端冗余 | 删死函数/死常量/未用导入/未引用关键帧/死 `.env`/死 vite alias；合并事件发送页两份重复批量循环；`useTemplateStore` 改吃 `templateApi`；i18n 死键 46×3 语言=138 行删除（删后三语言各 344 键、0 缺失） |
| 清后端冗余 | 删 6 个死类/死文件（含 213 行无引用的 `BankEventSimulator`、与生产类同名的空测试类、必然 401 的 `send-event.sh`）；重复 `ObjectMapperConfig` 并入 `common/JacksonConfig`；未用 `openConnections` 与 3 处未用 import 删除；空的常绿 JWT 测试重写成 3 个真用例；2 个 pom scope 纠正；1 个 Dockerfile `EXPOSE` 纠正；补 `.env.example` |
| 顺手修掉三处潜伏缺陷 | ① 铃铛骨架的 `animation: spin` 全仓无此关键帧（永远不转）→ 已补；② `SMS_TIMEOUT_MS` yml 读、compose 没传（文档旋钮空转）→ 已接线；③ `localStorage.locale` 无白名单，写进后端的 `zh_CN` 会**整页白屏** → 已校验 |
| N74：俄文界面弹中文报错 | 后端 `AuthService` 改成只回错误码（口令错与账号不存在共用 `INVALID_CREDENTIALS`，避免变成邮箱枚举口），`AuthController` 回 `{code}`；前端把三处各自的"取哪句报错"收敛成 `i18n` 的 `errorText(code)`，补 `error.code.INVALID_CREDENTIALS` 三语。实测：俄文界面显示 «Неверный e-mail или пароль» |
| 删库内测试数据 | 删前 701 通知 / 566 回执 / 26 偏好 / 12 客户 → 删后 **633 / 497 / 11 / 3**；模板 54 不动。删除对象：`qa14-*`/`qa15-*` 临时客户（id 8~18）及其偏好/通知/回执，以及 `event_id ~ '^(p15|p14|qa|pf|probe|tmp|test)'` 的行。⚠️ 当时说"CSV 备份留在 `qa-backup/deleted-2026-09-22-cleanup/`"——**那个目录现在磁盘上找不到，全仓也没有任何 .csv**（`find . -iname '*.csv'` 为空），所以那批行**不可回滚**，只有删除前后的计数可核对。当前计数已不是 633/497：见本节末"数据现状" |
| 三份新文档 | 本文件 + `FRONTEND_DOCUMENTATION.md` + `BACKEND_DOCUMENTATION.md`，现都在 `docs/` |
| 前后端合仓（本轮收尾） | 见 §7 |
| 验证 | `mvn -B test` 全绿（含新 JWT 3/3）；`npm run build` 通过；6 镜像重建、11 容器 healthy；Playwright 1440×900 回归：三语言渲染 + 0 `Missing message` + 两发送入口请求体一致（`bodies_equal_for_same_customer=true`），并且探针拦掉 `POST /api/events` 做到零写入（跑前跑后 `max(id)=930 rows=633` 一致）。⚠️ 探针脚本没留下文件，方法记在 §4 第 5 步 |

**数据现状（合并时复核）**：`notifications 646 / max(id) 943`、`sent_logs 510`、
`notification_preferences 11`、`customers 3`（id 1 `1@qq.com` USER、id 6 `admin@bank.com` ADMIN、
id 7 `202195808@qq.com` USER）、`notification_templates 54`。
比清理后的 633/497 多出的 13 条是 id 931~943 的 `evt-*` 行（客户 6、7 在界面上点出来的真实历史：
SMS 全部 `SMS_CONTENT_TOO_LONG`/`SMS_SEND_FAILED`，EMAIL SENT，一条 `PUSH_MOCK_SEND`），
属演示数据而不是测试垃圾，保留。

**保留未动（第三档，供你决定）**：
1. 剩下的 `evt-*`/uuid 通知是**你自己三个账号**（`1@qq.com`、`admin@bank.com`、`202195808@qq.com`）在界面上点出来的真实历史，删了就没有可演示的通知中心了 ⇒ 保留。要清空我可以再给一份带备份的 DELETE（这次会把 CSV 真留在仓库外的目录里，并当场核对文件存在）。
2. 仓库根下的 `bank-notification-web.zip`（198K，5 月的仓库自我快照）与 `bank-notification-web.iml` —— 都在 `.gitignore` 内、不属仓库内容，是本地遗留物；`web/deploy.ps1` 则**已经入库**（它跟 `.gitignore` 一样是历史跟踪文件），内容是 CRA 时代的手工部署脚本：找 `build/`、映射端口 3000、`docker run --network bank-network`，与现在的 Vite + compose 流程全不一致，跑必然失败，但没有任何流程引用它。这三个要不要一起清，等你点头（不是代码，且删文件不可逆）。
3. 早先引用过的 `PROJECT_DOCUMENTATION.md:816`（讲 `notification-service/ObjectMapperConfig` 已并入 `common/JacksonConfig`）随那份台账一起不存在了，这条前向说明就地作废；本轮的对应记录就在上面"清后端冗余"一行里。

---

## 7. 仓库结构（前后端合并之后）

一个仓库两套代码，历史都保留、没有压缩改写：

```
bank-notification-system/         # 远端：github.com/202195808yy-bot/bank-notification-system
├── .gitignore                    # 仓库根：依赖/产物/IDE/.env
├── docs/                         # 本文档 + 前端文档 + 后端文档
├── web/                          # 原前端仓库整体 git mv 进来（45 个跟踪文件，rename 可追溯）
│   ├── Dockerfile  nginx.conf  vite.config.ts  package.json
│   └── src/
└── backend/                      # 原后端仓库以子树方式并进来（117 个跟踪文件）
    ├── docker-compose.yml        # build context = ../web
    ├── pom.xml + 7 个模块
    └── .env                      # 只在本地，.gitignore 内
```

- 合并手法：`git merge -s ours --no-commit --allow-unrelated-histories backend/master`
  → `git read-tree --prefix=backend/ -u backend/master` → 一次两父提交。
  两侧原始提交都还是这个 HEAD 的祖先，`git log --oneline --graph` 看得见分叉。
- 看某一侧的完整历史：`git log --follow backend/docker-compose.yml`、`git log -- web/`。
- 后续用 `git subtree pull --prefix=backend <后端远端> master` 仍可继续并（前提是你还留着那个独立后端仓库）。
- 本地遗留：合并前那个独立的 `bank-notification-backend/` 目录被改名为
  `bank-notification-backend.pre-merge/`（就在本仓库同级，没有被删；确认新位置可用后可自行删除）。
  它的 `.env` 已复制到 `backend/.env`，历史已在本仓库里。
  仓库目录名目前仍是 `bank-notification-web`——IDE 占着句柄改不动名，对 git 与远端没有任何影响，
  想统一成 `bank-notification-system` 的话关掉 IDE 再 `mv` 即可。

