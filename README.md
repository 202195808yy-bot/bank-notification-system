markdown
# Bank Notification System

A full-stack microservice-based application that automates customer notifications for banking events (transactions, risk alerts, bill reminders) via SMS, Email, and Push.

---

## Overview

This project is a comprehensive assignment for the course **"Web Application User Interface Development"**.  
It adopts a **front-end/back-end separation + microservices** architecture: the back end uses **Spring Cloud Gateway** as the unified entry point and **Apache Kafka** for asynchronous event-driven processing; the front end is a **React** single-page application with internationalization, state management, and Ant Design components.

Core flow: Banking event → Kafka message bus → Notification Service matches customer preferences → Renders template → Multi-channel delivery.

---

## Key Features

### 🔐 Authentication & Authorization
- JWT-based login and registration.
- Gateway-level authentication; only `/api/auth/login` and `/api/auth/register` are public.
- Admin role (ADMIN) can access `/admin/**` routes; regular users are denied.

### 📊 Dashboard
- Displays real-time statistics for the current user: total notifications, pending, sent, failed.

### ⚙️ Notification Preferences
- Set notification channels (SMS, Email, Push) independently for each event type (transaction, risk, promotion, bill).
- Define quiet hours (do not disturb).
- Enable/disable notifications per event type.
- Redis caching for fast reads with automatic database fallback on failure.

### 📬 Notification History
- Paginated list with filters by event type, channel, status, and date range.
- Failed notifications can be manually retried (re-queued via Kafka).

### 📄 Template Management (Admin only)
- CRUD operations on notification templates.
- Templates support `{{variable}}` placeholders for dynamic content.
- Multi-language/multi-locale support (zh_CN, en_US, ru_RU).
- Redis Pub/Sub cache synchronization ensures templates are updated instantly.

### 🚀 Asynchronous Event Processing
- Three Kafka topics: `bank.events`, `notification.send.command`, `notification.status`.
- Event adapter receives external events and publishes them to Kafka.
- Notification service consumes events, matches preferences, renders templates, generates notifications, and publishes send commands.
- Channel service consumes send commands and dispatches to the appropriate sender (SMS/Email/Push) using the Strategy pattern with circuit breaker and fallback.
- Idempotency: the same event ID is not processed again within 1 hour (Redis SETNX).

### 🌐 Multi-language UI
- Supports English, Russian, and Simplified Chinese.
- Uses react-intl for text internationalization; Ant Design language packs are switched accordingly.

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| **Backend Framework** | Java 17, Spring Boot 3.1.6 |
| **API Gateway** | Spring Cloud Gateway |
| **Inter-service Communication** | OpenFeign, RestTemplate |
| **Security** | JJWT (JSON Web Tokens), Spring Security |
| **Database** | PostgreSQL 16 |
| **Cache** | Redis 7 (Lettuce) |
| **Message Broker** | Apache Kafka (Confluent 7.5.0) |
| **Frontend** | React 18, React Router 6, Ant Design 5, Zustand |
| **Internationalization** | react-intl |
| **Containerization** | Docker, Docker Compose |
| **Testing Tools** | Apifox / Postman Collections, JMeter |

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

---

## Quick Start (Docker Compose)

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 4.x or later
- Ensure the following ports are available: `3000`, `5432`, `6379`, `8080‑8085`, `9092`

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/youruser/bank-notification-system.git
   cd bank-notification-system
Build and start all services

bash
docker compose build
docker compose up -d
The first build may take 5–10 minutes.

### Access the application

Frontend: http://localhost:3000

Default admin account: admin@bank.com

Default password: 123456 (corresponds to the BCrypt hash in the database)

### Stop the system

bash
docker compose down
Loading Test Data (Optional)
To immediately populate the system with 2000 sample notifications, run:

bash
docker exec -i bank-postgres psql -U postgres -d bank_notifications < seed-data.sql
After execution, log in as any user to see notification history, dashboard statistics, and preferences.

### API Testing
The project includes a ready-to-import Apifox or Postman collection file: api-tests.json.
Import it to run the following core endpoints with a single click:

POST /api/auth/register – Register a new user

POST /api/auth/login – Login (token is automatically saved)

GET /api/preferences – Retrieve notification preferences

PUT /api/preferences – Update preferences (a pre-request script handles the channels format automatically)

GET /api/notifications/stats – Get notification statistics

GET /api/notifications?page=0&size=10 – View notification history

POST /api/notifications/{id}/retry – Retry a failed notification

POST /api/templates – Create a template (admin token required)

POST /api/events – Send a test Kafka event

All requests include assertion scripts for automated regression testing.

### Project Structure
## Project Structure

- **bank-notification-backend** – Maven parent project for backend
  - **common/** – Shared entities, enums, constants, DTOs
  - **api-gateway/** – Gateway: JWT filter + routes
  - **customer-service/** – User service: authentication, preferences
  - **notification-service/** – Event processing, rendering, retry
  - **template-service/** – Template CRUD + Redis caching
  - **channel-service/** – Sending strategies + circuit breaker
  - **event-adapter/** – HTTP → Kafka adapter
  - **docker-compose.yml** – All services orchestration
  - **seed-data.sql** – 2000 test data generation script
- **bank-notification-web** – React frontend project
  - **src/**
    - **api/** – Axios instance and API wrappers
    - **store/** – Zustand state stores
    - **pages/** – Page components
    - **components/** – Common UI components
    - **i18n/** – Multi-language messages
    - **utils/** – Constants, date formatters
  - **Dockerfile** – Frontend image build file
  - **nginx/conf.d/default.conf** – Nginx reverse proxy config
- **api-tests.json** – Apifox / Postman test collection
- **README.md** – This document

### Important Notes
All user passwords are stored as BCrypt hashes in the database. Test data uses the password 123456 with the hash $2a$10$.5G.GxQ3/x2upJ0oE.wopO80eOSN.FQgwLza3fcAO.oJ7o4sAJHKe.

The JwtAuthFilter in the gateway is in formal authentication mode: only login/register endpoints are public; all other requests must carry a valid JWT; admin routes additionally check for the ADMIN role.

Redis connection may occasionally fail due to container startup ordering, but all Redis-related business code includes fault-tolerance and fallback to prevent core functionality from being affected.

If you modify backend code, run docker compose build --no-cache <service-name> to ensure the new code takes effect.

### License
This project is an academic assignment for the "Web Application User Interface Development" course at Vladimir State University and is intended for educational purposes only.

Student: Ivan Ivanov
Supervisor: A.A. Shamyshev
2026
