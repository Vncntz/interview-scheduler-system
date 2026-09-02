# Interview Scheduler System — Contributor Guide

## Purpose and scope

This repository is a server-side interview scheduling and recruitment application. These instructions apply to the entire repository unless a more specific `AGENTS.md` is added below a subdirectory.

Preserve the current package-by-feature modular-monolith design. Prefer focused improvements over broad rewrites. Do not introduce a separate REST API, client-side SPA, microservices, or a new architectural layer unless the task explicitly requires it and the change is justified.

## Technology stack

- Java 25
- Spring Boot 4.0.6
- Vaadin Flow 25.1.5 with the Aura theme
- Spring Data JPA / Hibernate
- Spring Security with Vaadin security integration
- MySQL in normal runtime environments
- Flyway for schema migrations, with separate MySQL and H2 dialect migrations
- H2 in MySQL compatibility mode for the fast/default automated tests
- MySQL 8.4.6 Testcontainers for the opt-in production-migration integration suite
- Spring Mail for email delivery
- Spring Boot Actuator and Thymeleaf as present dependencies; neither is a separate application entry point
- Maven Wrapper (`mvnw` / `mvnw.cmd`)
- Lombok for simple entity and DTO accessors
- JUnit 5 and Spring Boot test support

Treat the versions in `pom.xml` as authoritative. Before adding a library, check whether Spring Boot, Vaadin, or the JDK already provides the capability. Use Spring Boot dependency management and avoid specifying dependency versions unless the BOM does not manage them.

## Architecture rules

The application is a modular monolith organized by business feature under `com.company.iss`:

- `applicant`
- `auth`
- `booking`
- `branch`
- `client`
- `dashboard`
- `evaluation`
- `hiring`
- `notification`
- `position`
- `recruiter`
- `schedule`
- `shared`
- root-level `config`

Each feature may contain `entity`, `repository`, `service`, `view`, `dialog`, `dto`, `config`, `component`, `event`, and `exception` packages as needed. Add code to the owning feature rather than creating generic technical packages at the application root.

Use the existing flow:

```text
Vaadin view/dialog -> service -> repository -> JPA entity
```

Rules:

- Views and dialogs handle presentation, user interaction, and display of validation errors.
- Services own business validation, authorization-sensitive filtering, status transitions, and multi-entity workflows.
- Repositories contain persistence queries only; do not put business rules in repositories.
- Entities contain persistence state and small domain-derived helpers, not UI logic or service lookups.
- `shared` is only for code genuinely used across features. Do not move feature-specific behavior there for convenience.
- `hiring` owns offers, hiring decisions, terminal hiring transitions, hiring audit records, and hiring notifications.
- Preserve the existing applicant-to-hiring dependency direction: `ApplicantAssignmentGuard` belongs to `applicant`, and hiring supplies `HiringApplicantAssignmentGuard`. `ApplicantService` must not depend directly on hiring repositories.
- Do not access repositories directly from views or dialogs.
- Avoid circular service dependencies. For a workflow spanning features, place orchestration in the service that owns the initiating use case.
- Publish cross-feature events using stable identifiers rather than managed JPA entities.
- Keep synchronous database state changes separate from asynchronous notification delivery. A failed notification must not corrupt a successfully committed booking, reschedule, cancellation, evaluation, offer, or hiring decision.

## Package and naming conventions

- Root package: `com.company.iss`.
- Use singular feature package names, matching the existing project.
- Entity names are singular nouns: `Applicant`, `Booking`, `Schedule`.
- Repository names end in `Repository` and extend the appropriate Spring Data interface.
- Business classes end in `Service`.
- Routed Vaadin components end in `View`; modal editors end in `Dialog` or `FormDialog`.
- DTOs contain transport/projection data and belong to the owning feature's `dto` package.
- Enums belong beside the entity or workflow they describe.
- Keep one top-level Java type per file.
- Do not manually edit files under `src/main/frontend/generated`.

