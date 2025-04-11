# Changelog

All notable changes to NexusAPI will be documented in this file.

## [Unreleased]

## [0.1.0] - 2025-01-01
### Added
- Initial project scaffold with Spring Boot 3.2 + Java 21
- JWT authentication with JJWT
- Task CRUD with state machine (TODO → IN_PROGRESS → IN_REVIEW → DONE)
- PostgreSQL 16 + Flyway migrations (V1 schema, V2 seed data)
- Redis caching (tasks 5min, task 10min, users 15min TTL)
- WebSocket/STOMP real-time task notifications per project
- MapStruct-style DTO mapping (manual mapper)
- Global exception handler with RFC 7807-style ApiError
- JUnit 5 + Mockito unit tests
- Testcontainers integration tests
- Docker + Docker Compose setup
- GitHub Actions CI pipeline
- Flutter frontend with Provider pattern
  - Login screen
  - Kanban board screen
  - Task card widget
  - Create task dialog
  - WebSocket service for live updates
  - Auth service with token persistence