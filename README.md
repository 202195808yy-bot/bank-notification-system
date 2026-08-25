# Bank Notification System (银行通知系统)

银行客户通知系统的前端 Web 应用。基于 React 18 + Ant Design 5 + react-intl 多语言，
提供通知仪表盘、偏好管理、模板管理、事件发送与通知历史等功能。

## 功能概览

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录 | `/login` | 邮箱/密码登录，JWT 认证 |
| 注册 | `/register` | 新用户注册 |
| 仪表盘 | `/dashboard` | 通知统计、实时刷新、分布图表 |
| 通知偏好 | `/preferences` | 按事件类型配置渠道与免打扰时段 |
| 通知历史 | `/notifications` | 通知列表、筛选、重试失败通知 |
| 模板管理 | `/admin/templates` | 通知模板的增删改查（管理员） |
| 事件发送 | `/admin/events` | 批量向客户发送银行事件（管理员） |

## 技术栈

- **框架**: React 18.2 + react-router-dom 6
- **UI**: Ant Design 5 + @ant-design/icons
- **状态管理**: Zustand 5
- **HTTP**: Axios（请求/响应拦截器，自动携带 JWT）
- **国际化**: react-intl（支持 俄语 / 中文 / 英文）
- **构建工具**: react-scripts 5（Create React App）
- **日期处理**: dayjs

## 安装与运行

### 环境要求

- Node.js >= 16
- npm（随 Node 一起提供）

### 本地开发

```bash
# 安装依赖
npm install

# 启动开发服务器（代理 /api 到 http://localhost:8080）
npm start
# 访问 http://localhost:3000
```

### 生产构建

```bash
npm run build
# 产物在 build/ 目录，可通过任意静态服务器或 Nginx 部署
```

## 部署

### Docker

```bash
# 构建镜像并启动容器（端口 3000 映射到容器 80）
docker build -t bank-frontend:latest .
docker run -d --name bank-frontend -p 3000:80 --network bank-network bank-frontend:latest
```

镜像基于 `node:18-alpine` 构建，产物用 `nginx:alpine` 托管。

### Nginx 配置

`nginx.conf` 已包含 SPA 路由回退（`try_files $uri $uri/ /index.html`）
以及 `/api/` 代理到后端网关 `http://api-gateway:8080`。

### Windows 自动部署脚本

`deploy.ps1` 提供一键部署：检测源码变更 → 重建 → 停止旧容器 → 构建镜像 → 启动新容器。

## 项目结构

```
src/
├── api/                  # Axios 实例 + 各模块 API 封装
│   ├── axiosInstance.js  # 请求/响应拦截器，自动注入 JWT 与 X-User-Id
│   ├── customerApi.js    # 登录、注册、偏好
│   ├── notificationApi.js# 通知列表、重试、统计
│   └── templateApi.js    # 模板增删改查
├── components/
│   ├── Layout/           # Header / Sidebar / NotificationDropdown
│   ├── NotificationCard.jsx
│   └── PreferenceForm.jsx
├── context/AuthContext.jsx
├── hooks/                # useNotifications / usePreferences / useFormDraft / ...
├── i18n/                 # messages.js（ru/zh/en）+ I18nProvider
├── pages/                # 各业务页面
├── routes.js             # 路由守卫（PrivateRoute）
├── store/                # Zustand stores（auth / notification / preference / template）
└── utils/                # constants（主题、枚举）/ formatters
```

## 认证与请求约定

- 登录成功后 `token` 与 `user`（含 `name`/`role`）写入 `localStorage`。
- `axiosInstance` 请求拦截器从 JWT 的 `sub` 字段提取用户 ID，
  通过 `Authorization: Bearer <token>` 与 `X-User-Id` 头发送。
- 401（除登录接口外）自动清除登录态并跳转登录页。
- 其他状态码（400/403/404/409/500）统一通过 Ant Design `message` 提示。

## 多语言

默认语言从 `localStorage.getItem('locale')` 读取，缺省为 `ru`（俄语）。
Header 语言切换下拉框可切换 `中文 / English / Русский`，
切换后写入 `localStorage` 并刷新页面生效。

## 开发说明

- 代理地址：`package.json` 中 `"proxy": "http://localhost:8080"`，
  开发时 `/api`、`/auth`、`/events` 等请求自动转发到后端。
- 生产环境由 Nginx 反向代理到 `api-gateway:8080`。

## License

Private.