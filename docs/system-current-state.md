# Interview Scheduler System — Current State

> Snapshot date: 2026-08-31
>
> Repository reference: the commit containing this document
>
> Application version: `0.0.1-SNAPSHOT`

This document describes the behavior currently implemented in the repository. It is a current-state reference, not a future-state design or production-readiness approval.

## 1. Executive summary

The Interview Scheduler System is a server-side recruitment operations application implemented as a Spring Boot and Vaadin modular monolith. It supports the administrator- and recruiter-managed lifecycle from applicant intake through interview scheduling, booking, attendance, evaluation, offer issuance, and a terminal hiring decision.

The system currently provides:

- Organization-wide administration of branches, recruiters, clients, position openings, schedules, notification settings, and notification templates.
- Recruiter work queues and branch-scoped applicant, booking, evaluation, and hiring operations.
- A consolidated applicant profile with derived current recruitment state, safe quick actions, and a chronological recruitment timeline.
- Capacity-controlled, explicit initial/final/client interview scheduling and booking workflows.
- Cancellation and rescheduling with history records.
- Attendance, no-show, evaluation, offer, hire, decline, and withdrawal transitions.
- Account lockout, mandatory password changes, administrator-initiated password resets, session invalidation, and security audit records.
- Best-effort email notifications delivered after successful business transactions.
- Flyway-managed MySQL schema migrations and an isolated H2 test environment.
- GitHub Actions continuous integration running the clean Maven test suite on Java 25.

The application does not currently provide applicant self-service, scheduled interview reminders, SMS delivery, durable notification retries, or a remote REST API.

## 2. Technology baseline

| Area | Current implementation |
|---|---|
| Language | Java 25 |
| Application framework | Spring Boot 4.0.6 |
| UI | Vaadin Flow 25.1.5, Aura theme |
| Persistence | Spring Data JPA and Hibernate |
| Runtime database | MySQL |
| Test database | H2 in MySQL compatibility mode |
| Schema management | Flyway, with separate MySQL and H2 migration sets |
| Security | Spring Security with Vaadin security integration |
| Notifications | Spring Mail with asynchronous in-process delivery |
| Build | Maven Wrapper |
| CI | GitHub Actions on pull requests and pushes to `main` |
| Supporting dependencies | Spring Validation, Actuator, Thymeleaf, Lombok |

The versions declared in [`pom.xml`](../pom.xml) are authoritative.

## 3. Architecture

The application is a package-by-feature modular monolith under `com.company.iss`.

```text
Vaadin view/dialog
        ↓
feature service
        ↓
Spring Data repository
        ↓
JPA entity / Flyway-managed database
```

Cross-feature workflows use services and ID-only domain events. Database mutations remain synchronous and transactional; notification listeners run after commit and reload committed state in separate read-only transactions.

### Feature modules

| Module | Responsibility |
|---|---|
| `applicant` | Applicant records, branch/position assignment, applicant search/grid pagination, and the recruitment-journey read model |
| `auth` | Users, roles, authentication, account lifecycle, password changes/resets, sessions, security audit |
| `booking` | Interview bookings, capacity allocation, confirmation, attendance, no-show, cancellation, rescheduling |
| `branch` | Branch master data |
| `client` | Client master data |
| `dashboard` | Administrator dashboard and recruiter workbench |
| `evaluation` | Interview scores, results, and applicant/booking result transitions |
| `hiring` | Offers, terminal hiring decisions, headcount updates, and hiring audit history |
| `notification` | Settings, templates, rendering, email dispatch, and after-commit listeners |
| `position` | Position openings, employment type, headcount, and recruitment counters |
| `recruiter` | Recruiter account and branch administration |
| `schedule` | Interview schedules, recruiter assignment, overlap checks, modes, and capacity |
| `shared` | Base entity, shared business exception, main layout, and safe UI error handling |
| root `config` | Cross-cutting security, async execution, and startup logging |

All persistent entities inherit optimistic locking through `BaseEntity.@Version` unless specifically documented otherwise.

## 4. Users, roles, and access

Three roles exist in the model:

- `ADMIN` — organization-wide administration and operational access.
- `RECRUITER` — operational access restricted to the recruiter's assigned branch.
- `APPLICANT` — defined in the role enum but not currently given an application workflow or authorized landing route.

