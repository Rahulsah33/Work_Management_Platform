# AI-Powered Work Management Platform

A robust enterprise work management platform built with Spring Boot, Spring Data JPA, Spring Security (JWT cookies & role authorization), Spring AI (Google Gemini / Ollama), and MySQL.

---

## Local Development

### Prerequisites & Requirements

- **Java**: JDK 21
- **Maven**: 3.9+ (or use the included `./mvnw.cmd` wrapper)
- **MySQL**: 8.0+ running on `localhost:3306`
- **Ollama**: `localhost:11434` (optional if using local LLM provider)

---

### Database Setup

1. Ensure MySQL is running on port `3306`.
2. The database name is `ai_work_management` (or `work_management`). By default, `createDatabaseIfNotExist=true` is enabled in connection settings.
3. Configure your local database credentials via environment variables or `application.properties`:

```properties
DB_URL=jdbc:mysql://localhost:3306/ai_work_management?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_mysql_password
```

---

### Environment Variables & AI Configuration

Create a `.env` file (refer to `.env.example`) or export the following variables locally:

| Variable | Description | Default Value |
|---|---|---|
| `SERVER_PORT` | Backend server port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |
| `DB_URL` | MySQL JDBC connection URL | `jdbc:mysql://localhost:3306/ai_work_management?...` |
| `DB_USERNAME` | MySQL database username | `root` |
| `DB_PASSWORD` | MySQL database password | `password` |
| `JWT_SECRET` | 256-bit secret key for HMAC-SHA | `YourSuperSecretKeyForWorkManagement2026Minimum32Bytes` |
| `JWT_EXPIRATION_MS` | JWT expiration duration in milliseconds | `3600000` (1 hour) |
| `GEMINI_API_KEY` | Google Gemini API Key | `your_gemini_api_key_here` |
| `GEMINI_MODEL` | Gemini Model Identifier | `gemini-3.7-flash` |

---

### Running the Application Locally

Run the Spring Boot application using Maven:

```bash
mvn spring-boot:run
```

Or using the Maven wrapper:

```bash
.\mvnw.cmd spring-boot:run
```

To run test suites:

```bash
.\mvnw.cmd test
```

To build production package:

```bash
.\mvnw.cmd clean package
```

---

### Local Service URLs & Documentation

| Service | Local URL | Description |
|---|---|---|
| **Backend API** | [http://localhost:8080](http://localhost:8080) | Root base URL |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive OpenAPI 3.0 documentation |
| **OpenAPI Specification** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | Raw OpenAPI JSON spec |
| **Actuator Health** | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Application health & probes |
| **Actuator Info** | [http://localhost:8080/actuator/info](http://localhost:8080/actuator/info) | Application version and metadata |

---

### Architecture & Security Overview

- **Authentication**: JWT authentication with support for both `HttpOnly` cookie (`jwt`) and `Authorization: Bearer <token>` header.
- **Role-Based Access Control**:
  - `ADMIN`: Global administration, system monitoring, and complete audit trail visibility.
  - `MANAGER`: Project creation, task delegation, requirement management, submission reviews (Approve/Changes Requested), manager dashboard metrics, and AI assistant chat.
  - `EMPLOYEE`: Task view (`/api/tasks/me`), submission upload, resubmission on feedback, employee dashboard metrics, and comments.
- **Data Isolation**: Strict tenancy enforcement preventing cross-user access to projects, tasks, submissions, notifications, comments, and audit logs.
- **AI Integration**:
  - Automated rubric-based submission evaluation against weighted task requirements.
  - Read-only manager AI assistant equipped with database inquiry tools.
- **Analytics & Reporting (Milestone 8)**:
  - High-performance aggregation pipeline for employee productivity, project health, AI scores, and task metrics.
  - Multi-format report exporter supporting streaming CSV, Apache POI XLSX, and iText PDF with formula-injection neutralization and audit logging.
  - Zero N+1 queries through batch relational aggregation and indexed database access patterns.
