# SumiCare

**SumiCare: A Web-Based Spa Operations Management Platform for New Lasema Spa Jjimjilbang.**

SumiCare computerizes the previously paper-based operational workflows of wellness enterprises. It is composed of two surfaces sharing one backend and one database: a **public booking website** (open endpoints) and an **internal operations system** (role-restricted, JWT-authenticated). The platform was designed against New Lasema Spa Jjimjilbang's operational reality but ships as a generalized, multi-tenant product — every organization configures its own logo, color scheme, services, shifts, rooms, and recommendation weights.

---

## Repository layout

```
sumicare/
├── apps/
│   ├── sumicare-web/          Angular 17 frontend (standalone components, signals, Tailwind)
│   └── sumicare-api/          Spring Boot 3.2 (Java 21) backend
├── libs/
│   ├── shared-types/          TypeScript DTOs/types shared with the web app
│   └── ui/                    Reusable Angular UI primitives
├── docs/
│   └── sumicare-erd.drawio.xml   Importable ERD (drag into draw.io / app.diagrams.net)
├── reference/
│   └── magic-patterns-react/  Original React Magic Patterns export, kept as a design reference
├── docker-compose.yml         Local dev: PostgreSQL 16, Redis 7, API
├── nx.json / package.json     NX workspace configuration
├── .env.example               Copy to .env before running
└── README.md
```

---

## Quick start (local development)

You need **Docker Desktop**, **Node 20+**, **Java 21**, and **Maven 3.9+** (or use the Maven inside the API container).

1. **Clone and configure environment.**
   ```bash
   cp .env.example .env
   ```
   Edit `.env` if you want to override the JWT secret, database password, etc. The defaults work for local development.

2. **Bring up the database, Redis, and API.**
   ```bash
   docker compose up -d db redis
   docker compose up --build api
   ```
   On first boot, Liquibase runs every changeset in `apps/sumicare-api/src/main/resources/db/changelog/`, creating all tables and seeding roles, permissions, the default `lasema` organization, the 14 services, the 5 shifts, the recommendation quiz schema, and reference weights. A default Superadmin (`superadmin / ChangeMe!12345`) is created in code by `SuperadminBootstrap` on the first run.

3. **Install web dependencies and serve the Angular app.**
   ```bash
   npm install
   npm start            # runs nx serve sumicare-web on http://localhost:4200
   ```

4. **Sign in.**
   - Public booking site: <http://localhost:4200>
   - Staff sign-in: <http://localhost:4200/login>
   - Default credentials: `superadmin / ChangeMe!12345` — **change this immediately** via the Users module.

### Running outside Docker

- API only: `cd apps/sumicare-api && ./mvnw spring-boot:run` (after `docker compose up -d db redis`)
- Build a JAR: `cd apps/sumicare-api && ./mvnw -DskipTests package` → `apps/sumicare-api/target/sumicare-api.jar`

### Production deployment (on-premise)

Same `docker-compose.yml` is the production deployment vehicle. Set production values in `.env` — at minimum `JWT_SECRET`, `POSTGRES_PASSWORD`, `BIOMETRICS_SHARED_KEY`, and `CORS_ALLOWED_ORIGINS=https://your-domain.example`. Front the stack with nginx for HTTPS termination and WebSocket upgrade headers.

---

## Environment variables (.env)

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection from the API |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Used by the `db` container |
| `REDIS_URL` | e.g. `redis://redis:6379` |
| `JWT_SECRET` | HS256 signing secret, min 32 chars |
| `JWT_EXPIRY_MS` | Access token lifetime (default 900000 = 15 min) |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token lifetime (default 7 days) |
| `CORS_ALLOWED_ORIGINS` | `*` during development; switch to a specific origin in prod |
| `BIOMETRICS_SHARED_KEY` | Shared header secret for the biometrics webhook |
| `ANTHROPIC_API_KEY` | Optional. Enables natural-language rationale on recommendations |
| `API_PORT` | Host port for the API container (default 8080) |

The `.env` file is git-ignored. Only `.env.example` is committed.

---

## Default seed data

| Resource | Default |
|---|---|
| Organization | `lasema` (display name *New Lasema Spa Jjimjilbang*) |
| Roles | SUPERADMIN, ADMIN, MANAGER, RECEPTIONIST, STAFF |
| Permissions | MANAGE_USERS, MANAGE_ADMINS, VIEW_AUDIT_LOGS, VIEW_REPORTS, EXPORT_REPORTS, OPERATE_POS, MANAGE_BOOKINGS, MANAGE_DECKING, MANAGE_CONTENT, MANAGE_BRANDING |
| Shifts | 7am–5pm, 12n–10pm, 2:30pm–12:30am, 5pm–3am, 7:30pm–7:30am |
| Services | All 14 from the catalogue plus a VIP package (jacuzzi + 1hr massage) |
| Quiz | 5 questions with weighted mappings to several services |

