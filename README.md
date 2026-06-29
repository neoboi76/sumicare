<p align="center">
  <img src="apps/sumicare-web/src/assets/logos/sumicare-colored-linear.svg" alt="SumiCare" width="360" />
</p>

<h1 align="center">SumiCare</h1>

<p align="center">
  A web-based spa operations management platform that computerizes the paper-based workflows of
  wellness enterprises, pairing a public booking website with a role-restricted internal operations
  system.
</p>

---

## Authors

SumiCare is an academic thesis project developed at **Mapúa University** by:

| Name | Student No. | Email |
|---|---|---|
| De La Paz, Lance Gabriel C. | 2023103105 | [lgcdelapaz@mymail.mapua.edu.ph](mailto:lgcdelapaz@mymail.mapua.edu.ph) |
| Pereira, Franz C. | 2023105242 | [fcpereira@mymail.mapua.edu.ph](mailto:fcpereira@mymail.mapua.edu.ph) |
| Timbol, Dino Alfred T. (Group Leader) | 2021130744 | [dattimbol@mymail.mapua.edu.ph](mailto:dattimbol@mymail.mapua.edu.ph) |

Group leader: Dino Alfred T. Timbol — [LinkedIn](https://www.linkedin.com/in/dino-alfred-timbol-3b949a248)

The platform was designed and implemented against the operational reality of the partner
organization, **New Lasema Spa Jjimjilbang**.

## Project Overview

SumiCare is a web-based spa operations management platform that **computerizes** the previously
paper-based operational workflows of wellness enterprises. It replaces manual booking sheets,
treatment slips, therapist line-ups, cashiering, and reporting with a single, auditable system that
spa staff and clients use through the browser. While it was built for New Lasema Spa Jjimjilbang, it
ships as a generalized, multi-tenant product: every organization configures its own branding,
services, shifts, rooms, vouchers, and recommendation weights.

The system exposes two surfaces that share one backend and one database. The **public booking
website** lets prospective clients browse services, receive personalized massage recommendations,
make hard or soft reservations, manage or cancel a booking, and leave feedback — all through open,
unauthenticated endpoints. The **internal operations system** is a role-restricted application for
spa staff to manage therapist decking, client and room assignment, treatment-slip generation,
point-of-sale and cashier operations, and report generation, protected by JWT
authentication and method-level authorization.

As a thesis project, SumiCare targets a real operational gap: small and mid-sized spas that run on
paper and cannot justify enterprise software. It keeps the walk-in-first reality of the business
(client accounts are optional and sessions never require one) while adding online booking,
recommendations, immutable audit logging, and remotely accessible CSV-exportable reports.

## Features

### Internal Operations System

- Therapist decking (ordered line-up) with latest-shift-first preemption, skip mode, requested-therapist handling, and manual backup insertion.
- Booking and walk-in management, including automatic group/couple/VIP bookings with per-guest sessions.
- Live room and bed occupancy with gender-segregated common rooms, updated in real time over WebSocket.
- Treatment-slip generation and digitization (clients identified by nickname only).
- Point-of-sale and cashier module: cash, GCash, credit, and debit payments, refunds, receipts, discounts, vouchers, and an append-only transaction ledger.
- Cutoff, end-of-day, monthly, commission, and decking reports, all exportable to CSV (`text/csv`).
- Per-organization branding.
- Immutable audit logging of every authenticated state-changing action.

### Public Booking Website

- Service catalogue and package browsing with per-organization branding.
- Weighted-scoring massage recommendations (recreational only; disclaimer always shown).
- Online booking with hard or soft reservations and automatic locker assignment.
- Email-verified booking lookup and self-service cancellation.
- Feedback submission and a public contact form.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular 21.2 (standalone components, signals, OnPush), TypeScript 5.9, Tailwind CSS 3.4 |
| Backend | Spring Boot 3.2.5, Java 21 (modular monolith, layered architecture) |
| Database | PostgreSQL 16 |
| Cache / in-memory store | Redis 7 (Spring Data Redis) |
| Real-time | STOMP over WebSocket (`@stomp/rx-stomp` 1.2 on the client) |
| Auth | Spring Security, JWT (`jjwt` 0.12.5) with access + refresh tokens, BCrypt, MFA |
| Migrations | Liquibase |
| PDF / QR | openhtmltopdf-pdfbox 1.0.10, ZXing 3.5.3 |
| Workspace | NX 22.6 integrated monorepo |
| Payments | PayMongo (cash/GCash/card), with mock mode for local development |
| Deployment | Railway (frontend, backend, Redis), managed PostgreSQL |

## System Architecture

The backend is a **modular monolith**: a single deployable Spring Boot application organized into
bounded-context modules, each following a `Controller -> Service -> Repository -> Domain` layering
with no business logic in controllers or repositories and no cross-module direct database access.
Modules include `auth`, `user`, `organization`, `booking`, `therapist`, `shift`, `room`,
`transaction`, `pos`/`cashier`, `report`, `recommendation`, `client`, `notification`,
and `audit`. Both the public booking website and the internal operations system are
served by this one backend and persist to one PostgreSQL database; public endpoints are exposed
while internal endpoints are protected.

The repository is an **NX integrated monorepo**: `apps/sumicare-web` (Angular) and
`apps/sumicare-api` (Spring Boot) are the deployable applications, while `libs/shared-types` holds
the TypeScript DTOs shared with the frontend and `libs/ui` holds reusable Angular UI primitives.

## Prerequisites

Install the following before running the project locally:

- **Node.js** 20 LTS or newer (required by Angular 21 / NX 22)
- **JDK 21**
- **Maven 3.9+** (the backend uses a globally installed Maven; there is no wrapper)
- **Docker** and **Docker Compose**

## Getting Started (Local Development)

```bash
# 1. Clone and enter the repository
git clone <repository-url> sumicare
cd sumicare

# 2. Create your local environment file
cp .env.example .env        # then fill in the required values

# 3. Start Redis (the only service the compose file defines)
docker compose up

# 4. Provide PostgreSQL and run the Spring Boot API separately
#    - point DB_URL at your Postgres instance
#    - run the API via Maven (mvn spring-boot:run in apps/sumicare-api)
#      or the multi-stage apps/sumicare-api/Dockerfile, on :8080

# 5. In a second terminal, install workspace dependencies
npm install

# 6. Start the Angular dev server (proxies /api and /ws to the backend)
npm start                   # nx serve sumicare-web, http://localhost:4200
```

`docker compose up` starts Redis only; the compose file does not define a `db` (PostgreSQL) or
`api` service. Provide a PostgreSQL instance yourself and run the API via Maven or the
`apps/sumicare-api/Dockerfile`. The API listens on `http://localhost:8080`, and the dev server
proxies `/api` and `/ws` to it via `apps/sumicare-web/proxy.conf.json`. Liquibase applies all schema
migrations automatically on API startup. A helper script, `start-api.ps1`, is provided for running
the backend on Windows.

On first run, a default superadmin is seeded. Sign in to the internal system with:

- **Username:** `superadmin`
- **Password:** `ChangeMe!12345`
- **Organization:** `lasema`

Change this password immediately in any shared or deployed environment.

## Environment Variables

All variables are defined in `.env.example` (copy it to `.env`). Never commit real values.

| Variable | Description |
|---|---|
| `SERVER_PORT` | Internal port the Spring Boot app binds to. |
| `API_PORT` | Host port mapped to the API container (default 8080). |
| `DB_URL` | JDBC URL of the PostgreSQL database. |
| `DB_USERNAME` | Database username. |
| `DB_PASSWORD` | Database password. |
| `HIBERNATE_TIME_ZONE` | JDBC time zone (e.g. `Asia/Manila`). |
| `POSTGRES_DB` | Database name for the local Docker Postgres. |
| `POSTGRES_USER` | Username for the local Docker Postgres. |
| `POSTGRES_PASSWORD` | Password for the local Docker Postgres. |
| `REDIS_URL` | Redis connection URL. |
| `JWT_SECRET` | Signing secret for JWT access/refresh tokens. |
| `JWT_EXPIRY_MS` | Access-token lifetime in milliseconds. |
| `JWT_REFRESH_EXPIRY_MS` | Refresh-token lifetime in milliseconds. |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins (`*` in development). |
| `EMAIL_PROVIDER` | Email transport to use (`smtp` or Brevo). |
| `EMAIL_FROM` | From address for outbound email. |
| `EMAIL_FROM_NAME` | Display name for outbound email. |
| `BREVO_API_KEY` | Brevo API key (optional email provider). |
| `SMTP_HOST` | SMTP host (optional). |
| `SMTP_PORT` | SMTP port (optional). |
| `SMTP_USERNAME` | SMTP username (optional). |
| `SMTP_PASSWORD` | SMTP password (optional). |
| `APP_PUBLIC_BASE_URL` | Public website base URL used for all email and QR links. |
| `PAYMONGO_SECRET_KEY` | PayMongo secret key. |
| `PAYMONGO_PUBLIC_KEY` | PayMongo public key. |
| `PAYMONGO_WEBHOOK_SECRET` | Secret used to verify PayMongo webhook signatures (required). |
| `PAYMONGO_MOCK_MODE` | When `true`, payments are mocked for local development. |

## Project Structure

```
sumicare/
├── apps/
│   ├── sumicare-web/                  Angular 21 frontend (standalone components, signals, Tailwind)
│   │   └── src/
│   │       ├── app/features/          Public, auth, and internal feature modules (lazy-loaded)
│   │       ├── app/core/              Guards, interceptors, auth, loading
│   │       ├── app/shared/            Shared components and directives
│   │       ├── assets/logos/          Brand assets (SumiCare and partner logos)
│   │       └── environments/          Build-time configuration
│   └── sumicare-api/                  Spring Boot 3.2 (Java 21) backend
│       └── src/main/
│           ├── java/com/sumicare/     Bounded-context modules (controller/service/repository/domain)
│           └── resources/db/changelog/  Liquibase changelogs
├── libs/
│   ├── shared-types/                  TypeScript DTOs/types shared with the web app
│   └── ui/                            Reusable Angular UI primitives
├── docker-compose.yml                 Local dev: Redis only (provide Postgres and run the API separately)
├── nx.json                            NX workspace configuration
└── .env.example                       Canonical environment variable list
```

## Module Overview

| Module | Responsibility |
|---|---|
| `auth` | JWT issuance, Spring Security config, login/logout/refresh, MFA. |
| `user` | User CRUD, role assignment, permissions, password resets. |
| `organization` | Per-organization branding, colors, and fonts. |
| `booking` | Appointment scheduling, reservation types, walk-ins, session time management. |
| `therapist` | Therapist profiles, decking algorithm, skip and backup handling. |
| `shift` | Shift definitions and shift-therapist assignments. |
| `room` | Room and bed allocation, occupancy, gender-segregation rules. |
| `transaction` | Treatment-slip creation and digitization, session records, commissions. |
| `pos` / `cashier` | Payment processing, receipts, transaction ledger, cashier reconciliation. |
| `report` | Cutoff, day, monthly, commission, and decking reports with CSV export. |
| `recommendation` | Weighted-scoring recommendation engine for massage services. |
| `client` | Optional, non-critical client accounts for usage patterns and vouchers. |
| `notification` | STOMP/WebSocket broker and topic broadcasting from Redis state. |
| `audit` | Immutable audit logs per action per user for non-repudiation. |

## User Roles

Access follows a strict hierarchy: `SUPERADMIN > ADMIN > MANAGER > RECEPTIONIST`.

| Role | Access and responsibilities |
|---|---|
| Superadmin | Full access, including managing Admin accounts. |
| Admin | Everything plus user management and audit logs (cannot manage other Admins). |
| Manager | Receptionist functions plus reports, analytics, and user management below their level. |
| Receptionist | Booking, room and therapist assignment, treatment slips, and cashier operations. |
| Public User | Unauthenticated client using the public booking website (browse, book, recommend, cancel, feedback). |

## API Overview

The backend exposes a REST API over HTTP under the base path `/api`. Public endpoints live under
`/api/public/**` and are open; all other endpoints require a JWT access token presented as a
`Bearer` token, with method-level `@PreAuthorize` enforcing role-based authorization. Authentication
returns a short-lived access token plus an httpOnly refresh-token cookie; unauthenticated requests
receive `401` and forbidden ones `403`, with all errors flowing through a global exception handler.
Real-time updates (room occupancy, decking, reservations) are delivered over a STOMP WebSocket
endpoint at `/ws`.

## Database and Migrations

The system uses **PostgreSQL 16** as its primary store, with the schema managed entirely by
**Liquibase**; changelogs live under `apps/sumicare-api/src/main/resources/db/changelog/` and run
automatically on API startup (`hibernate.ddl-auto=validate`, so the schema is never auto-generated).
**Redis 7** holds live operational state: the therapist decking queue (sorted set), per-bed room
occupancy (hashes), the JWT revocation deny-list (keys with TTL), login rate-limit counters, and the
WebSocket session registry.

## Deployment

In the evaluation environment, SumiCare is deployed on **Railway** as separate services — the
Angular frontend, the Spring Boot backend, and Redis — backed by a managed **PostgreSQL** host
(Supabase historically; the project now also uses a Railway-hosted Postgres). The backend image is
the multi-stage `apps/sumicare-api/Dockerfile`, used for both local development and production.
Production requires, at minimum, `APP_PUBLIC_BASE_URL` (the public site used for all generated
links), `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, the `DB_*` and `REDIS_URL` connection settings, and the
`PAYMONGO_*` keys (webhook signature verification is mandatory).

## Commit Convention

Commits follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
specification (`type(scope): description`). For example:

```
feat(booking): add email-verified public booking cancellation
```

## License

No open-source license is currently distributed with this repository. SumiCare is an academic thesis
project; all rights are reserved by the authors unless and until a `LICENSE` file is added to the
repository root.
