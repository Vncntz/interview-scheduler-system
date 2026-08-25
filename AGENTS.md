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
- Spring Mail for email delivery
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
- `notification`
- `position`
- `recruiter`
- `schedule`
- `shared`
- root-level `config`

Each feature may contain `entity`, `repository`, `service`, `view`, `dialog`, `dto`, and `config` packages as needed. Add code to the owning feature rather than creating generic technical packages at the application root.

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
- Do not access repositories directly from views or dialogs.
- Avoid circular service dependencies. For a workflow spanning features, place orchestration in the service that owns the initiating use case.
- Keep synchronous database state changes separate from asynchronous notification delivery. A failed notification must not corrupt a successfully committed booking or evaluation.

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
- Use profile-specific configuration for development, testing, and production.
- Do not commit real credentials or machine-specific datasource settings.
- Maintain a sanitized example configuration or documented environment-variable contract whenever configuration keys change.
- Demo seeders and data generators must be development-only and deterministic where tests or repeatability matter. Use profiles and explicit ordering when one loader depends on another.
- Use `@Transactional` on service methods that perform a business operation across multiple repository writes. Keep transaction boundaries in services, not views.
- Avoid slow network I/O inside database transactions.
- Use `@Async` only for work that is safe after the initiating transaction commits. Handle and log asynchronous failures without exposing secrets.
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

## Database rules

- MySQL is the runtime database. Tests should use an isolated test database or a compatible container; do not point automated tests at a developer or production database.
- Use Flyway or Liquibase for schema evolution once migrations are introduced. Do not combine competing migration tools.
- Production must not rely on Hibernate creating or updating the schema implicitly. Prefer migration-managed schemas and validation.
- Never commit datasource passwords, SMTP passwords, SMS API keys, or other secrets.
- Configuration should use environment variables or an approved secret provider, with safe local examples.
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
- `RECRUITER` access must be scoped to the recruiter's permitted branch, schedules, bookings, applicants, and evaluations. Never return all records merely because the route allows recruiters.
- Do not grant `APPLICANT` access until an applicant-facing workflow and its ownership checks are explicitly implemented.
- Encode user passwords with the configured `PasswordEncoder`; never store or compare plaintext passwords.
- Do not add hard-coded default passwords. Development bootstrap credentials must come from configuration, be development-only, and require rotation.
- Enforce `active`, `mustChangePassword`, lockout, and similar account fields if they remain in the model. Do not add security-state fields without implementing their behavior.
- Validate current-user ownership on every mutation, not only when listing records.
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

The current test suite is minimal. Every behavior change should improve coverage rather than relying only on `contextLoads()`.

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
- Run the Maven Wrapper test suite before handing off changes. On Windows use `mvnw.cmd test`; on Unix-like systems use `./mvnw test`.
- If tests cannot run because Java, MySQL, credentials, or another prerequisite is unavailable, report that explicitly and do not claim successful verification.

## Git and change rules

- Keep commits small and focused by feature or fix. Do not repeat the existing pattern of combining many independent modules into one large commit.
- Use clear commit subjects consistent with the repository's style, such as `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, and `chore:`.
- Do not commit secrets, local `application.properties`, IDE settings, build output, logs, or generated transient files.
- Do not manually commit changes under `target` or `src/main/frontend/generated` unless Vaadin explicitly requires a reviewed generated artifact and the project has decided to track it.
- Do not amend, rebase, reset, force-push, or rewrite shared history unless explicitly requested.
- Preserve unrelated user changes in a dirty worktree.
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