---

## System overview

### High-level architecture

```
┌─ Angular SPA (apps/sumicare-web) ──────────────────────────────┐
│  Public site: /, /services, /recommendation, /book             │
│  Internal:    /app/dashboard, /reception, /decking, /pos,      │
│               /reports, /users, /audit, /branding              │
│  AuthInterceptor + AuthGuard + RoleGuard + StompService        │
└────────────────────────────┬───────────────────────────────────┘
                             │ HTTPS REST + STOMP/WS
┌────────────────────────────▼───────────────────────────────────┐
│  Spring Boot 3.2 API (apps/sumicare-api)                       │
│  Modular monolith. Each module has controller/service/         │
│  repository/domain layers under com.sumicare.<module>.         │
│   auth · user · organization · therapist · shift · attendance  │
│   biometrics · room · booking · service_catalogue              │
│   transaction · pos · report · notification · recommendation   │
│   client · audit · content                                     │
└──────────┬───────────────────────────┬─────────────────────────┘
           │                           │
   PostgreSQL 16                  Redis 7
   (durable: all entities)        (volatile operational state)
   Liquibase-managed schema       decking ZSET, room HSET,
                                  JWT deny-list, rate limit
```

### Backend modules

| Module | Responsibility |
|---|---|
| `auth` | JWT issuance/refresh/logout, Spring Security config, CORS, BCrypt cost 12, JWT deny-list in Redis, login rate-limit in Redis |
| `user` | Users, roles, permission overrides, role-seeded RBAC, default Superadmin bootstrap |
| `organization` | Multi-tenant root: per-org slug, display name, logo, color scheme, theme |
| `therapist` | Therapist profiles, decking algorithm (Redis Sorted Set), skip mode, requested-flag, backup insertion |
| `shift` | 5-shift definitions, shift-therapist assignments, shift resolution by current time |
| `attendance` | Clock-in/out, day-off, absence remarks |
| `biometrics` | `BiometricsAdapter` interface + webhook implementation. Shared-key authenticated. On clock-in: resolve therapist, record attendance, find active shift, append to decking, broadcast |
| `room` | Rooms, beds, gender lock per row, Redis-backed live occupancy hash |
| `service_catalogue` | All 14 massage types and the VIP package, fixed-rate flags, tandem flag, per-org pricing |
| `booking` | Public + internal booking, 15-minute prep buffer, hard/soft reservation, session start/end, manual time adjustment, extension |
| `transaction` | Treatment slip generation per session, commission ledger |
| `pos` | Payment processing (CASH/GCASH/CREDIT/DEBIT), receipt numbers, immutable `transaction_ledger` inserts, cashier shift open/close with variance |
| `report` | Cutoff/day/monthly aggregation, Excel export via Apache POI |
| `notification` | STOMP/WebSocket broker on `/ws`, topics `/topic/decking-updates/{orgId}` and `/topic/room-updates/{orgId}`, Redis WS session registry |
| `recommendation` | Weighted scoring engine over `recommendation_weights`, optional Anthropic call for natural-language rationale, mandatory disclaimer |
| `client` | Optional client accounts, nickname uniqueness check, consent flags |
| `audit` | `AuditInterceptor` records every successful state-modifying request to `audit_logs` (immutable, async write) |
| `content` | Editable public website content blocks per organization |

### Frontend feature modules

Lazy-loaded standalone Angular components (signals + OnPush by default):

- **Public:** landing page (reads `website_content_blocks`), service catalogue, recommendation quiz, booking form with consent checkbox.
- **Auth:** login form. Token returned by `/api/auth/login` is held in memory (`AuthService.session` signal). Refresh token lives in an httpOnly cookie.
- **Internal shell:** sidebar nav, role-driven; sign-out clears the cookie.
- **Reception:** room map. White available, gray male-occupied, pink female-occupied. Locker number, therapist nickname, elapsed time per occupied bed.
- **Decking:** therapist lineup with the legend below.
- **POS:** payment form for an active session.
- **Reports:** date-range cutoff report viewer + Excel download.
- **Users / Audit / Branding:** admin tools, role-guarded.

### Decking legend (matches the paper logsheet)