The authenticated root route forwards administrators to `/dashboard` and recruiters to `/workbench`. Navigation is role-aware, while services and repository queries enforce the actual data boundary.

### Route matrix

| Route | Screen | Access |
|---|---|---|
| `/login` | Login | Anonymous |
| `/reset-password` | Signed-link password reset | Anonymous |
| `/` | Role-based landing redirect | `ADMIN`, `RECRUITER` |
| `/dashboard` | Administrator dashboard | `ADMIN` |
| `/workbench` | Recruiter workbench | `RECRUITER` |
| `/branches` | Branch management | `ADMIN` |
| `/recruiters` | Recruiter management | `ADMIN` |
| `/clients` | Client management | `ADMIN` |
| `/positions` | Position openings | `ADMIN` |
| `/scheduling` | Schedule management | `ADMIN` |
| `/notification-settings` | Notification configuration | `ADMIN` |
| `/notification-templates` | Notification templates | `ADMIN` |
| `/applicants` | Applicant management | `ADMIN`, `RECRUITER` |
| `/applicants/:applicantId` | Applicant profile and recruitment timeline | `ADMIN`, `RECRUITER` |
| `/bookings` | Booking management | `ADMIN`, `RECRUITER` |
| `/evaluations` | Interview evaluations | `ADMIN`, `RECRUITER` |
| `/hiring-decisions` | Offers and final hiring decisions | `ADMIN`, `RECRUITER` |
| `/profile` | Current-user profile | `ADMIN`, `RECRUITER` |
| `/change-password` | Mandatory or voluntary password change | `ADMIN`, `RECRUITER` |

### Branch authorization

- Applicants have a required authoritative branch.
- Recruiters must have a valid branch before performing operational work.
- Administrators may operate across branches.
- Applicant and booking grids apply branch scope in database fetch and count queries.
- Booking grid visibility follows `booking.applicant.branch`, not the schedule branch.
- Cross-branch identifiers are rejected by mutation services rather than filtered only in the UI.
- Applicant profile URLs authorize the applicant root record before any related history query. Recruiters receive access only for applicants whose authoritative branch matches their own.

## 5. Domain model and workflow

### Applicant lifecycle

Applicant statuses currently defined are:

```text
NEW, SCREENING, SCHEDULED, INTERVIEWED, PASSED, OFFERED,
OFFER_DECLINED, FAILED, WITHDRAWN, HIRED,
FOR_FINAL_INTERVIEW, FOR_CLIENT_INTERVIEW, ON_HOLD
```

Important behavior:

- New applicants start as `NEW` and active.
- Applicants are assigned to a branch and position opening.
- Recruiter-created applicants inherit the recruiter's branch; administrators select the branch.
- Applicant email uniqueness is enforced.
- Position application counters are updated when applicants are created or moved.
- Applicants with a hiring decision cannot be reassigned to another branch or position.
- Applicant activation and deactivation are supported.

The applicant grid uses database-backed keyword/status filtering and lazy 50-row pages. Keyword matching covers first name, last name, and email. Results are ordered by last name, first name, and ID.

### Applicant profile and recruitment timeline

The applicant grid links to a dedicated profile that combines an applicant summary, the derived current recruitment state, current appointment, context-sensitive quick actions, and an oldest-first recruitment timeline. The profile is a read model and does not add persistent workflow state.

The current/next interview stage is derived from the applicant status, the current booking, and the central booking-stage eligibility policy. The booking's immutable `interviewStage` remains the historical source. Current appointments include active `BOOKED`, `CONFIRMED`, or legacy `RESCHEDULED` bookings on an active, non-cancelled schedule; cancelled and no-show bookings are never displayed as upcoming appointments. Hiring quick actions use the same passed applicant/booking/evaluation eligibility predicate as the hiring workflow. Mutation actions reuse the existing booking and evaluation dialogs or navigate to the booking/hiring workspaces, whose services reauthorize and revalidate every operation.

The timeline uses only timestamps already reliable in the domain: `Applicant.createdAt`, `Booking.bookedDateTime`, `BookingRescheduleHistory.rescheduledAt`, `InterviewEvaluation.evaluationDate`, and immutable `HiringDecisionAudit.occurredAt`. It does not fabricate dated confirmation, cancellation, attendance, or no-show events because those transitions currently have no dedicated persisted timestamp. Reschedule history identifies the source and destination schedule records, but schedule slot fields are mutable and are therefore not immutable historical snapshots.

