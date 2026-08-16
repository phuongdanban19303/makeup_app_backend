# On-Demand Mobile Makeup Microservices Platform

Monorepo boilerplate architecture for the real-time On-Demand Mobile Makeup System using **Java 17**, **Spring Boot 3.2.3**, and **Spring Cloud 2023.0.0**.

---

## Service Overview & Port Mapping

| Service Name | Port | Description | Technologies |
| :--- | :--- | :--- | :--- |
| `api-gateway` | `8080` | Unified REST Gateway & WebSocket Router | Spring Cloud Gateway, WebFlux |
| `user-service` | `8081` | User Account & MUA Profile Management | Spring Data JPA, PostgreSQL (`user_db`) |
| `location-service` | `8082` | Real-time GPS Stream Ingestion & GEO Queries | Spring Data Redis (Redis GEO) |
| `booking-service` | `8083` | Booking Creation & Order State Machine | Spring Data JPA, PostgreSQL (`booking_db`), Kafka |
| `pricing-service` | `8084` | Dynamic Pricing & Surge Calculation Engine | Spring Web, Redis Cache |
| `payment-service` | `8085` | E-Wallet & Payment Transaction Management | Spring Data JPA, PostgreSQL (`payment_db`), Kafka |

---

## Infrastructure Setup (Docker Compose)

The `docker-compose.yml` file sets up all required infrastructure services:
- **PostgreSQL 16**: `localhost:5432` (Username: `postgres`, Password: `postgrespassword`)
- **Redis 7 (GEO Supported)**: `localhost:6379`
- **Apache Kafka**: `localhost:9092`
- **Zookeeper**: `localhost:2181`

### Running Infrastructure

To start the infrastructure containers:
```bash
docker compose up -d
```

---

## Building and Running Services

### Build All Services (Maven Root)
```bash
mvn clean install -DskipTests
```

### Running Individual Services
Navigate to any service directory or run from root:
```bash
mvn spring-boot:run -pl services/api-gateway
mvn spring-boot:run -pl services/user-service
mvn spring-boot:run -pl services/location-service
mvn spring-boot:run -pl services/booking-service
mvn spring-boot:run -pl services/pricing-service
mvn spring-boot:run -pl services/payment-service
```

---

## Directory Structure

```
makeup_app_v1/
├── pom.xml
├── docker-compose.yml
├── init-scripts/
│   └── 01-init-databases.sql
├── README.md
└── services/
    ├── api-gateway/
    ├── user-service/
    ├── location-service/
    ├── booking-service/
    ├── pricing-service/
    └── payment-service/
```
