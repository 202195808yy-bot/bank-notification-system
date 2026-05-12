# Bank Notification System

> Microservice-based automated customer notification system for banking events (transactions, risk alerts, billing reminders) via SMS, Email and Push notifications.

## 📦 Overview

This project is a full-stack application developed as part of the **Web Application User Interface Development** course.  
It demonstrates a modern microservice architecture with **Spring Boot**, **React**, **PostgreSQL**, **Redis**, **Apache Kafka**, and **Docker**.

The system automatically sends personalised notifications to bank customers based on their preferences whenever a banking event occurs (e.g. a new transaction, a security alert, or a bill due date).  
Users can manage their notification preferences, view notification history, and administrators can manage message templates.

---

## ✨ Key Features

### 🔐 Authentication & Authorization
- JWT‑based authentication (access token)
- Role‑based access control (`USER` / `ADMIN`)
- Secure API Gateway that enforces authentication on all endpoints (except login/register)

### 📊 Dashboard
- Real‑time statistics: total notifications, pending, sent, failed

### ⚙️ Notification Preferences
- Set preferred channels per event type (SMS, Email, Push)
- Configure quiet hours (do not disturb)
- Enable / disable notifications for each event type
- Redis caching for fast reads with database fallback

### 📬 Notification History
- Paginated list with filtering by event type, channel, status and date range
- Retry failed notifications (re‑queues them via Kafka)

### 📄 Template Management (admin only)
- Create, update, delete notification templates with variable placeholders (`{{variable}}`)
- Multiple languages / locales
- Redis Pub/Sub cache synchronisation

### 🚀 Asynchronous Event Processing
- Apache Kafka topics: `bank.events`, `notification.send.command`, `notification.status`
- Event‑driven generation of notifications
- Idempotency via Redis SETNX (duplicate events are ignored)
- Simulated multi‑channel sending (SMS, Email, Push) with circuit breaker (Resilience4j)

### 🌐 Multi‑language UI
- Supports English, Russian and Chinese (react‑intl + Ant Design ConfigProvider)

---

## 🧱 System Architecture
