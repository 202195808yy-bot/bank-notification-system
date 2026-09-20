# API 文档

## 概述
银行通知系统 API 文档，包含所有服务的接口说明。

---

## 认证服务 (Customer Service)

### 用户注册
- **URL**: `/auth/register`
- **方法**: `POST`
- **请求参数**:
  ```json
  {
    "username": "string",
    "password": "string",
    "email": "string"
  }
  ```
- **响应**:
  ```json
  {
    "token": "string",
    "userId": "long"
  }
  ```

### 用户登录
- **URL**: `/auth/login`
- **方法**: `POST`
- **请求参数**:
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **响应**:
  ```json
  {
    "token": "string",
    "userId": "long"
  }
  ```

### 获取通知偏好
- **URL**: `/preferences`
- **方法**: `GET`
- **认证**: 需要
- **响应**:
  ```json
  [
    {
      "id": 1,
      "customerId": 1,
      "eventType": "TRANSACTION",
      "channels": ["SMS", "EMAIL"],
      "enabled": true,
      "quietStart": "22:00:00",
      "quietEnd": "08:00:00"
    }
  ]
  ```

### 更新通知偏好
- **URL**: `/preferences`
- **方法**: `POST`
- **认证**: 需要
- **请求参数**:
  ```json
  [
    {
      "eventType": "TRANSACTION",
      "channels": ["SMS", "EMAIL"],
      "enabled": true,
      "quietStart": "22:00:00",
      "quietEnd": "08:00:00"
    }
  ]
  ```

---

## 通知服务 (Notification Service)

### 获取通知列表
- **URL**: `/notifications`
- **方法**: `GET`
- **认证**: 需要
- **查询参数**:
  - `page`: 页码 (默认 0)
  - `size`: 每页大小 (默认 10)
  - `eventType`: 事件类型 (可选)
  - `channel`: 渠道 (可选)
  - `status`: 状态 (可选)
  - `startDate`: 开始日期 (可选，ISO 格式)
  - `endDate`: 结束日期 (可选，ISO 格式)

### 获取通知统计
- **URL**: `/notifications/stats`
- **方法**: `GET`
- **认证**: 需要
- **响应**:
  ```json
  {
    "total": 100,
    "pending": 10,
    "sent": 85,
    "failed": 5
  }
  ```

### 重试通知
- **URL**: `/notifications/{id}/retry`
- **方法**: `POST`
- **认证**: 需要

---

## 事件适配器 (Event Adapter)

### 接收银行事件
- **URL**: `/events`
- **方法**: `POST`
- **请求参数**:
  ```json
  {
    "eventId": "evt_123",
    "eventType": "TRANSACTION",
    "customerId": 1,
    "payload": {
      "amount": 1000,
      "account": "123456"
    }
  }
  ```

---

## 渠道类型 (Channel Types)
- `SMS`: 短信
- `EMAIL`: 邮件
- `PUSH`: 推送通知

## 发送状态 (Send Statuses)
- `PENDING`: 待发送
- `SENT`: 已发送
- `FAILED`: 发送失败

## 事件类型 (Event Types)
- `TRANSACTION`: 交易事件
- `BALANCE`: 余额变动
- `LOGIN`: 登录通知
- `SECURITY`: 安全事件