## Java coding standards

- Use Java 25 language features only when they improve clarity and remain compatible with the configured toolchain.
- Prefer constructor injection with `final` fields for new and substantially modified Spring components. Existing field injection may be migrated when touching the class, but avoid unrelated mass rewrites.
- Keep methods focused and use descriptive names. Extract complex validation and transition logic.
- Use braces for control-flow blocks and avoid deeply nested conditionals.
- Use `Objects.equals` or null-safe comparisons when entity identifiers may be null.
- Do not catch `Exception` in service code unless adding meaningful recovery or context. UI boundaries may catch expected application exceptions to show a notification.
- Introduce specific application/business exceptions when adding nontrivial validation; do not expand the current use of generic `RuntimeException`.
- Validate at the service boundary even when Vaadin Binder also validates the form.
- Never log passwords, SMTP credentials, API keys, session identifiers, or other secrets.
- Keep formatting consistent with surrounding code. Do not combine functional changes with repository-wide formatting.
- Prefer immutable DTOs where practical. JPA entities may remain mutable as required by the current model.

## Spring Boot conventions

- Use component scanning from `InterviewSchedulerSystemApplication`; do not add redundant scan configuration.
- Put cross-cutting Spring configuration in `com.company.iss.config`; feature-specific startup/configuration belongs in the feature's `config` package.
- `src/main/resources/application.properties` is tracked and defines the runtime MySQL/Flyway contract through environment placeholders.
- `src/test/resources/application.properties` is tracked and owns the isolated H2/Flyway test configuration.
- `src/test/resources/application-mysql-it.properties` is tracked and owns the isolated Testcontainers
  MySQL/Flyway integration-test configuration; it must use container service-connection details.
- Runtime datasource values come from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Do not commit real credentials or machine-specific datasource settings.
- Local `.env` is ignored and may contain real credentials. Never commit, print, quote, or copy its values into reports.
- Spring Boot does not automatically parse this repository's `.env`. Source it explicitly only for a local runtime launch, and never source it for automated tests.
- Do not add `SPRING_DATASOURCE_*` or `SPRING_FLYWAY_*` overrides to test commands; environment properties can supersede the tracked H2 test configuration.
- `ADMIN_EMAIL` and `ADMIN_PASSWORD` make `AdminSeeder` opt-in. Never generate, log, report, or commit the bootstrap password.
- Maintain a sanitized example configuration or documented environment-variable contract containing names and placeholders only whenever configuration keys change.
- Idempotent, non-secret reference data such as default notification templates may be bootstrapped. Bulk clients, positions, and applicants are demo data and must be development-only and deterministic.
- `ClientDataLoader`, `PositionOpeningDataLoader`, and `ApplicantDataLoader` are development-only, deterministic loaders that require both the `dev` profile and explicit demo-data enablement. Preserve both gates and their dependency order; never enable them for production data.
- Use `@Transactional` on service methods that perform a business operation across multiple repository writes. Keep transaction boundaries in services, not views.
- Avoid slow network I/O inside database transactions.
- Commit domain state first and publish ID-only domain events inside the transaction.
- Deliver notifications through `@TransactionalEventListener(phase = AFTER_COMMIT)`. Asynchronous listeners that reload data must use a separate read-only transaction.
- Notification delivery is currently best-effort. Log failures safely without rolling back domain work, and do not claim durable delivery or retries; those guarantees require a separately designed outbox.
- Never log raw integration credentials, tokens, session identifiers, or unnecessary applicant contact data while handling notification failures.
- Prefer configuration properties over constructing infrastructure clients from database or string values throughout application code.
- Do not expose Actuator endpoints publicly without an explicit security decision.

## Vaadin conventions

