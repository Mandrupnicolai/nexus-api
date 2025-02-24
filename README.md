# NexusAPI

[![CI](https://github.com/Mandrupnicolai/nexus-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Mandrupnicolai/nexus-api/actions)
[![codecov](https://codecov.io/gh/Mandrupnicolai/nexus-api/branch/main/graph/badge.svg)](https://codecov.io/gh/Mandrupnicolai/nexus-api)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A production-grade task and team management REST API demonstrating enterprise Java engineering patterns. Built with Spring Boot 3, PostgreSQL, Redis, WebSockets, and a Flutter mobile frontend.

## ✨ Features

- **JWT authentication** — stateless Bearer token auth with BCrypt password hashing
- **Role-based access control** — `@PreAuthorize` method security with USER and ADMIN roles
- **Task state machine** — enforced workflow: TODO → IN_PROGRESS → IN_REVIEW → DONE
- **Real-time updates** — WebSocket/STOMP broadcasting to Flutter clients on task changes
- **Redis caching** — configurable per-cache TTLs with automatic eviction on mutations
- **Versioned migrations** — Flyway manages all schema changes with rollback support
- **OpenAPI documentation** — Swagger UI auto-generated at `/swagger-ui.html`
- **Testcontainers** — integration tests run against real PostgreSQL (not H2)

## 🏗️ Architecture

```
┌─────────────┐     HTTP/WS      ┌─────────────────────────────────┐
│Flutter client│ ◄──────────────► │         Spring Boot API          │
└─────────────┘                   │                                  │
                                  │  Controllers → Services → Repos  │
                                  │  JWT Filter → Security Config    │
                                  └────────┬─────────┬──────────────┘
                                           │         │
                                    ┌──────▼──┐ ┌───▼────┐
                                    │PostgreSQL│ │ Redis  │
                                    └─────────┘ └────────┘
```

## 🚀 Quick Start

**Prerequisites:** Java 21, Docker, Docker Compose

```bash
git clone https://github.com/Mandrupnicolai/nexus-api.git
cd nexus-api
docker-compose up --build
```

The API is available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

## 📡 API Reference

### Authentication

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","username":"you","password":"Password123!"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"Password123!"}'
```

### Tasks

```bash
# Create a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"My Task","projectId":"<uuid>","priority":"HIGH"}'

# Update task status
curl -X PATCH http://localhost:8080/api/v1/tasks/<id> \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}'
```

## 🧪 Testing

```bash
# Unit tests only (fast — no Docker required)
mvn test

# Full suite including integration tests (requires Docker)
mvn verify

# Generate coverage report
mvn jacoco:report
# Open target/site/jacoco/index.html
```

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (records, sealed classes, text blocks) |
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JJWT |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Cache | Redis + Spring Cache |
| Real-time | WebSocket + STOMP |
| API Docs | SpringDoc OpenAPI 3 |
| Testing | JUnit 5 + Mockito + Testcontainers |
| Build | Maven + JaCoCo |
| Container | Docker + Docker Compose |
| Frontend | Flutter |

## 📄 License

MIT — see [LICENSE](LICENSE) for details.