| Glyph | Meaning |
|---|---|
| ♥ | Therapist was specifically requested by the client |
| ★ | Scrub massage (Salt Glow, Milk Bath, Dae Mi DI) |
| – | Ordinary massage |
| ◯ | Backup therapist, manually inserted |

The flag is set per-therapist via `POST /api/decking/{therapistId}/flag?flag=REQUESTED|SCRUB|ORDINARY|BACKUP` and rendered on `/app/decking`.

### Key business rules enforced in code

- **15-minute prep buffer.** `BookingService.toBookingResponse` always returns `effectiveStartAt = scheduledAt + 15min` and `projectedEndAt = effectiveStartAt + service.durationMinutes`.
- **Latest-shift-first decking.** `DeckingService.prependShift` writes new-shift therapists with scores below the lowest existing score so they sort to the front of the ZSET.
- **Requested therapist preserves position.** `servedRequested` does not rotate to the back; the requested service is counted as additional work.
- **Skip mode caps at 30 minutes.** `DeckingController.skip` clamps the requested duration to 30 minutes max; cancel-skip removes the Redis key.
- **Backup therapists never auto-enter the lineup.** Only `POST /api/decking/backup/{therapistId}?position=N` inserts them, and the `BACKUP` flag is set automatically.
- **Treatment slips never carry the client's real name.** `TreatmentSlipService.generateForSession` only ever copies `Booking.clientNickname`.
- **Immutable transaction ledger.** `PosService.processPayment` inserts a `PAYMENT_RECEIVED` row in `transaction_ledger` on every payment; the table has no update or delete endpoints.
- **Tandem commission split.** `PosService.recordCommissions` halves the service commission between the two therapists when `requires_two_therapists` is true.
- **Extension commission.** Half-hour blocks at half the hourly rate (₱60 per 30 min on top of the standard ₱120/hr).
- **VIP fixed at 2 hours.** The VIP service in the catalogue is `duration_minutes=120` and cannot be extended through the standard rule.
- **Recommendation disclaimer always rendered.** `RecommendationExplainerService.disclaimer()` is included unconditionally in every public response.

### Multi-tenant customization (general version)

Every operational table is scoped by `organization_id`. To onboard another spa:

1. Insert a new row in `organizations` (slug, display name, primary/secondary/accent colors, optional logo URL).
2. Insert that org's services, shifts, rooms, beds, recommendation weights.
3. Create a Superadmin user for that org.

Inside the app:

- **`/app/branding`** — Manager+ can edit logo URL, display name, and the three colors live. The frontend reads `/api/public/branding/{slug}` on app startup and writes the values to CSS custom properties (`--sumi-primary`, `--sumi-secondary`, `--sumi-accent`), so all Tailwind utilities like `bg-[var(--sumi-primary)]` immediately reflect the change.
- **`/app/users`** — Admin/Manager creates RECEPTIONIST/MANAGER/STAFF accounts.
- **Public website content** — Manager+ can edit `website_content_blocks` per organization and they appear on the landing page.

### Recommendation engine

5 questions: pressure, focus area, texture (dry/oil), duration, primary goal. Each (question_code, option_code) pair maps to a list of (service_id, weight) entries in `recommendation_weights`. `RecommendationEngine.score(answers)` accumulates weights per service and returns them sorted descending. The top result is the primary recommendation; the next two are alternatives.

If `ANTHROPIC_API_KEY` is set, `RecommendationExplainerService` calls the Anthropic Messages API with `claude-haiku-4-5` to generate a short relaxation-framed rationale. The API call **never** affects scoring — if it fails, the recommendation is returned without a rationale. The fixed disclaimer (*"SumiCare's recommendations are for relaxation purposes only and do not constitute medical advice."*) is rendered on every response.

### Real-time via STOMP

The API runs a SimpleBroker on `/topic` and `/user`, exposes `/ws` (with SockJS fallback). Subscribe per organization:

- `/topic/decking-updates/{organizationId}` — every queue mutation
- `/topic/room-updates/{organizationId}` — every bed occupy/release

`WebSocketSessionRegistry` tracks active session IDs in Redis Sets so the registry survives across stateless API replicas.

### Biometrics integration

`POST /api/biometrics/clock-in` accepts `{ staffNumber, timestamp, deviceId }` with header `X-Biometrics-Key: <BIOMETRICS_SHARED_KEY>`. The webhook adapter resolves the staff number → therapist, records attendance, finds the currently active shift via `ShiftService.resolveActiveShiftFor`, and appends the therapist to the decking (which broadcasts on `/topic/decking-updates/{orgId}`). Polling and direct-DB adapters can be added by implementing `BiometricsAdapter`.