- Continue using server-side Vaadin Flow views and dialogs.
- Routed views must declare `@Route`, `@PageTitle`, and an explicit access annotation such as `@RolesAllowed` or `@AnonymousAllowed`.
- Use `MainLayout` for authenticated application views unless the screen has a specific reason not to.
- Navigation must be role-aware. Do not show users links to routes they cannot access.
- Every authenticated role must have a valid landing route. Do not assume the admin-only dashboard is suitable for all roles.
- UI visibility is not authorization. Enforce record ownership and role/branch scope in services and repository queries as well.
- Use Vaadin Binder for form binding and immediate field feedback; repeat critical validation in services.
- Dialogs should not persist directly. Return validated input through a callback or invoke the owning service through the established view flow.
- Refresh affected grids after successful mutations and preserve useful search/filter state when practical.
- Display actionable user-safe error messages. Do not expose stack traces, SQL errors, or secret configuration in notifications.
- Use the existing `UserSafeNotifier` at Vaadin boundaries for known user-safe errors instead of duplicating exception-to-notification handling.
- Use typed renderers/item label generators and null-safe value providers for relationship columns.
- For growing datasets, use lazy/paginated data providers rather than loading all rows with `findAll()`.
- Keep reusable styling in the `iss` theme. Avoid duplicate stylesheet declarations and avoid editing generated frontend assets.
- Make destructive actions explicit and confirm them when the result is not easily reversible.

## JPA conventions

- Persistent entities extend `BaseEntity` unless there is a documented reason not to.
- Preserve optimistic locking through the inherited `@Version` field.
- Use `@Enumerated(EnumType.STRING)` for enums.
- Make database nullability match business requirements. Required relationships should use `optional = false` and non-null join columns when a migration can safely enforce them.
- Add database unique constraints for invariants that must survive concurrent requests; service-level existence checks alone are insufficient.
- Do not use `CascadeType.ALL` by default. Choose cascades deliberately based on aggregate ownership.
- Avoid bidirectional relationships unless navigation in both directions is required and lifecycle ownership is clear.
- Do not expose JPA entities as a remote API contract if an API is later introduced.
- Keep denormalized references and counters consistent. Before adding another stored counter, prefer a query or projection unless measured performance requires denormalization.
- When a workflow updates `Applicant`, `Booking`, `Schedule`, `InterviewEvaluation`, or `PositionOpening` together, make the operation transactional and test rollback behavior.
- Protect capacity allocation and similar contested updates with optimistic retry, pessimistic locking, or an atomic database operation. Never rely only on a read-then-increment sequence.
- Avoid N+1 grid queries. Use targeted fetch queries, entity graphs, projections, or paginated data providers where appropriate.
- Do not depend on an open persistence context in Vaadin UI code. Load and update entities through services.

### Audit and history records

- Treat audit and history rows as append-only business records.
- Do not add setters for audit-specific fields; use controlled constructors or factories, Hibernate `@Immutable`, and non-updatable columns where practical. Do not rely on `@Immutable` alone because `BaseEntity` currently exposes inherited setters.
- Do not expose ordinary update or delete CRUD operations from audit repositories. Provide explicit append and query operations instead.
- Never correct history by editing or deleting a row; append a compensating record when the business requires a correction.
- Test both dirty-checking immutability and the repository's exposed API.
- Use `HiringDecisionAudit` and its explicit appender as the current pattern.
- `BookingRescheduleHistory` follows the explicit append/query repository pattern and must remain free of generic mutation and deletion APIs.

## Database rules

- Flyway is the only schema migration tool for this repository.
- MySQL runtime migrations belong in `src/main/resources/db/migration/mysql`; default tests use logically
  equivalent migrations from `src/main/resources/db/migration/h2` against isolated H2 in MySQL mode. The
  opt-in `mysql-it` profile validates the production migrations against an isolated MySQL Testcontainer.
  Never point automated tests at a developer or production database.
