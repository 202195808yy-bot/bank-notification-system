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

## System Architecture

The system consists of 6 microservices, 1 React frontend, and 3 middleware components, all orchestrated via Docker Compose.

**Services and responsibilities**:

| Service | Port | Description |
|---------|------|-------------|
| `api-gateway` | 8080 | Unified entry point, JWT authentication, route forwarding |
| `customer-service` | 8081 | User registration/login, notification preferences management |
| `notification-service` | 8082 | Consumes banking events, matches preferences, renders templates, generates and retries notifications |
| `template-service` | 8083 | Notification template CRUD, Redis cache synchronization |
| `channel-service` | 8084 | Channel sending (SMS/Email/Push) with circuit breaker and fallback |
| `event-adapter` | 8085 | Receives external events and publishes them to Kafka |
| `frontend` (React) | 3000 | Management interface (dashboard, preferences, history, templates) |

**Middleware**: PostgreSQL 16, Redis 7, Apache Kafka (Zookeeper)

**Typical request flow**:
1. User logs in via `api-gateway` and obtains a JWT
2. Core banking system pushes an event to Kafka topic `bank.events`
3. `notification-service` consumes the event → fetches preferences → renders template → creates notification → sends command to Kafka topic `notification.send.command`
4. `channel-service` consumes the command → invokes the specific sending channel → writes result to Kafka topic `notification.status`
5. Frontend queries notification history, preferences, and statistics through the gateway
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
bank-notification-system/
│
├── bank-notification-backend/ ← Maven parent project for backend
│ ├── common/ ← Shared entities, enums, constants, DTOs
│ ├── api-gateway/ ← Gateway: JWT filter + routes
│ ├── customer-service/ ← User service: authentication, preferences
│ ├── notification-service/ ← Notification service: event processing, rendering, retry
│ ├── template-service/ ← Template service: CRUD + Redis caching
│ ├── channel-service/ ← Channel service: sending strategies + circuit breaker
│ ├── event-adapter/ ← Event adapter: HTTP → Kafka
│ ├── docker-compose.yml ← All services orchestration (one-click start)
│ └── seed-data.sql ← 2000 test data generation script
│
├── bank-notification-web/ ← React frontend project
│ ├── src/
│ │ ├── api/ ← Axios instance and API wrappers
│ │ ├── store/ ← Zustand state stores (auth, preference, notification, template)
│ │ ├── pages/ ← Page components (login, dashboard, preferences …)
│ │ ├── components/ ← Common components (Layout, NotificationCard, PreferenceForm)
│ │ ├── i18n/ ← Multi-language messages (zh, en, ru)
│ │ └── utils/ ← Constants, date formatters
│ ├── Dockerfile ← Frontend image build file
│ └── nginx/conf.d/default.conf ← Nginx reverse proxy configuration
│
├── api-tests.json ← Apifox/Postman test collection
└── README.md ← This document
📜 License
This project is created for educational purposes as part of the university course "Web Application User Interface Development" (Vladimir State University, 2026).

Developed by Ivan Ivanov, group PRI‑320
Supervisor: A.A. Shamyshev
Vladimir State University, 2026

text
