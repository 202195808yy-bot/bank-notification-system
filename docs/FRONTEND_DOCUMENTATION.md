# 前端文档 · `web/`

> 追加轮十六新增文档。范围：`web/`（原 `bank-notification-web` 仓库）单页应用（React 18 + antd 5 + Vite 8）。
> 口径：只写**代码里真实存在**的东西；每条结论都能对上下面的"文件:行"。
> 实测的浏览器探针脚本本轮没能留在磁盘上（`find` 查无 `.py`），因此凡是"实测过"的结论，
> 这里连同**复现方法**一起写清楚，而不只给一个对不上号的文件名。
> 相关：整体流程见 `PROJECT_OVERVIEW.md`，后端见 `BACKEND_DOCUMENTATION.md`。

---

## 1. 技术栈与产物

| 维度 | 取值 | 出处 |
| --- | --- | --- |
| 框架 | React 18.2（函数组件 + 自动 JSX 运行时） | `package.json` |
| 构建 | Vite 8 + `@vitejs/plugin-react` | `vite.config.ts` |
| UI 库 | antd 5.29 + `@ant-design/icons` 5 | `package.json` |
| 路由 | react-router-dom 6（`React.lazy` 代码分割） | `src/routes.jsx` |
| 状态 | zustand 5（4 个 store） | `src/store/*` |
| 文案 | react-intl 6（ru/zh/en 三语言） | `src/i18n/*` |
| HTTP | axios（统一实例） | `src/api/axiosInstance.js` |
| 日期 | dayjs | 免打扰时段、时间列 |
| 运行形态 | 构建出静态文件，nginx 容器托管，宿主端口 **1111** | `Dockerfile`、`docker-compose.yml` |

构建脚本只有 `dev / build / preview`（`package.json`）。镜像走 `pnpm install → pnpm build → nginx:alpine`（`Dockerfile`）。

**代码量**：`src/` 共 7039 行（`i18n/messages.js` 占 1039 行，是最大单文件）。

---

## 2. 目录结构

```
src/
  main.jsx                应用入口：ConfigProvider(主题+antd 语言) + I18nProvider + Router
  App.jsx                 仅渲染 <AppRoutes/>
  routes.jsx              路由表 + PrivateRoute（角色守卫）+ 404 兜底页
  index.css               全局样式与关键帧（含本轮补的 @keyframes spin）
  api/
    axiosInstance.js      唯一 axios 实例：注入令牌、统一错误文案、401 跳转
    customerApi.js        当前客户 / 用户目录 / 偏好 / 送达面预检
    templateApi.js        模板 CRUD + 返回体解包
  store/
    useAuthStore.js       登录态（user + token 落 localStorage）
    useNotificationStore  列表 / 未读数 / 铃铛快照 / 声音开关 / 状态迁移判定
    usePreferenceStore.js 偏好读写
    useTemplateStore.js   模板列表（本轮起复用 templateApi）
  components/
    LocaleSwitcher.jsx    全站唯一语言切换器（Header 与登录/注册页共用）
    PreferenceForm.jsx    偏好编辑表单（免打扰时段成对校验）
    Layout/
      Layout.jsx          外壳：Sidebar + Header + 内容区
      Header.jsx          顶栏：横向导航、语言切换、铃铛、静音、用户菜单
      Sidebar.jsx         侧栏（可折叠）
      NotificationDropdown.jsx  铃铛面板（列表 + 已读 + 骨架）
      menu.js             全站唯一导航来源（roles / inHeader 元数据）
  pages/                  8 个页面（见 §5）
  utils/
    constants.js          事件类型 / 渠道 / 状态 / 原因码 / 主题色 / 通知语言
    labels.js             enumLabel：把枚举键映射到 enum.<group>.<key>
    formatters.js         服务端 Instant 解析 + 按语言格式化日期时间
    notificationStream.js SSE 客户端（fetch + ReadableStream 自解帧）
    notifySound.js        WebAudio 提示音 + 静音持久化
  i18n/
    index.jsx             IntlProvider + 语言白名单
    messages.js           三语言语料（每语言 344 键）
```

---