- Hibernate uses `spring.jpa.hibernate.ddl-auto=validate` in both environments. It must never create, update, or repair the schema.
- Never edit an applied migration. Add the next version to both dialect directories and keep constraints, defaults, enum values, indexes, and nullability logically equivalent.
- The current latest migration is V8, and `contextLoads()` asserts that version.
- H2 MySQL mode is a fast compatibility check, not proof that MySQL-specific DDL is safe.
- Keep `spring.flyway.clean-disabled=true`; never run Flyway clean against a developer, rehearsal, or real database.
- `baseline-on-migrate` is a one-time controlled rollout option only. Follow `docs/database-migrations.md`; never enable it by default.
- Schema changes require H2 migration tests and, for MySQL-specific DDL, an isolated MySQL rehearsal before touching the real schema.
- Never migrate or start the application against a real database without explicit authorization plus the backup and rehearsal evidence required by `docs/database-migrations.md`.
- Never commit datasource passwords, SMTP passwords, SMS API keys, or other secrets. Use environment variables or an approved secret provider with sanitized examples.
- Schema changes must include migration, rollback/mitigation notes when relevant, and tests for affected constraints or queries.
- Preserve existing data when changing nullability, enums, uniqueness, or relationships. Plan and document data backfills.
- Store timestamps consistently and document timezone assumptions. The current application uses Java time types; do not mix legacy date APIs into new code.
- Add indexes for frequently searched foreign keys, statuses, dates, booking references, and case-insensitive lookup patterns when supported by measured query needs.
- Demo data must never run automatically in production.

## Security rules

Roles currently defined are `ADMIN`, `RECRUITER`, and `APPLICANT`.

- Keep route authorization explicit.
- Enforce authorization at both route and business/data-access boundaries.
- `ADMIN` may manage organization-wide configuration and master data.
- Resolve the actor through `SecurityService.requireOperationsUser()` for operational use cases.
- `ADMIN` operational reads and mutations may be organization-wide.
- `RECRUITER` reads and mutations must be scoped using the applicant's authoritative non-null branch established by V2. Reject recruiters without a valid branch.
- Apply recruiter scope in repository/service queries for lists, detail lookup, audit lookup, and mutation commands. Never load all records and filter only in the UI.
- Cross-branch identifiers must not enable access or mutation. Every affected workflow requires tests for ADMIN success, same-branch recruiter success, and cross-branch recruiter denial.
- Hiring scope follows the applicant's branch for eligibility, outstanding and completed decisions, actions, and audit history.
- Do not grant `APPLICANT` access until an applicant-facing workflow and its ownership checks are explicitly implemented.
- Encode user passwords with the configured `PasswordEncoder`; never store or compare plaintext passwords.
- Do not add hard-coded default passwords. Development bootstrap credentials must come from configuration, be development-only, and require rotation.
- Enforce `active`, `mustChangePassword`, lockout, and similar account fields if they remain in the model. Do not add security-state fields without implementing their behavior.
- Validate current-user ownership and branch scope on every mutation, not only when listing records.
- Make navigation role-aware, but never treat hidden controls as a security boundary.
- Store notification and integration credentials using an approved secret/encryption approach. Do not repopulate secret fields with plaintext values in the UI.
- Keep CSRF and Vaadin's default security behavior enabled unless a documented integration requires a narrowly scoped exception.
- Add audit records for authentication-sensitive changes, role changes, password resets, configuration changes, and material applicant workflow transitions.
- Avoid revealing whether an email account exists in public authentication error messages.

## Business workflow rules

Preserve these existing rules unless requirements explicitly change:

- Branch codes and user/applicant emails must remain unique where defined.
- Recruiters belong to a branch, and schedules must use a recruiter from the selected branch.
- Recruiter schedules on the same date must not overlap.
- Schedule end time must be after start time, capacity must be positive, and booked count must not exceed capacity.
- Only active applicants and open, active, non-full schedules can be booked.
- An applicant must not have multiple active bookings.
- Only attended bookings can be evaluated, and a booking must have at most one evaluation.
- Evaluation scores are from 1 through 10.
- Applicant, booking, schedule, evaluation, and position states must transition together atomically.