---

## ERD

The schema is documented in `docs/sumicare-erd.drawio.xml`. Open <https://app.diagrams.net> → File → Open from Device → pick the file. Or in Obsidian / VS Code with the draw.io extension.

The diagram covers:

- **Identity & access:** organizations, roles, users, permissions, role_permissions, user_permission_overrides, audit_logs.
- **Therapist & shift:** therapists, shifts, shift_assignments, therapist_attendance, decking_state.
- **Operations:** rooms, beds, clients, services_catalogue, bookings, sessions, treatment_slips.
- **POS & financials:** transactions, transaction_ledger (immutable), commissions, cashier_shifts.
- **Reports:** cutoff_reports, day_reports, monthly_reports.
- **Public/recommendation:** recommendation_questions, recommendation_options, recommendation_weights, recommendations_log, feedback, vouchers, website_content_blocks.

---

## Hard constraints applied throughout

- **No comments** in any file (Java, TypeScript, HTML, XML, YAML). Code is self-documenting via naming.
- **No emojis** anywhere in templates, strings, or UI copy.
- **Never the word "automate"** — replaced everywhere with "computerize".
- **All identifiers and UI copy in English.**
- **CORS** defaults to `*` with `allowCredentials=false`. Switching to a specific domain automatically flips `allowCredentials=true` (in `SecurityConfig.corsConfigurationSource`).
- **Clients identified only by nickname.** Real names are never stored in operational tables; the `clients` table holds nickname/email/Facebook handle only.
- **Staff TV display:** out of scope. The `notification` module is structured so `/topic/staff-callout` can be added later without breaking existing clients.
- **POS:** SumiCare's own `pos` module handles cashier operations. No integration with La Sema's external BIR-registered POS.

---

## Common workflows

### Receptionist takes a walk-in

1. POST `/api/bookings` with the client's nickname and chosen service → returns a booking with `effectiveStartAt` already at +15 min.
2. POST `/api/bookings/{bookingId}/sessions` with primaryTherapistId, roomId, bedId, and `specificallyRequested=true|false` → starts the session, marks the bed occupied in Redis, broadcasts `/topic/room-updates`.
3. POST `/api/treatment-slips/from-session/{sessionId}` → generates a digital treatment slip with TSN.
4. POST `/api/sessions/{sessionId}/end` → closes the session, releases the bed.
5. POST `/api/pos/payments` with the session id, subtotal, payment method → records a transaction and an immutable ledger entry, computes commissions.

### Shift change

When the next shift's first therapist clocks in, the biometrics webhook calls `DeckingService.appendToBack`. To explicitly prepend an entire shift (the "latest shift first" rule), call `DeckingService.prependShift(orgId, shiftId, therapistIds)` from your scheduled task at the shift's start time.

### End-of-day report

Manager hits `/app/reports`, picks a date range, clicks Excel — `GET /api/reports/cutoff/export?from=...&to=...` streams an `.xlsx` built with Apache POI.

---

## Notes on what is fully wired vs. scaffolded

This repository is a complete project scaffold against the SumiCare spec. The following are **fully functional**:

- Liquibase schema + seed data
- Auth (login/refresh/logout, JWT, BCrypt, deny-list, rate limit, CORS)
- User module + Superadmin bootstrap
- Organization branding (CRUD + public read)
- Therapist + decking (Redis ZSET, skip, requested, backup, flag, broadcast)
- Booking + session lifecycle with 15-minute buffer
- Treatment slip generation
- POS payment + immutable ledger + cashier shift open/close
- Cutoff report aggregation + Excel export
- WebSocket broker + Redis session registry
- Recommendation scoring engine + optional Anthropic rationale
- Audit interceptor on every state-modifying API call
- Biometrics webhook adapter
- Angular: login, role-guarded routing, public landing/services/recommendation/booking, internal dashboard/reception/decking/POS/reports/users/audit/branding

Areas **deliberately left as stubs** for the next iteration (the foundation is in place, the entities exist, the routes exist):

- Bulk therapist/shift/room admin CRUD UIs (entities + repositories exist; only the read views are wired in the Angular app)
- Day-report and monthly-report aggregation jobs (the tables exist; currently only the cutoff aggregation runs synchronously on demand)
- Voucher redemption flow (table + entity in schema)
- Polling and database biometrics adapter implementations (interface exists)
- Tests beyond skeleton — JUnit + Testcontainers, and Karma/Jasmine — are configured in pom.xml/project.json but no specs are committed

Add to taste; the seams are designed for it.