## 3. 导航与路由（单一来源）

`components/Layout/menu.js` 的 `MENU_ITEMS` 是**全站唯一**的导航定义，Sidebar、Header 横向导航、移动端抽屉都从它派生（历史上三处各写一份，抽屉只有 3 项，手机上管理员进不去模板/事件页）。

| 路由 | 页面 | 可见角色 | 进 Header | 文件 |
| --- | --- | --- | --- | --- |
| `/login` | LoginPage | 公开 | — | `pages/LoginPage.jsx` |
| `/register` | RegisterPage | 公开 | — | `pages/RegisterPage.jsx` |
| `/dashboard` | DashboardPage | ADMIN, USER | 是 | `pages/DashboardPage.jsx` |
| `/notifications` | NotificationHistoryPage | ADMIN, USER | 是 | `pages/NotificationHistoryPage.jsx` |
| `/preferences` | PreferencesPage | ADMIN, USER | 是 | `pages/PreferencesPage.jsx` |
| `/profile` | ProfilePage | ADMIN, USER | 否 | `pages/ProfilePage.jsx` |
| `/admin/templates` | TemplatesPage | **ADMIN** | 否 | `pages/TemplatesPage.jsx` |
| `/admin/events` | EventSenderPage | **ADMIN** | 否 | `pages/EventSenderPage.jsx` |
| 其它 | NotFoundPage（404 兜底 + 回仪表盘出口） | — | — | `routes.jsx` |

守卫 `PrivateRoute`：未登录跳 `/login`；`roles` 不含当前角色跳 `/dashboard`。
⚠️ `menu.js` 的 `roles` 必须与 `routes.jsx` 一致，否则菜单会把用户导向一个跳回 dashboard 的路由。

---

## 4. 请求层约定（`api/axiosInstance.js`）

1. **baseURL = `/api`**（写死，不读环境变量）。所以 nginx 的 `/api/` 反代就是唯一出口。
2. **请求拦截**：从 `localStorage.user.token` 带 `Authorization: Bearer`，并从 JWT 的 `sub` 解出 `X-User-Id`（忽略前端自报的其它 id 字段，真实归属以后端网关注入为准）。
3. **响应拦截**：按 HTTP 码统一弹 `message.error`，**先查后端 `code` 对应的 `error.code.<CODE>` 文案**，没有才回落到通用文案（`error.400/403/404/409/500/network`）。
   - 401：登录接口的 401 交给页面自行处理（不跳转、不清态）；其它 401 清 `user` 并跳 `/login`。
   - 502/503 单独处理：这是"上游活着但暂时不能用"（如 Kafka 不可达时 `POST /api/events` 返回的 `EVENT_PUBLISH_FAILED`），不能糊成一句网络错误。
4. **业务错误契约**：后端统一返回 `{code, message}`，前端以 `code` 为准做本地化，`message` 只作兜底。

`customerApi.js` / `templateApi.js` 是薄封装；`templateApi.getTemplates` 负责把"数组 / `{content}` / `{data}`"三种返回形态**在一处**解包（本轮 `useTemplateStore` 改为复用它，消除了第二份解包逻辑）。

---

## 5. 页面说明

### 5.1 LoginPage / RegisterPage
- 登录成功写入 `user`+`token`，跳 `/dashboard`；已登录访问会被 `useEffect` 直接送回 `/dashboard`。
- 两页各挂一个 `LocaleSwitcher`（在 Layout 之外，必须自带切换器）。
- 注册：`locale` 取浏览器语言（N59，去掉静默默认值）。

### 5.2 DashboardPage（450 行）
统计卡 + 最近通知。默认只看当前登录客户自己的通知；全行统计（`/notifications/stats/all`）仅 ADMIN，普通用户会拿到 403。

### 5.3 NotificationHistoryPage（678 行）
分页、筛选（状态/渠道/事件类型）、单条已读/全部已读、失败重试。列表加载失败与"没有通知"分开渲染（`listError`，PRD-54①），避免 502/令牌过期显示成"暂无数据"。