The recruiter workbench includes the explicit interview stage and a link to the same authorized applicant profile.

### Position openings

Position states are `OPEN`, `ON_HOLD`, `FILLED`, `CLOSED`, and `CANCELLED`.

Position openings track required headcount and recruitment counters, including applications, interviews, passes, and hires. Employment types are `FULL_TIME`, `PART_TIME`, `CONTRACTUAL`, `PROJECT_BASED`, and `SEASONAL`.

### Scheduling

Schedules contain a branch, recruiter, date, start/end time, interview mode, slot capacity, booked count, and status.

Implemented rules include:

- End time must be after start time.
- Capacity must be positive.
- Booked count cannot exceed capacity.
- The selected recruiter must belong to the schedule branch.
- A recruiter cannot have overlapping schedules on the same date.
- Schedule statuses are `OPEN`, `FULL`, `CLOSED`, and `CANCELLED`.
- Interview modes are `ONSITE`, `ONLINE`, and `PHONE`.
- Bulk schedule creation is available.

### Booking lifecycle

Booking statuses are:

```text
BOOKED, CONFIRMED, ATTENDED, PASSED, FAILED, NO_SHOW,
CANCELLED, RESCHEDULED, FOR_FINAL_INTERVIEW,
FOR_CLIENT_INTERVIEW, ON_HOLD
```

Every booking also stores an immutable `InterviewStage` snapshot:

```text
INITIAL, FINAL, CLIENT
```

Applicant lifecycle status and interview stage are separate concepts. Scheduling still changes the
applicant to `SCHEDULED`, while the booking preserves whether the operational appointment is an
initial, final, or client interview. New and screening applicants are eligible only for `INITIAL`;
`FOR_FINAL_INTERVIEW` applicants only for `FINAL`; and `FOR_CLIENT_INTERVIEW` applicants only for
`CLIENT`. Cancelled and no-show appointments may be replaced only at their previous stage. Ambiguous
or terminal states, including `ON_HOLD`, `PASSED`, `FAILED`, and hiring states, cannot be booked.

Implemented booking rules and actions:

- Only active applicants and active, open, non-full schedules can be booked.
- An applicant cannot have multiple active bookings.
- Capacity allocation uses database locking rather than an unprotected read/increment sequence.
- Successful creation updates the schedule count and applicant state atomically.
- Bookings can be confirmed, marked attended, marked no-show, cancelled, or rescheduled according to their source state.
- Cancellation releases schedule capacity and updates related state in one transaction.
- Rescheduling transfers capacity between locked schedules and appends a history record.
- Created, confirmed, cancelled, and rescheduled notifications are published as ID-only events and delivered after commit.
- Rescheduling preserves the booking's immutable interview stage.

Schedules remain generic branch/recruiter capacity windows and do not own an interview stage. The
booking dialog displays the one inferred eligible stage read-only, and the service revalidates that
stage after locking the applicant and schedule.

The booking grid uses database-backed keyword/status/date filtering and lazy 50-row pages. Keyword matching covers booking reference and applicant name. Results are ordered by schedule date, start time, and ID in descending order.

### Evaluation

Only an `ATTENDED` booking can be evaluated, and each booking can have at most one evaluation. Communication, technical, and attitude scores must each be between 1 and 10.

Evaluation results are:

- `PASS`
- `FAIL`
- `FOR_FINAL_INTERVIEW`
- `FOR_CLIENT_INTERVIEW`
- `ON_HOLD`

Creating an evaluation updates the evaluation record, booking status, applicant status, and position counters transactionally.

Evaluation results are constrained by the booking stage:

- `INITIAL` permits pass, fail, progression to final or client, and on-hold.
- `FINAL` permits pass, fail, progression to client, and on-hold; another final progression is rejected.
- `CLIENT` permits only pass, fail, and on-hold; further stage progression is rejected.

A `PASS` at any valid stage produces the existing passed applicant/booking/evaluation triad required
for hiring. Applicants awaiting final or client interviews are not hiring-eligible.

### Hiring

The hiring module owns offers and terminal hiring outcomes.

Offer eligibility requires all of the following:

