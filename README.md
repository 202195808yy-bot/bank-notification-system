# 银行客户自动通知系统

## 项目概述

构建了一套多通道的实时银行通知平台，支持短信、邮件、App推送等渠道，覆盖交易提醒、风险告警、营销活动等业务场景。系统基于事件驱动架构，实现了通知的精准分发、偏好管理、静默期控制及去重机制，并通过统一的 API 网关对外提供服务。

## 项目结构

本项目采用微服务架构，包含以下服务模块：

- **common** - 公共模块，包含实体类、常量、DTO、工具类等
- **api-gateway** - API网关，通过 Spring Cloud Gateway 统一路由，负责请求路由和JWT认证
- **customer-service** - 客户服务，管理客户信息和通知偏好
- **notification-service** - 通知服务，处理通知的分发和状态管理
- **template-service** - 模板服务，管理通知模板和变量渲染
- **channel-service** - 渠道服务，负责通过不同渠道发送通知
- **event-adapter** - 事件适配器，接收银行系统事件并转换为通知

## 技术栈

- **React + Ant Design** - 前端框架和UI组件库
- **Spring Boot 3.1.6** - 后端微服务框架
- **Spring Cloud Gateway** - API网关
- **Spring Data JPA** - 数据访问层
- **PostgreSQL 16** - 主数据库
- **Redis 7** - 缓存和会话管理
- **Apache Kafka** - 事件驱动消息队列
- **Docker & Docker Compose** - 容器化部署

## 核心功能

### 📨 多渠道通知支持
- 短信通知 (SMS)
- 邮件通知 (Email)
- App推送 (Push Notification)

### 🎯 业务场景覆盖
- 交易提醒 (TRANSACTION)
- 风险告警 (RISK_ALERT)
- 营销活动 (PROMOTION)
- 账单通知 (BILL)

### ⚙️ 通知偏好管理
- 多事件类型配置
- 多渠道灵活选择
- 单行启用/禁用切换
- 表单新增/编辑
- 免打扰时段设置

### 🔐 安全机制
- JWT认证
- 通知去重机制 (基于 event_id + customer_id 唯一约束)
- 消息重试机制

### 📊 实时监控
- 通知状态追踪
- 发送成功率统计
- 实时数据仪表盘

## 快速开始

### 前置条件

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Node.js 18+ (前端开发)

### 一键部署本地开发环境

```bash
# 克隆项目
git clone <repository-url>
cd bank-notification-backend

# 一键启动所有服务
docker-compose up -d --build

# 等待所有服务健康检查通过后，访问
# 前端界面: http://localhost:3000
# API网关: http://localhost:8080
```

### 本地开发运行

1. **启动基础设施**
   ```bash
   docker-compose up -d postgres redis zookeeper kafka
   ```

2. **编译项目**
   ```bash
   mvn clean install -DskipTests
   ```

3. **启动各微服务**
   ```bash
   # 按顺序启动
   cd customer-service && mvn spring-boot:run
   cd notification-service && mvn spring-boot:run
   cd template-service && mvn spring-boot:run
   cd channel-service && mvn spring-boot:run
   cd event-adapter && mvn spring-boot:run
   cd api-gateway && mvn spring-boot:run
   ```

4. **启动前端**
   ```bash
   cd ../bank-notification-web
   npm install
   npm start
   ```

## 服务端口

| 服务 | 端口 | 功能说明 |
|------|------|----------|
| api-gateway | 8080 | 统一API入口 |
| customer-service | 8081 | 客户管理、偏好设置 |
| notification-service | 8082 | 通知分发、状态追踪 |
| template-service | 8083 | 模板管理、变量渲染 |
| channel-service | 8084 | 多渠道发送 |
| event-adapter | 8085 | 事件适配、消息生产 |
| frontend | 3000 | 前端应用 |
| postgres | 15432 | 数据库 |
| redis | 16379 | 缓存 |
| kafka | 9092 | 消息队列 |

## Docker Compose 编排设计

### 基础设施容器
- **PostgreSQL** - 健康检查、数据持久化
- **Redis** - 健康检查、配置保护
- **Zookeeper** - Kafka协调服务
- **Kafka** - 健康检查、主题自动创建

### 网络隔离
- **bank-net** - 专用网络，实现服务间通信隔离

### 启动顺序控制
```yaml
depends_on:
  postgres:
    condition: service_healthy
  kafka:
    condition: service_healthy
```

## 项目成果

### 技术积累
- ✅ 丰富的容器化环境调试经验
- ✅ Docker网络配置与故障排查
- ✅ Kafka内外网地址映射处理
- ✅ Redis端口冲突快速定位与解决
- ✅ PostgreSQL版本兼容性问题处理

### 系统特性
- ✅ 事件驱动架构，高吞吐量
- ✅ 通知去重机制，避免重复打扰用户
- ✅ 静默期控制，尊重用户偏好
- ✅ 多租户支持（预留扩展）
- ✅ 完整的消息追踪和审计日志

## 开发指南

### 本地测试发送事件

```bash
# 使用事件发送器
curl -X POST http://localhost:8085/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "TRANSACTION",
    "customerId": 1,
    "payload": {
      "amount": 1000.50,
      "type": "DEBIT",
      "account": "****6789"
    }
  }'
```

### 数据库连接
- Host: localhost
- Port: 15432
- Database: bank_notifications
- Username: postgres
- Password: 123456

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行单个服务测试
cd customer-service && mvn test
```

## 项目特色亮点

1. **完整的微服务架构** - 6个核心服务 + API网关
2. **事件驱动设计** - Kafka消息队列实现异步处理
3. **Redis缓存加速** - 模板和热点数据缓存
4. **Docker一键部署** - 开发环境开箱即用
5. **专业的前端界面** - 基于React + Ant Design构建

## 许可证

[MIT License](LICENSE)
