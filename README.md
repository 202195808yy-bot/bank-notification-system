markdown
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
┌─────────────────────────────────────────────────────────────┐
│ React SPA (Port 3000) │
└──────────────────────────┬──────────────────────────────────┘
│ HTTPS /api/*
▼
┌─────────────────────────────────────────────────────────────┐
│ API Gateway (Spring Cloud Gateway) │
│ Port 8080 – JWT Auth │
└───┬──────────┬──────────┬──────────┬──────────┬─────────────┘
│ │ │ │ │
▼ ▼ ▼ ▼ ▼
┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
│Customer││ Notif. ││Template││Channel ││ Event │
│Service ││ Service ││Service ││Service ││Adapter │
│ :8081 ││ :8082 ││ :8083 ││ :8084 ││ :8085 │
└───┬────┘└───┬─────┘└───┬─────┘└───┬─────┘└───┬────┘
│ │ │ │ │
│ ┌────▼─────┐ │ │ │
│ │ Kafka │◄───┘ │ │
│ │ :9092 │◄──────────────┘ │
│ └────┬──────┘◄────────────────────────┘
│ │
▼ ▼
┌────────┐┌───────┐
│PostgreSQL││ Redis │
│ :5432 ││ :6379 │
└────────┘└───────┘

text

---

## 🛠 Technology Stack

| Layer               | Technology                                         |
|---------------------|----------------------------------------------------|
| Backend Framework   | Java 17, Spring Boot 3.1.6                         |
| API Gateway         | Spring Cloud Gateway                               |
| Internal Calls      | OpenFeign, RestTemplate                            |
| Authentication      | JJWT (JSON Web Tokens), Spring Security            |
| Database            | PostgreSQL 16                                      |
| Caching             | Redis 7 (Lettuce)                                  |
| Message Broker      | Apache Kafka (Confluent 7.5.0)                     |
| Frontend            | React 18, React Router 6, Ant Design 5, Zustand    |
| Internationalisation| react‑intl                                        |
| Containerisation    | Docker, Docker Compose                             |
| Testing             | Postman / Apifox collections, JMeter               |

---

## 🚀 Quick Start (Docker Compose)

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 4.x+
- Ports `3000`, `5432`, `6379`, `8080‑8085`, `9092` available

### Steps
1. **Clone the repository**
   ```bash
   git clone https://github.com/youruser/bank-notification-system.git
   cd bank-notification-system
Start all services

bash
docker compose build
docker compose up -d
(First build may take a few minutes)

Access the application

Frontend: http://localhost:3000

Default admin account: admin@bank.com / 123456

Stop the system

bash
docker compose down
📀 Test Data (optional)
To populate the database with 2,000 sample notifications, run:

bash
docker exec -i bank-postgres psql -U postgres -d bank_notifications < seed-data.sql
(Included in the repository root)

🧪 API Testing
A ready‑to‑use Apifox/Postman collection is available in the file api-tests.json at the root of the project.
Import it and run the following flow:

POST /api/auth/login – obtains JWT

GET /api/preferences – view current preferences

PUT /api/preferences – update preferences

GET /api/notifications/stats – dashboard statistics

GET /api/notifications?page=0&size=10 – notification history

POST /api/notifications/{id}/retry – retry a failed notification

POST /api/templates – (admin) create template

POST /api/events – send a test banking event

📂 Project Structure
text
bank-notification-system/
├── bank-notification-backend/     # Maven multi‑module backend
│   ├── common/                   # Shared entities, constants, DTOs
│   ├── api-gateway/              # Spring Cloud Gateway + JWT filter
│   ├── customer-service/         # Users, authentication, preferences
│   ├── notification-service/     # Event processing, template rendering, retry
│   ├── template-service/         # Template CRUD + Redis Pub/Sub
│   ├── channel-service/          # Sending strategies (SMS/Email/Push) + circuit breaker
│   ├── event-adapter/            # HTTP → Kafka adapter
│   ├── docker-compose.yml        # Infrastructure + all microservices
│   └── seed-data.sql             # Sample dataset
├── bank-notification-web/        # React SPA
│   ├── src/
│   │   ├── api/                  # Axios instance, API wrappers
│   │   ├── store/                # Zustand stores (auth, preferences, notifications, templates)
│   │   ├── pages/                # Page components (Login, Dashboard, Preferences, …)
│   │   ├── components/           # Layout, reusable UI components
│   │   ├── i18n/                 # Multi‑language messages
│   │   └── utils/                # Constants, formatters
│   ├── Dockerfile
│   └── nginx/conf.d/default.conf # Nginx reverse proxy configuration
├── api-tests.json                # Postman/Apifox collection
└── README.md
📜 License
This project is created for educational purposes as part of the university course "Web Application User Interface Development" (Vladimir State University, 2026).

Developed by Ivan Ivanov, group PRI‑320
Supervisor: A.A. Shamyshev
Vladimir State University, 2026

text