- The applicant is active and `PASSED`.
- The related booking is `PASSED`.
- The matching evaluation result is `PASS`.
- The position is active, `OPEN`, and has remaining headcount.
- No hiring decision already exists for the applicant or evaluation.

Hiring decision states are `OFFERED`, `HIRED`, `DECLINED`, and `WITHDRAWN`.

Implemented behavior:

- Issuing an offer changes the applicant to `OFFERED` but does not reserve headcount.
- An outstanding offer can transition only to hired, declined, or withdrawn.
- Accepting an offer pessimistically locks the position, increments hired count exactly once, and marks the position `FILLED` when headcount is reached.
- Exact repeated terminal actions are idempotent; conflicting terminal actions are rejected.
- Every successful transition appends an immutable hiring audit record.
- Offer and hired notifications run after commit.

Re-offers, reversals, applicant self-service acceptance, and offer-time headcount reservation are not supported.

## 6. Dashboards and operational UX

### Administrator dashboard

The administrator dashboard provides organization-wide metrics, schedule summaries, upcoming interviews, activity information, and charted operational data.

### Recruiter workbench

The recruiter workbench provides branch-scoped queues and actions for upcoming interviews, attendance/no-show processing, pending evaluations, and related recruiter work.

### Grid scalability

Applicant and booking grids now use server-side pagination, database counts, and database filtering. Their queries use deterministic ordering and eager loading of displayed to-one relationships.

Other management and operational grids have not all been converted to lazy paging; several still load complete result lists and remain a future scalability concern.

## 7. Authentication and account security

### Authentication controls

- Passwords are BCrypt encoded.
- Public login errors do not reveal whether an account exists or whether it is inactive, locked, or using the wrong password.
- Five consecutive bad-password attempts lock an existing active account for 15 minutes.
- A successful login clears failure state and records login time.
- Inactive accounts cannot perform operational work.
- Administrators can activate, deactivate, and unlock recruiter accounts.

### Password policy

The shared policy requires:

- 15–64 Unicode characters.
- No more than 72 UTF-8 bytes.
- No password truncation.

Temporary-password users are forced to `/change-password` before entering operational routes.

### Password reset

Administrator-initiated recruiter password reset uses a signed, single-use link:

- Default expiry is 30 minutes.
- A new request invalidates the previous outstanding request.
- The database stores a random request identifier and SHA-256 token hash, not the bearer token.
- Password reset delivery occurs after commit and is best-effort.
- A successful change or reset invalidates known sessions and requires a new login.

The session registry is process-local. In a multi-instance deployment, session invalidation on one node does not immediately invalidate sessions registered only on another node.

### Security audit

Account security events include login success/failure, lock/unlock, password changes/resets, activation, and deactivation. Audit records are append-only through explicit appender APIs.

## 8. Notifications

Email notification templates are seeded idempotently for:

- Booking created
- Booking confirmed
- Booking cancelled
- Booking rescheduled
- Job offered
- Applicant hired
- Password reset

`INTERVIEW_RESULT` exists as an event value but has no default seeded template or automated delivery workflow.

Notification behavior:

- Email delivery must be enabled in notification settings.
- SMTP host, port, username, sender name, and company name are stored as non-secret settings.
- The SMTP password is supplied only through `SMTP_PASSWORD` at runtime.
- Booking, hiring, and reset listeners deliver only after the business transaction commits.
- Listeners reload state using stable IDs and contain delivery failures.
- Delivery is asynchronous, in-process, and best-effort.
- There is no durable outbox, retry queue, dead-letter handling, delivery audit, or exactly-once guarantee.
- SMS configuration is visible only as disabled/read-only metadata; runtime SMS delivery is unsupported.

## 9. Persistence and migrations

Flyway exclusively owns the schema. Hibernate runs with `ddl-auto=validate` and `open-in-view=false`.

MySQL runtime migrations and logically equivalent H2 test migrations currently cover:

| Version | Purpose |
|---|---|
| V1 | Baseline schema |
| V2 | Required applicant branch |
| V3 | Hiring decision workflow |
| V4 | Secure account lifecycle |
| V5 | Remove persisted notification secrets and disable SMS |

Migration locations:

- Runtime MySQL: `classpath:db/migration/mysql`
- Automated tests: `classpath:db/migration/h2`

Flyway clean is disabled. The production application requires migration version 6, and automated tests assert the expected migrated schema.