### 5.4 PreferencesPage / PreferenceForm
按事件类型订阅渠道、开关、免打扰时段。`validateQuietPair`：时段两端要么都填要么都不填且起止不等——只填一端后端按"未设置"处理，起止相同则永远判不出"正在免打扰"，两种都是"界面显示设好了、实际永不生效"。

### 5.5 ProfilePage
改姓名/邮箱/手机/时区/通知语言。`PATCH /customers/me` 支持部分更新（N21）。

### 5.6 TemplatesPage（ADMIN）
模板 CRUD，按事件类型 + 渠道 + 语言（zh_CN/ru_RU/en_US）筛选。数据经 `useTemplateStore → templateApi`。

### 5.7 EventSenderPage（ADMIN，958 行，最大页面）
两种模式：
- **批量**：勾选客户 → 对每人各发一个事件（`sendToSelectedCustomers` 循环 `POST /api/events`，带各自 `customerId`）。
- **直发（PRD-51/N47）**：手填号码/邮箱，请求体**故意不带 `customerId`**，后端用网关注入的 `X-User-Id` 作归属。口令："输入谁的号码就发给谁"。

要点：
- 主按钮与"快速发送"按钮**共用同一段批量循环**（本轮把两份逐字复制的循环合并为 `sendToSelectedCustomers`）。
- 快速按钮的 `disabled` 条件与 `handleQuickSend` 保持一致，直发模式下不因"没勾选客户"而禁用。
- `eventTemplates` 提供各事件类型样例载荷；TRANSACTION 含 `balance`，避免把 `{{balance}}` 原样发出去（PRD-35）。
- 缺变量前端先拦（`blockMissingVars`），并对 `account` 给出"系统按收件人自动补全，无需填写"的提示。

---

## 6. 状态与实时 / 声音链路

四个 zustand store：`useAuthStore`、`useNotificationStore`、`usePreferenceStore`、`useTemplateStore`。

**实时通道**（`utils/notificationStream.js`）：
- 不用原生 `EventSource`（它不能带 `Authorization` 头，只能把令牌塞进 URL，会进访问日志/Referer/历史）。改用 `fetch + ReadableStream` 自解 SSE 帧。
- 通道只作"该刷新了"的信号，界面内容仍以 REST 为准：`onNotification` → `fetchLatest()`，声音和列表同一取数入口，杜绝"响了一声、列表是旧的"。
- 指数退避重连、30 秒封顶；心跳注释帧（`:` 开头）忽略。

**提示音**（`store/useNotificationStore.js` + `utils/notifySound.js`）：
- 15 秒轮询 + SSE 都汇入 `fetchLatest()`。
- 铃声只在**状态迁移进 SENT** 时响，用 `bellSeen: Map<id,status>` 去重；与后端 `NotificationStreamService` 只推终态的口径一致。
- 每次响两个振荡器（探针里 `rings=2` 即"响了一次"）。静音开关持久化在 localStorage。

⚠️ SSE 登记表在后端进程内存里，notification-service 只能单实例（PRD-49）。

---

## 7. i18n 与两套"语言"

- **界面语言**：`ru / zh / en`（`i18n/index.jsx` 的 `LANG_ATTR`），默认 `ru`。存 `localStorage.locale`。
- **通知正文语言**：`zh_CN / ru_RU / en_US`（`utils/constants.js` 的 `NOTIFICATION_LOCALES`），与后端 `AppConstants.SUPPORTED_LOCALES` 逐项对应，存 `customers.locale`。
- **两套不通用**：派发端只按 `customers.locale` 取模板，"界面切俄文就顺带用俄文正文"是不存在的映射（PRD-10）。
- 枚举/原因码/错误码走动态键：`enum.<group>.<key>`、`enum.reason.<CODE>`、`error.code.<CODE>`（`utils/labels.js` 的 `enumLabel` 与 axios 拦截器）。

**本轮修复**：`i18n/index.jsx` 对 `localStorage.locale` 做**白名单校验**（只认 `ru/zh/en`，其余回落 `ru`）。此前把后端的 `zh_CN` 误写进这个键，`IntlProvider` 会对非法语言标记抛 `RangeError` 导致**整页白屏**——两套语言只差一个下划线，这个误写随时可能发生。

