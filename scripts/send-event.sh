#!/bin/bash

# 银行事件测试脚本 - 使用 curl 发送事件到事件适配器

EVENT_ADAPTER_URL="http://localhost:8085/api/events"

echo "=== 银行事件测试脚本 ==="
echo "事件适配器地址: $EVENT_ADAPTER_URL"
echo ""

# 发送交易事件
echo "1. 发送交易事件..."
curl -s -X POST $EVENT_ADAPTER_URL \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-trans-'$(date +%s)'",
    "eventType": "TRANSACTION",
    "customerId": 1,
    "account": "1234567890",
    "amount": 1500.50,
    "currency": "RUB",
    "transactionType": "DEBIT",
    "description": "线上购物",
    "merchant": "电商平台"
  }' | jq .

echo ""

# 发送风险预警事件
echo "2. 发送风险预警事件..."
curl -s -X POST $EVENT_ADAPTER_URL \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-risk-'$(date +%s)'",
    "eventType": "RISK_ALERT",
    "customerId": 2,
    "account": "9876543210",
    "riskLevel": "HIGH",
    "riskType": "异常交易",
    "ipAddress": "10.0.0.1",
    "location": "未知位置"
  }' | jq .

echo ""

# 发送账单事件
echo "3. 发送账单事件..."
curl -s -X POST $EVENT_ADAPTER_URL \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-bill-'$(date +%s)'",
    "eventType": "BILL",
    "customerId": 3,
    "billType": "水电费",
    "amount": 350.00,
    "dueDate": "2024-02-28",
    "account": "****1234"
  }' | jq .

echo ""
echo "=== 测试完成 ==="