The implemented hiring workflow additionally requires:

- Only an active `PASSED` applicant whose booking is `PASSED` and whose matching evaluation result is `PASS` is offer-eligible.
- The selected position must be active, `OPEN`, and have remaining headcount.
- Database uniqueness enforces at most one hiring decision per applicant and per evaluation.
- Hiring decisions transition only from `OFFERED` to `HIRED`, `DECLINED`, or `WITHDRAWN`.
- Applicant status follows the decision as `OFFERED`, `HIRED`, `OFFER_DECLINED`, or `WITHDRAWN`.
- Issuing an offer does not reserve or increment headcount.
- Accepting an offer pessimistically locks the position, increments `hiredCount` exactly once, and marks the position `FILLED` when headcount is reached.
- Exact repeated actions are idempotent; conflicting terminal actions are rejected.
- Every successful hiring transition appends an immutable audit record.
- `JOB_OFFERED` and `HIRED` notifications are delivered only after the transaction commits.
- Applicants with a hiring decision cannot be reassigned to another branch or position.
- Re-offers, reversals, applicant self-service, and offer-time headcount reservation are not currently supported. Adding any of them requires explicit business rules and architecture review.

When adding a new status or action, define:

1. Allowed source states.
2. Resulting states for every affected entity.
3. Counter/capacity effects.
4. Authorization and ownership rules.
5. Notification events.
6. Idempotency and retry behavior.
7. Tests for valid, invalid, repeated, and concurrent execution.

Do not increment derived counters again when editing an existing evaluation or repeating an idempotent operation.

## Testing requirements

The test suite includes service, repository, security, migration, asynchronous-notification, and route-security coverage. Every behavior change must preserve that coverage and add focused tests for its own rules rather than relying only on `contextLoads()`.

- Use unit tests for pure validation, status-transition, counter, and template-rendering logic.
- Use repository integration tests for custom queries, constraints, locking, and active/role/branch filters.
- Use service integration tests for transactional multi-entity workflows and rollback behavior.
- Add security tests for every role, route, record scope, and forbidden mutation affected by a change.
- Add Vaadin UI tests or focused component tests for critical dialogs and workflows when feasible.
- Test booking capacity under concurrent attempts.
- Test cancellation, rescheduling, attendance, no-show, evaluation, and hiring transitions as those workflows are implemented.
- Test duplicate booking and duplicate evaluation protection at both service and database levels.
- Mock or replace email/SMS delivery in automated tests; tests must not send real notifications.
- Tests must be deterministic and must not depend on random seed data, local credentials, or an existing developer database.
- Bug fixes require a regression test that fails before the fix and passes afterward.
- Before handing off application changes, run the clean wrapper suite: `./mvnw clean test`.
- For migration or MySQL-schema compatibility changes, also run `./mvnw clean verify -Pmysql-it` with Docker available.
- On Windows, confirm the Maven process uses Java 25. Set `JAVA_HOME` and prepend its `bin` to `PATH` for that process only; do not change the POM or global Java installation to work around a local launcher problem.
- Prefer the normal wrapper when it works. If the PowerShell or Windows wrapper bootstrap fails, invoke `./mvnw clean test` through the installed Git Bash environment.
- Never source `.env` for tests. Verify that H2 and the H2 Flyway migration location were used without requiring `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD`.
- Report tests run, failures, errors, skipped, and the final Maven result. Treat H2/MySQL compatibility warnings separately from actual failures.
- If tests cannot run because Java or another prerequisite is unavailable, report the exact blocker and do not claim successful verification.

## Git and change rules