---

## 8. 追加轮十六清理记录（前端）

| 项 | 动作 | 依据 |
| --- | --- | --- |
| `customerApi.login()` | 删 | 登录走 `useAuthStore` 自己的 `axios.post('/auth/login')`，此函数无引用 |
| `labels.enumOptions()` | 删 | 全仓 0 引用 |
| `constants.STATUS_COLOR` | 删 | 未用（颜色在 `STATUS_DETAILS`） |
| EventSenderPage 未用 antd 导入 | 删 | `InputNumber/Tooltip/Spin` 未用 |
| EventSenderPage 批量循环 | 合并 | 两份逐字复制的循环 → `sendToSelectedCustomers` |
| `useTemplateStore` | 改吃 `templateApi` | 消除第二份解包，令 create/update/delete 成为活代码 |
| Header `@keyframes pulse` | 删 | 无引用；`slideDown` 保留（下拉依赖） |
| `index.css @keyframes spin` | **补** | 铃铛骨架引用了 `spin` 但全仓无此关键帧（真缺陷） |
| 13 文件 `import React` 默认绑定 | 删 | 自动 JSX 运行时下不需要（`routes.jsx` 因 `React.lazy` 保留） |
| `messages.js` 死键 ×3 语言 | 删 138 行 | 46 键×3；排除 `enum.*`/`error.code.*` 与任何字面串仍出现在 `src` 的键。删后每语言 344 键、PARITY OK、0 缺失、0 残留死键 |
| `vite.config.ts` `resolve.alias '@'` | 删 | 全仓 0 处 `from '@/` |
| `.env`（仅含从未读的 `VITE_API_BASE_URL`） | 删 | baseURL 写死 `/api` |
| `check-i18n*.mjs`、`*-out.log`、`build-run.log` | 删 | 一次性脚手架，两个脚本（`check-i18n.mjs` / `check-i18n-usage.mjs`）都已随本轮删除；日志从未入库（`.gitignore` 里 `*.log`） |
| RegisterPage navigate state | 简化 | 目标 state 从未被读 |

**验证**（Playwright chromium 1440×900；`ctx.route('**/api/events', fulfill)` 全程本地应答，所以探针不落库）：
- 登录页真渲染、真提交错口令 → 本地化错误提示正常；
- ru / zh / en / zh_CN 四档首页渲染，0 `Missing message`、0 未翻译字面量外泄（`zh_CN` 是故意塞的非法值，用来验白名单回落 `ru`）；
- CSSOM：`spin`/`slideDown` 在、`pulse` 已删；
- 铃铛面板出内容、模板页 10 行、事件页两入口请求体字段一致（`bodies_equal_for_same_customer: true`）；
- 库 `max(id)=930 rows=633` 探针前后不变。

> 内置浏览器面板只有 532px 宽，桌面布局缺陷一律看不见，所以这类走查必须用 1440×900 的真浏览器。

**本轮新发现的缺陷 → 都已修**：
1. `localStorage.locale` 没有白名单：写进 `zh_CN`（后端/模板那套拼法）会让 `IntlProvider` 抛
   `RangeError: Invalid language tag` 并**整页白屏**。已在 `src/i18n/index.jsx` 的 `resolveLocale()`
   里收敛为 `ru|zh|en`，其余回落 `ru`；实测四档均正常。
2. N74 俄文界面弹中文：后端 `AuthService` 原来抛硬编码中文（`该邮箱已注册` / `邮箱或密码错误`）且不带 `code`，
   `LoginPage` / `RegisterPage` / `EventSenderPage` 各写一份"读 `data.message`"的逻辑，于是中文直接上屏。
   现在后端只回错误码（`EMAIL_ALREADY_USED` / `INVALID_CREDENTIALS`），
   三处统一调用 `i18n` 的 `errorText(code)` → `error.code.*` 按当前语言解析，回落 `message`，再回落通用兜底。
   实测俄文界面显示 «Неверный e-mail или пароль»。
   为什么把"口令错"和"账号不存在"并成一个码：分开写就成了"这个邮箱注册过吗"的探测口。