Audit/history state includes hiring decision audit, account security audit, and booking reschedule history. Their repositories expose explicit append/query APIs, and immutable history records cannot be updated or deleted through generic repository operations.

## 10. Runtime configuration

### Required runtime database variables

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### Notification and password-reset variables

- `SMTP_PASSWORD`
- `APP_BASE_URL`
- `ACCOUNT_RESET_TOKEN_SECRET`

### Optional controlled variables

- `ADMIN_EMAIL` and `ADMIN_PASSWORD` — opt-in first-administrator bootstrap; remove after use and rotate the credential.
- `SPRING_PROFILES_ACTIVE=dev` plus `DEMO_DATA_ENABLED=true` — both are required for deterministic development demo data.
- `FLYWAY_BASELINE_ON_MIGRATE` and `FLYWAY_BASELINE_VERSION` — controlled legacy-baseline procedure only; not normal defaults.

Spring Boot does not automatically load the repository's local `.env`. Automated tests must never source it.

Production defaults include a 30-minute HTTP session timeout, Flyway validation, disabled Flyway clean, disabled demo data, and Hibernate schema validation.

## 11. Demo and reference data

Notification settings and templates are idempotent reference data and may be created when missing.

Clients, positions, and applicants are demo data. Their loaders require both the `dev` profile and explicit demo-data enablement. They are deterministic and ordered by dependency. They must not run in production.

## 12. Testing and continuous integration

At this snapshot, the clean Java 25 suite contains **247 tests** with:

- 0 failures
- 0 errors
- 0 skipped

Coverage includes:

- Service validation and transactional workflows
- Repository queries, constraints, locking, and branch scope
- Route and service authorization
- Account lifecycle and password resets
- Flyway migrations and isolated H2 configuration
- Booking capacity, cancellation, rescheduling, attendance, and final-state behavior
- Evaluation and hiring transitions
- Asynchronous after-commit notification behavior
- Applicant and booking grid pagination, filtering, count parity, stable ordering, and branch isolation
- Notification templates/settings and user-safe UI boundaries

GitHub Actions runs `./mvnw clean test` using Temurin Java 25 for every pull request and push to `main`.

H2 in MySQL mode is a fast compatibility test; it is not proof that MySQL-specific DDL is production-safe. Production migration requires the backup, rehearsal, and authorization process described in [`database-migrations.md`](database-migrations.md) and [`production-release-checklist.md`](production-release-checklist.md).

## 13. Current limitations and known technical debt

### Product and workflow gaps

- No applicant-facing portal or ownership-protected applicant workflow.
- `APPLICANT` has no authorized landing route.
- Multi-stage progression is enforced, but there is not yet a dedicated follow-up queue for applicants awaiting final or client interviews.
- No scheduled interview reminder automation.
- No automated interview-result email policy or default template.
- No offer reversal, re-offer, or applicant self-service acceptance.

### Scalability and operability gaps

- Only applicant and booking grids currently have server-side pagination and database filtering.
- Leading-wildcard keyword searches may still become expensive at high volume.
- Notification delivery is not durable and can be lost if the process stops after commit but before dispatch.
- Session invalidation is process-local rather than distributed.
- No runtime SMS sender exists.
- Actuator is present, but public endpoint exposure is not part of the current application design.

### Technical debt

- Some existing Spring components still use field or setter injection.
- Evaluation and other remaining grids still use unpaged list loading.
- Tracked Vaadin-generated frontend artifacts require careful synchronization during builds.

## 14. Recommended next priorities

Based on the current implementation, the next coherent priorities are:

1. Protect booking reschedule history behind an explicit append/query repository API.
2. Define and implement the guided final/client interview progression policy.
3. Add scheduled, duplicate-safe interview reminder emails after the stage policy is settled.
4. Continue server-side pagination for evaluation, schedule, hiring, and administration grids based on measured usage.
5. Design applicant identity, provisioning, and ownership rules before enabling applicant self-service.
6. Introduce a durable outbox only if the business requires notification retry and delivery guarantees.

## 15. Related documentation

- [`recruiter-workbench.md`](recruiter-workbench.md)
- [`account-security.md`](account-security.md)
- [`database-migrations.md`](database-migrations.md)
- [`production-release-checklist.md`](production-release-checklist.md)
- [`../AGENTS.md`](../AGENTS.md) — contributor and engineering constraints
