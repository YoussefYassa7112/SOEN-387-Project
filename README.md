# Locus

**Company:** SQUARED
**Product:** Locus, a parking spot rental service. Owners list their parking spots, and drivers search for a spot, reserve it for a time interval, and pay through Stripe.

Locus is built as a back-end service with an explicit OpenAPI contract. The React front end exists only to test the service.

SOEN 387 (Web-Based Enterprise Application Design), Concordia University, Fall 2026.

## Founders

| Name              | GitHub                                                   |
|-------------------|----------------------------------------------------------|
| Alvin Biju        | [@Alvin-11](https://github.com/Alvin-11)                 |
| Tristan Girardi   | [@TristanGirardi](https://github.com/TristanGirardi)     |
| _Youssef Yassa_   | [@YoussefYassa7112](https://github.com/YoussefYassa7112) |
| _Youssef Youssef_ | _[@Youssef8811](https://github.com/Youssef8811)_         |

## Tech stack

| Part | Technology |
|------|------------|
| Backend | Java 21, Spring Boot 3.5, Maven |
| Database | PostgreSQL on [Neon](https://neon.tech), schema migrations with Flyway |
| API contract | OpenAPI 3.1, browsable through Swagger UI (springdoc) |
| Frontend | React 19 with Vite |
| Payments | Stripe (sandbox / test mode) |
| Authentication | Google Sign-In, then a signed token issued by the service |

## Features

Planned for the course milestones (see the [issues](https://github.com/Alvin-11/SOEN387Project/issues) and [milestones](https://github.com/Alvin-11/SOEN387Project/milestones)):

- [ ] Owners create parking spot listings (photo, address, price, availability)
- [ ] Drivers search available spots and view spot details and availability
- [ ] Drivers reserve a spot for a time interval, with no double-booking under concurrent requests
- [ ] Safe retries of reservations with an `Idempotency-Key`
- [ ] Drivers view and cancel their reservations
- [ ] Payment through Stripe, processed asynchronously, with the spot released if payment fails
- [ ] Authentication and per-user authorization
- [ ] Rate limiting, health checks and structured logs with a correlation id

## Repository layout

```
backend/    Spring Boot service
  src/main/java/com/squared/locus/
    api/          interface layer (REST controllers)
    service/      service layer (use cases, transactions)
    domain/       domain layer (business rules, no framework code)
    datasource/   data source layer (repositories, persistence)
  src/main/resources/
    application.yml
    db/migration/ Flyway migrations (V1__..., V2__...)
frontend/   React test client (Vite)
```

## Prerequisites

- JDK 21
- Node.js 20.19+ (or 22.12+) and npm
- A Neon PostgreSQL database (ask a teammate for the connection details)

Maven does not need to be installed: the backend ships with the Maven wrapper (`mvnw`).

## Running the backend

The backend reads its configuration from environment variables. See `backend/.env.example` for the full list.

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | JDBC URL of the Neon database, e.g. `jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require` |
| `DATABASE_USERNAME` | Database user |
| `DATABASE_PASSWORD` | Database password |
| `PORT` | HTTP port (default `8080`) |

Set them in your shell (or in your IDE run configuration), then start the service:

```bash
# macOS / Linux
cd backend
./mvnw spring-boot:run
```

```powershell
# Windows (PowerShell)
cd backend
$env:DATABASE_URL="jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require"
$env:DATABASE_USERNAME="<user>"
$env:DATABASE_PASSWORD="<password>"
.\mvnw.cmd spring-boot:run
```

Once it is running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

## Running the security and ownership tests

These tests cover listing ownership (LOCUS-12), reservation access (LOCUS-13), and JWT validation (LOCUS-14), together with API conformance. They do not require a running backend, a database, or real credentials.

From the project root:

```bash
# macOS / Linux
cd backend
bash mvnw -Dtest=ParkingSpotServiceTest,ReservationServiceTest,JwtSecurityTest,ParkingSpotSecurityTest,ApiConformanceTest test
```

```powershell
# Windows (PowerShell)
cd backend
.\mvnw.cmd "-Dtest=ParkingSpotServiceTest,ReservationServiceTest,JwtSecurityTest,ParkingSpotSecurityTest,ApiConformanceTest" test
```

To run just the JWT tests from the `backend` directory:

```bash
bash mvnw -Dtest=JwtSecurityTest test
```

Look for `BUILD SUCCESS` when the tests finish. Detailed results are saved in `backend/target/surefire-reports/`.

## Running the reservation concurrency test

Create a temporary local PostgreSQL database named `locus_concurrency_test`, then run:

```bash
cd backend
bash mvnw test -Dtest=ReservationConcurrencyTest
```

The test uses local PostgreSQL by default and does not use the Neon database. Override the database settings with `-Dtest.database.url`, `-Dtest.database.username`, and `-Dtest.database.password` if needed.

## Running the frontend

```bash
cd frontend
cp .env.example .env.local   # points the client at the backend URL
npm install
npm run dev
```

Then open <http://localhost:5173>.

## Secrets

Never commit real credentials. `.env` files are ignored by Git; only the `.env.example` templates are tracked.