- Keep commits small and focused by feature or fix. Do not repeat the existing pattern of combining many independent modules into one large commit.
- Use clear commit subjects consistent with the repository's style, such as `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, and `chore:`.
- Track the sanitized production and H2 test `application.properties` files. Never track `.env`, credentials, local secret overrides, logs, `target` output, IDE settings, or machine-specific datasource values.
- Record `git status --short --branch` before editing. Treat all pre-existing modifications and untracked files as user work; never restore, delete, format, stage, or commit them as part of an unrelated task.
- Do not mix an unrelated documentation or maintenance change into an uncommitted feature.
- Capture the pre-command status of Vaadin-generated paths because Maven and Vaadin commands can rewrite tracked output. After verification, compare generated changes with that snapshot and remove only newly generated build noise when those paths were previously clean.
- Never blanket-restore a generated directory when it contained pre-existing changes. Do not manually edit generated files, and commit generated artifacts only when Vaadin requires them for an intentional reviewed change that the project has explicitly chosen to track.
- Before handoff, inspect `vite.generated.ts`, `src/main/frontend/generated`, and `src/main/bundles` for accidental changes.
- Do not amend, rebase, reset, force-push, or rewrite shared history unless explicitly requested.
- Commit and push authorization is action-specific and scope-specific. Approval to push an earlier commit does not authorize committing or pushing later work.
- Before an authorized commit, inspect the staged scope and scan it for secrets. Before an authorized push, report the branch and intended commit. Never force-push without separate explicit authorization.
- After a resumed request such as "continue," recheck repository status and do not assume that earlier mutation, commit, migration, startup, or push authorization still applies.
- Keep schema migrations in the same change as the entity/repository code that requires them.
- Keep tests in the same change as the behavior they verify.
- Avoid unrelated dependency upgrades, formatting, or renames in feature commits.
- Update documentation and example configuration whenever setup, roles, routes, configuration keys, or operational behavior changes.
- Before committing, review the diff for credentials, generated files, accidental binary assets, debug code, and overly broad changes.

## Change checklist

Before considering a change complete, verify:

- The code belongs to the correct feature package.
- Route and record-level permissions are correct for every role.
- Multi-entity changes are transactional.
- Database constraints support concurrency-sensitive invariants.
- Status and counter transitions remain consistent and idempotent.
- No secrets or local settings were introduced.
- New configuration has a sanitized example or documentation.
- Tests cover success, validation failure, authorization failure, and relevant concurrency/rollback cases.
- The Maven Wrapper test suite passes, or the exact verification blocker is reported.
- The final diff contains no unrelated edits or generated build output.

## Automated Development Workflow

For non-trivial requests that modify application source code,
application behavior, tests, persistence behavior, security behavior, or
architecture, use the `iss-development-workflow` skill.

The primary Codex agent acts as the development orchestrator. It must
classify the request before selecting specialists.

## Multi-Agent Request Routing

Available specialists:

- `feature-strategist` — recommends what should be built and prioritized
- `architect` — designs selected features and architectural changes
- `diagnostician` — investigates failures and determines root causes
- `implementer` — owns source-code modifications
- `reviewer` — independently validates changes

Default routing:

- Feature recommendation: `feature-strategist`
- Selected new feature: `architect → implementer → reviewer`
- Bug/error: `diagnostician → implementer → reviewer`
- Bug requiring architecture: `diagnostician → architect → implementer → reviewer`
- Architecture/refactor: `architect → implementer → reviewer`
- Review only: `reviewer`
- Read-only explanation: parent agent or an appropriate read-only specialist

Do not automatically implement a strategist recommendation unless the
user explicitly requested both recommendation and implementation. Do
not make speculative fixes when the diagnostician reports insufficient
evidence. The implementer should normally be the only specialist
modifying production source code.

If a named specialist or configured model is unavailable, do not
silently skip a required phase. Use an available agent as a same-role
fallback, give it the same task packet and read/write boundaries, and
disclose the fallback in the consolidated report. Specialist
unavailability does not authorize the orchestrator to collapse
architecture, implementation, and review into one unreviewed phase.

Do not run dependent architecture, implementation, and review phases in
parallel. The workflow permits at most two automatic repair cycles.

Never automatically commit or push unless explicitly requested by the
user. Preserve unrelated uncommitted user changes.
