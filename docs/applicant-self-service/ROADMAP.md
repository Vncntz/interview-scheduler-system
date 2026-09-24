# Applicant Self-Service MVP roadmap

## Document contract

This roadmap defines planned work; it does not describe implemented behavior. Repository code and tests remain authoritative for the current state. Before implementing a step, read this entire milestone, the exact step, [`STATUS.md`](STATUS.md), root [`AGENTS.md`](../../AGENTS.md), and the relevant current code.

Stable step IDs must not be renumbered. Complete one atomic step at a time and do not implement later-step behavior except where strictly required for compilation. One milestone is one focused PR.

## Locked product decisions

- Invitation only; no public registration or automatic email-based linking.
- A database relationship, never email equality, establishes `User` → `Applicant` ownership.
- Applicants may view their appointments, confirm intent to attend, and request reschedule/cancellation. Recruiters approve or decline appointment changes.
- The original appointment remains active while a request is pending. Submission does not reserve destination capacity or mutate a booking.
- Future eligibility and displayed appointment timezone use the configured `BusinessTime` contract.
- A stale appointment or resolved/stale request requires a new action/request.
- Applicant-facing data is explicitly projected and excludes internal notes, scores, audit actors, hiring internals, and other applicants.
- Preserve the current prohibition on branch reassignment while `BOOKED` or `CONFIRMED` bookings exist.

Deferred until after MVP: public registration, profile editing, self-service contact/login-email changes, document uploads, direct slot booking, offer acceptance, SMS, calendar integrations, and a new active-booking branch-transfer policy.

---

## M1 — Define applicant identity and access

### Milestone goal

Link an applicant account to one existing applicant record, establish ownership-based authorization, isolate operations routes, and provide a minimal applicant portal landing shell.

### Milestone acceptance

- Each `APPLICANT` User links to exactly one Applicant, and each Applicant to at most one User; the database enforces the invariant.
- Applicant A cannot read Applicant B, including by supplying B's ID.
- Applicant users cannot access recruiter/admin routes; operations users cannot enter applicant-only routes.
- Inactive accounts are denied at route and service boundaries, including from an existing session.
- Existing administrator/recruiter behavior and route coverage remain passing.

### M1-S1 — Database-backed User → Applicant ownership

**Goal:** Make applicant ownership an explicit, immutable database relationship.

**Implementation requirements:** Add a nullable Applicant relationship on `User` for operations-account compatibility, with a unique foreign key so one Applicant can have at most one User. Enforce role/link compatibility (`APPLICANT` requires a link; `ADMIN`/`RECRUITER` require none) in the strongest MySQL/H2-compatible database form plus entity/service validation. Add a new paired Flyway migration after the actual latest version; never edit V1–V10. Audit existing rows before adding strict checks and fail safely rather than infer links from email. Map the relationship without adding mutable applicant-side ownership setters.

**Inspect:** `auth/entity/User`, `auth/entity/Role`, `applicant/entity/Applicant`, `auth/repository/UserRepository`, both migration trees, `FlywayMigrationTest`, `MySqlMigrationIT`, account/recruiter creation tests.

**Acceptance criteria:** Schema and Hibernate validation agree; duplicate ownership is rejected by the database; applicant-without-link and operations-with-link are rejected; existing valid admin/recruiter rows migrate unchanged; no email-based backfill exists.

**Required tests:** H2 migration/current-version assertions; JPA mapping and uniqueness tests; role/link constraint tests; MySQL migration/schema assertions; existing account persistence regressions.

**Security/concurrency:** Database uniqueness is the final race-safe ownership guard. Do not expose the linked Applicant through operations DTOs unintentionally.

**Self-review:** Check MySQL/H2 parity, existing-data rollout, FK/index names, nullability, entity mutability, uniqueness under concurrent inserts, and absence of email matching.

**Deferred:** Identity resolution, portal routes, invitations, and all appointment behavior remain for later steps.

### M1-S2 — Applicant authentication identity resolver

**Goal:** Resolve the authenticated active applicant account to its owned Applicant without changing operations authorization.

**Implementation requirements:** Add a dedicated applicant identity service/resolver that starts from the authenticated principal, reloads current User state, requires active/unlocked/eligible `APPLICANT`, requires the ownership link, and returns only the owned Applicant identity needed by downstream services. Keep `requireOperationsUser()` unchanged. Define user-safe, non-enumerating failure behavior.

**Inspect:** `SecurityService`, `AccountAuthenticationService`, `UserRepository`, `ApplicantRepository`, `AccountSessionService`, current security tests.

**Acceptance criteria:** Active linked applicants resolve; inactive, locked, wrong-role, missing-link, anonymous, and deleted-link cases fail; operations users continue resolving only through operations methods.

**Required tests:** Unit/service tests for every role and account state; stale-session deactivation test; regression tests for `requireOperationsUser()` and `requireAdmin()`.

**Security/concurrency:** Reload database state on each protected operation; do not trust only session authorities or a browser-supplied applicant ID.

**Self-review:** Verify no email comparison, no operations authorization weakening, no entity/data leakage in exceptions, and current-state checks on every call.

**Deferred:** Record-specific ownership guards and portal UI remain later steps.

### M1-S3 — Applicant ownership guard

**Goal:** Provide reusable read/mutation guards that scope every applicant action to the authenticated owner.

**Implementation requirements:** Add repository/service operations that derive the owned Applicant ID from M1-S2 and query/lock by that ID. If a command contains an applicant or child-record ID, verify it belongs to the resolved Applicant inside the transaction. Use indistinguishable denied/not-found behavior where a foreign ID could disclose existence.

**Inspect:** Applicant/booking repositories and services, branch-scoped query patterns, `UserSafeNotifier`, service security tests.

**Acceptance criteria:** Owner access succeeds; foreign Applicant and foreign child IDs reveal no data and mutate nothing; anonymous, inactive, and operations users are denied; the mutation guard supports transactional reauthorization.

**Required tests:** Applicant A/B read and mutation tests; foreign/nonexistent ID response parity; inactive-session test; operations-role denial; repository query-scope tests.

**Security/concurrency:** Authorization and locking must be in the same transaction for mutations. UI filtering alone is forbidden.

**Self-review:** Trace every input ID to an ownership predicate, confirm no load-then-filter leakage, and verify safe errors/logging.

**Deferred:** Appointment DTOs and actions are not introduced here.

### M1-S4 — Applicant landing route and portal shell

**Goal:** Give applicants a dedicated minimal landing route and navigation shell without exposing operational UI.

**Implementation requirements:** Add an applicant-only landing route and layout/shell, update root-role forwarding for `APPLICANT`, and keep admin/recruiter navigation unchanged. The shell may show safe identity/navigation placeholders only; no appointment query yet. Make the layout responsive and use explicit role annotations.

**Inspect:** `RoleLandingView`, `LoginView`, `MainLayout`, route security tests, theme styles, `SecurityConfig`.

**Acceptance criteria:** Applicant login/root navigation lands in the portal; applicant cannot navigate to operations routes; admin/recruiter landings and menus are unchanged; inactive applicants are redirected/denied safely.

**Required tests:** Route annotation/security matrix; role landing tests; layout navigation tests for all roles; component smoke tests at mobile and desktop widths when supported.

**Security/concurrency:** Route annotations complement, not replace, service ownership. Do not reuse `MainLayout` if it can expose operations navigation/state.

**Self-review:** Enumerate every current route for applicant denial, verify no internal links or data in the shell, and confirm operations regressions.

**Deferred:** Appointment display and actions remain M4+.

### M1-S5 — Route isolation, regression coverage, and milestone validation

**Goal:** Close authorization gaps and prove the complete M1 boundary.

**Implementation requirements:** Build a role/route/service matrix covering anonymous, inactive, `APPLICANT`, `RECRUITER`, and `ADMIN`; add missing negative tests; verify database ownership invariants and current account lifecycle behavior. Update current-state documentation only after behavior exists.

**Inspect:** All Vaadin `@Route` classes, route tests, security services, M1 diff, CI workflow.

**Acceptance criteria:** All M1 milestone acceptance criteria pass; no applicant access to operations routes/services; no operations access to applicant portal services; clean standard suite passes; MySQL gate passes locally when Docker is available and must pass in CI.

**Required tests:** Focused M1 suite, `./mvnw clean test`, `./mvnw clean verify -Pmysql-it` when available.

**Security/concurrency:** Re-run ownership uniqueness and stale-session cases; confirm no authorization depends on navigation visibility.

**Self-review:** Review the complete milestone diff for route gaps, ownership bypass, migration parity, session behavior, applicant-safe errors, and recruiter/admin regressions.

**Deferred:** Invitation and account creation begin in M2.

---

## M2 — Invitation and account activation

### Milestone goal

Allow an authorized same-branch recruiter or administrator to invite an existing Applicant at its recorded email and let that Applicant activate one account securely.

### Milestone acceptance

- Same-branch recruiters and administrators can invite; cross-branch recruiters cannot.
- Invitation tokens expire, are single-use, store no raw bearer token, and reissue invalidates the old invitation.
- Activation uses the existing password policy and cannot create duplicate accounts under retries/concurrency.
- Delivery failure is visible and retryable without creating another account or invitation.

### M2-S1 — Invitation persistence and secure token service

**Goal:** Persist invitation lifecycle safely and generate verifiable single-use tokens.

**Implementation requirements:** Introduce an invitation aggregate tied to Applicant with issuer, public request ID, token hash, expiry, lifecycle timestamps/status, and delivery status/attempt metadata needed by M2-S3. Use a configuration namespace/domain separate from password reset while reusing its proven SecureRandom/HMAC/hash/base-URL design where appropriate. Enforce at most one active invitation with a separate operational guard row keyed uniquely by Applicant and linked uniquely to the active invitation, so history can remain append-only. Reissue atomically invalidates the old invitation and replaces the guard. Store all final lifecycle enum values needed by the milestone to avoid editing the migration later.

**Inspect:** `PasswordResetRequest`, `PasswordResetTokenService`, account-security properties, appender patterns, notification settings, paired migrations/tests.

**Acceptance criteria:** Raw token/link never persists; public ID and hash are unique; expired/consumed/invalidated invitations are unusable; the database guard rejects a second active invitation per Applicant while retaining invitation history; configurations are separate.

**Required tests:** Token format/signature/hash/expiry tests; repository constraint tests; reissue invalidation; migration/Hibernate/MySQL schema tests; secret-redaction tests.

**Security/concurrency:** Treat links as credentials. Make concurrent issue/reissue deterministic and prevent two usable invitations.

**Self-review:** Compare with reset-token protections, inspect logs/DTOs for raw tokens, verify constraints and microsecond timestamps, and confirm no account is created.

**Deferred:** Authorization, delivery UI/retry, and activation are later M2 steps.

### M2-S2 — Branch-scoped invitation issuance

**Goal:** Issue or reissue an invitation only to an eligible existing Applicant within current authority.

**Implementation requirements:** Add a transactional issuance service. Resolve operations actor, lock the Applicant using current branch authority, reject inactive/ineligible/already-linked cases as defined, use the Applicant's current recorded email only, rotate an outstanding invitation, and publish an ID-only delivery event after persistence.

**Inspect:** `ApplicantService` branch locking, `SecurityService`, User/Applicant repositories, password-reset issuance, current branch-transfer guards.

**Acceptance criteria:** Admin and same-branch recruiter succeed; cross-branch/wrong-role/inactive/already-linked cases fail without side effects; reissue invalidates the old token; email is not accepted from the browser as an ownership input.

**Required tests:** Role/branch matrix; stale branch transfer; already-linked and concurrent issuance; transaction rollback/event publication tests.

**Security/concurrency:** Authorize on Applicant's current branch inside the transaction. Lock before checking/linking invitation state.

**Self-review:** Verify no email auto-linking, no cross-branch existence leak, correct lock order, and ID-only event payload.

**Deferred:** Delivery retry and activation remain M2-S3/S4.

### M2-S3 — Email delivery state and retry

**Goal:** Make invitation delivery failure visible and safely retry the same invitation.

**Implementation requirements:** Deliver after commit with a narrow invitation context. Persist delivery state/attempt timestamps and a safe failure code without storing message bodies, recipient snapshots beyond approved need, provider responses, or bearer token. A retry must target the same still-usable invitation and regenerate/verify its deterministic token from the public ID; it must not create another invitation or User. Provide branch-scoped/admin query and retry service APIs.

**Inspect:** after-commit notification listeners, `PasswordResetNotificationListener`, `NotificationService`, `EmailService`, notification health patterns.

**Acceptance criteria:** Success/failure is visible; failure does not roll back issuance; retry is authorized and reuses one invitation; unusable/reissued invitation is not sent; logs mask recipient and exclude links/tokens.

**Required tests:** AFTER_COMMIT annotation/rollback tests; simulated success/failure/retry; branch authorization; duplicate retry/concurrency; safe logging/context tests; no real SMTP.

**Security/concurrency:** Serialize retry state updates; portal/database state remains authoritative. Never expose token through staff UI.

**Self-review:** Confirm after-commit boundary, failure containment, retry identity, safe context, and absence of duplicate records.

**Deferred:** Durable general outbox and automatic retry remain out of scope.

### M2-S4 — Single-use account activation

**Goal:** Consume a valid invitation and create exactly one active linked Applicant User.

**Implementation requirements:** Add an anonymous activation route/service. Validate token and password policy, locate by public ID, then lock Applicant and invitation/active guard in a documented order; revalidate eligibility/current recorded email and absence of ownership; create `APPLICANT` User linked to Applicant; consume invitation and remove the active guard atomically. Exact retry after success fails safely without revealing data. Account uniqueness/ownership constraints must decide concurrent races.

**Inspect:** reset-password view/service, `PasswordPolicy`, `UserRepository`, M1 ownership constraints, session/authentication services.

**Acceptance criteria:** Valid activation creates one account; expired/reused/reissued/malformed tokens fail safely; passwords follow shared policy; concurrent activation yields one User/one consumed invitation; operations roles cannot be created through this path.

**Required tests:** Token cases; password cases; rollback; concurrent double activation in H2 and MySQL where lock/constraint behavior differs; route anonymity/security; audit/event tests.

**Security/concurrency:** Token validation must be constant/safe enough for public use. Never identify Applicant by submitted email. Maintain lock ordering and translate uniqueness races to safe outcomes.

**Self-review:** Verify one transaction, raw-token absence, link/role invariant, no enumeration, session behavior, and database race handling.

**Deferred:** Login recovery and staff lifecycle controls remain M3.

### M2-S5 — Staff invitation controls and milestone validation

**Goal:** Add minimal authorized UI for issue/status/reissue/retry and prove M2 end to end.

**Implementation requirements:** Integrate controls into the existing authorized Applicant management/profile flow, with role/branch-aware visibility backed by service authorization. Display safe state and timestamps, never token/link. Add activation happy-path coverage and operational guidance for invitation configuration.

**Inspect:** Applicant views/profile actions, `UserSafeNotifier`, notification configuration docs, M2 services/tests.

**Acceptance criteria:** Authorized staff can issue/reissue/retry and see safe delivery state; cross-branch controls are absent and service calls denied; activation completes; all milestone criteria and existing operations flows pass.

**Required tests:** Component/route/service matrix; issue→failure→retry and issue→activate flows; clean suite and MySQL gate as milestone final step.

**Security/concurrency:** UI state is untrusted; reauthorize every click. No bearer tokens in grids, notifications, URLs shown to staff, or logs.

**Self-review:** Review entire M2 diff for token exposure, duplicate accounts/invitations, branch scope, after-commit behavior, configuration secrecy, and regressions.

**Deferred:** Public registration and applicant email changes remain excluded.

---

## M3 — Login, recovery, and account lifecycle

### Milestone goal

Complete applicant login/logout, password change/recovery, and staff-controlled deactivation/reactivation while preserving ownership.

### Milestone acceptance

- Applicant lands in the portal; login and recovery do not reveal account existence.
- Expired/reused reset tokens fail safely; password change/reset invalidates supported sessions.
- Deactivation blocks new login and operations from an existing session.
- Applicant email edits cannot transfer account ownership; self-service email change is absent.

### M3-S1 — Applicant login and navigation

**Goal:** Complete role-correct login/logout behavior for Applicant accounts.

**Implementation requirements:** Reuse the shared authentication provider and generic errors, route Applicant to the portal, support logout, and ensure `mustChangePassword`, lockout, inactive state, and session registration behave consistently across roles. Do not create a second login stack.

**Inspect:** authentication provider/service, `SecurityConfig`, `LoginView`, role landing, session service, route tests.

**Acceptance criteria:** Active Applicant authenticates and lands in portal; invalid/unknown/inactive/locked failures remain non-enumerating; operations landing remains unchanged; logout invalidates the current session.

**Required tests:** Authentication matrix, landing/navigation, lockout and logout regressions for all roles.

**Security/concurrency:** Preserve dummy-hash timing behavior and serialized failed-attempt updates.

**Self-review:** Check error equivalence, route access, session registration, and no role-specific password shortcuts.

**Deferred:** Recovery and staff lifecycle controls remain later M3 steps.

### M3-S2 — Applicant password change through existing lifecycle service

**Goal:** Let applicants change passwords using the shared lifecycle rules.

**Implementation requirements:** Authorize the change-password route for Applicant and reuse `AccountLifecycleService.changeCurrentPassword`, shared policy, audit, reset invalidation, credential-change event, and forced logout. Keep operations behavior identical.

**Inspect:** `ChangePasswordView`, `AccountLifecycleService`, `PasswordPolicy`, `AccountSessionService`, account route tests.

**Acceptance criteria:** Applicant can change a valid password; wrong current/reused/invalid password fails safely; known sessions are invalidated and re-login is required; audits are appended once.

**Required tests:** Applicant route/component tests; lifecycle service regressions; session invalidation and duplicate-submit tests.

**Security/concurrency:** Reload/lock current User; never log passwords; prevent repeat submission from creating duplicate audit effects.

**Self-review:** Confirm shared service use, policy parity, session behavior, audit append-only semantics, and operations regressions.

**Deferred:** Self-service email and profile changes remain excluded.

### M3-S3 — Non-enumerating applicant forgot-password flow

**Goal:** Allow an Applicant to request recovery without revealing whether an account exists.

**Implementation requirements:** Add a public request flow returning one generic response. Reuse the secure reset-token persistence/service where domain rules allow, but add an applicant-safe initiation path rather than weakening admin-only recruiter reset. Only active linked Applicant accounts with a current eligible email may produce a request/event. Rotate prior outstanding requests; deliver after commit.

**Inspect:** `PasswordResetService`, token/request entities, reset view, notification readiness/listener/context, account-security docs.

**Acceptance criteria:** Known and unknown/inactive/unlinked inputs receive the same public response; eligible Applicant receives one usable expiring token; reissue invalidates old; consume is single-use; session invalidation occurs; recruiter/admin reset remains unchanged.

**Required tests:** Response equivalence and timing-path test where practical; token expiry/reuse/rotation; AFTER_COMMIT/no-event-on-rollback; session invalidation; operations regression.

**Security/concurrency:** Normalize identifiers consistently without linking by Applicant email. Rate limiting/abuse controls must be assessed and documented if not implemented.

**Self-review:** Check enumeration, raw token handling, delivery context, separate authorization paths, and concurrent requests.

**Deferred:** General public registration and email change remain excluded.

### M3-S4 — Staff Applicant account deactivate/reactivate

**Goal:** Let authorized staff control Applicant account activity using current Applicant branch authority.

**Implementation requirements:** Add lifecycle service methods that lock Applicant/User, authorize Admin or current-branch Recruiter, preserve the ownership link, append security audit, invalidate outstanding reset/invitation credentials as appropriate, and expire known sessions on deactivation. Reactivation must not generate credentials or invitations.

**Inspect:** recruiter lifecycle controls, Applicant service/view, account audits/events/session service, branch-transfer behavior.

**Acceptance criteria:** Admin/same-branch recruiter can deactivate/reactivate; cross-branch denied; deactivation blocks new and existing-session actions; exact repeat is idempotent; ownership is unchanged.

**Required tests:** Role/branch matrix, stale transfer, existing-session denial, idempotency/audit count, concurrent lifecycle action.

**Security/concurrency:** Authorize from current Applicant branch inside the transaction; lock in the documented order.

**Self-review:** Verify no account deletion/link mutation, session invalidation, safe audit, invitation/reset invalidation, and recruiter behavior.

**Deferred:** Applicant-driven deactivation and ownership transfer are excluded.

### M3-S5 — Email/ownership regression rules and milestone validation

**Goal:** Prevent contact-data changes from changing identity and close M3 regressions.

**Implementation requirements:** Define and enforce behavior when staff edits `Applicant.email`: the existing User link remains authoritative, login email is not silently changed, and a mismatch does not relink another account. Surface an appropriate staff warning/state if needed. Verify all account lifecycle flows and update docs.

**Inspect:** Applicant edit service/dialog, User uniqueness, ownership guard, auth/recovery services, system current state.

**Acceptance criteria:** Email edits cannot transfer or auto-create ownership; duplicate email constraints remain; all M3 criteria pass; clean suite and MySQL gate pass as final milestone validation.

**Required tests:** Email change/mismatch/duplicate scenarios, attempted takeover, recovery targeting, full route/security regressions, clean gates.

**Security/concurrency:** Treat linked User identity and Applicant contact email as distinct until a future explicit verified-change design.

**Self-review:** Search for email-equality ownership logic, verify no implicit login change, and review session/recovery/account regressions.

**Deferred:** Self-service login/contact email change remains post-MVP.

---

## M4 — Read-only appointment portal

### Milestone goal

Show each Applicant only their current appointment and limited safe history with correct business-zone presentation and clear empty/cancelled/rescheduled states.

### Milestone acceptance

- Date/time/timezone, stage, mode, and applicant-safe location/meeting details are correct.
- Empty, cancelled, and rescheduled states are clear.
- Only owned appointments appear; internal notes, scores, audit details, hiring internals, and other applicants are absent.
- Portal is usable at desktop and mobile widths.

### M4-S1 — Explicit applicant-safe Schedule details

**Goal:** Model safe location/meeting information without reclassifying internal `Schedule.notes`.

**Implementation requirements:** Confirm no current field is explicitly applicant-safe (current `notes` is internal). Add minimal explicit applicant-facing location/meeting detail fields with mode-aware validation and documented length/URL/sanitization rules. Add paired migrations with safe null/default handling for existing schedules and expose staff editing in the established schedule flow. Do not copy existing notes automatically.

**Inspect:** Schedule entity/service/dialog/view, interview modes, migrations/tests, notification template rendering.

**Acceptance criteria:** Staff can maintain explicit safe details; existing schedules migrate without leaking notes; mode validation is enforced in service; internal notes remain separate and inaccessible to portal projections.

**Required tests:** Migration/Hibernate/MySQL mapping; service/dialog validation by mode; existing-row migration; privacy test proving notes are not copied/exposed.

**Security/concurrency:** Sanitize/render as plain text or validated URL; avoid credentials/secrets in meeting details and document safe-use expectations.

**Self-review:** Verify migration parity/backfill, no notes reuse, validation at service boundary, and existing scheduling regressions.

**Deferred:** Portal queries/UI remain M4-S2–S4.

### M4-S2 — Applicant-safe appointment DTO/query service

**Goal:** Produce an ownership-scoped projection containing only safe appointment data.

**Implementation requirements:** Add explicit DTO/query service deriving Applicant from M1 identity and querying bookings by owned Applicant ID. Include only booking identity needed for later stale actions, status/stage, schedule date/time/mode, configured timezone label, and M4-S1 safe details. Avoid entities, remarks, recruiter internals, mutable audit actors, evaluation/hiring data, and unrelated IDs.

**Inspect:** Booking repository entity graphs, Applicant profile read model, `BusinessTime`, schedule fields, ownership guard.

**Acceptance criteria:** Only owned records return; foreign input cannot change scope; DTO contains no forbidden fields; timezone comes from `BusinessTime`; query avoids N+1 and open-session UI dependence.

**Required tests:** Applicant A/B repository/service tests; DTO field whitelist/reflection test; timezone test; inactive/wrong-role cases; query-count/fetch test where practical.

**Security/concurrency:** Derive scope server-side. Treat booking/schedule identifiers as opaque action tokens, not authority.

**Self-review:** Field-by-field privacy audit, ownership query inspection, timezone correctness, and no entity leakage.

**Deferred:** State presentation and mutations remain later steps.

### M4-S3 — Current, empty, cancelled, and rescheduled state mapping

**Goal:** Map booking/lifecycle data into understandable current and limited-history states.

**Implementation requirements:** Define deterministic selection/order for active `BOOKED`/`CONFIRMED` and legacy `RESCHEDULED` status handling, cancelled/no-show/terminal history, and V9 appointment snapshots. Use immutable lifecycle/reschedule snapshots for history; label legacy missing details honestly. Do not infer historical details from a current Schedule.

**Inspect:** Booking statuses, lifecycle/reschedule histories and repositories, Applicant profile presentation, V9 migration/docs.

**Acceptance criteria:** No-appointment, upcoming, confirmed, cancelled, and rescheduled cases are distinct; reschedule shows correct old/new safe snapshots when available; ordering is stable; only a limited relevant history is returned.

**Required tests:** State matrix, multiple historical bookings/reschedules, legacy missing snapshots, same-timestamp ordering, privacy/ownership regressions.

**Security/concurrency:** Limit data by ownership before mapping. Never expose actor IDs/names, reasons intended as internal, or internal remarks.

**Self-review:** Validate every status mapping, immutable snapshot source, deterministic ordering, and safe field list.

**Deferred:** Confirmation and change-request actions remain M5/M6.

### M4-S4 — Responsive appointment portal UI

**Goal:** Present the safe appointment model clearly on desktop and mobile.

**Implementation requirements:** Build applicant-only cards/timeline/empty states in the portal shell, show explicit configured timezone, stage/mode labels, safe details, and clear status language. Use accessible responsive layouts and user-safe errors. No mutation controls yet.

**Inspect:** portal shell, existing Vaadin presentation classes/theme, `UserSafeNotifier`, component test patterns.

**Acceptance criteria:** Required fields/states render accurately; mobile layout does not require horizontal grid use; keyboard/screen-reader labels are meaningful; operations navigation/data is absent.

**Required tests:** Component rendering for each state; route/role tests; safe error test; viewport smoke checks or equivalent responsive assertions.

**Security/concurrency:** UI consumes DTOs only. Do not store trusted ownership state in component fields beyond a refresh cycle.

**Self-review:** Visual/state review, privacy inspection of rendered text/component data, and role/navigation regression.

**Deferred:** Action buttons remain M5/M6.

### M4-S5 — Privacy regression tests and milestone validation

**Goal:** Prove read-only portal ownership and data minimization.

**Implementation requirements:** Add explicit negative assertions for every forbidden field/source and foreign-ID attempt. Exercise timezone and state mapping through service and UI. Update current-state docs as implemented.

**Inspect:** Complete M4 diff, internal Applicant/Booking/Schedule/Evaluation/Hiring/Audit fields, route matrix.

**Acceptance criteria:** All M4 milestone criteria pass; no forbidden data reaches DTO/template/component; clean suite and MySQL gate pass as final milestone validation.

**Required tests:** Privacy whitelist/blacklist tests, A/B isolation, route/component regressions, clean gates.

**Security/concurrency:** Refresh after concurrent reschedule/cancel and show current authoritative state without mixing snapshots.

**Self-review:** Search applicant packages/templates for entity types and internal field names; review rendered errors/logging and regression scope.

**Deferred:** All applicant mutations remain later milestones.

---

## M5 — Applicant attendance confirmation

### Milestone goal

Allow an Applicant to confirm intent to attend an eligible future appointment while reusing booking transition/history/event logic.

### Milestone acceptance

- Only the owner can confirm a strictly future eligible booking.
- `CONFIRMED` means intent to attend, not actual attendance.
- Exact repeated confirmation is harmless and produces no duplicate history/notification.
- Cancelled/ineligible or stale-replaced appointments cannot be confirmed; recruiters see the updated status.

### M5-S1 — Refactor confirmation transition for safe reuse

**Goal:** Separate the booking confirmation transition from operations-role entry authorization without duplicating behavior.

**Implementation requirements:** Refactor `BookingService.confirm` into role-specific entry points sharing one internal transactional transition/history/event path. Preserve operations behavior and current lock order. Define idempotent handling for an already-confirmed same appointment without appending/publishing again.

**Inspect:** `BookingService.confirm`, booking locks/repository, lifecycle unique constraint, notification listener, booking tests.

**Acceptance criteria:** Operations confirmation behaves as before; shared transition writes status/history/event once; repeated exact confirmation is a no-op; no applicant endpoint exists yet.

**Required tests:** Unit/integration transition tests, duplicate call/history/event count, operations authorization and notification regressions.

**Security/concurrency:** Do not accept an arbitrary actor to bypass authorization. Keep entry services responsible for current role/ownership.

**Self-review:** Confirm one transition implementation, transaction boundary, lock order, idempotency, history uniqueness, and unchanged operations behavior.

**Deferred:** Applicant command, future/stale checks, and UI remain M5-S2–S4.

### M5-S2 — Applicant-owned confirmation command with appointment identity

**Goal:** Add an applicant entry command that cannot confirm another or replacement appointment.

**Implementation requirements:** Command includes booking ID and expected current schedule ID (or an equally strong current appointment identity). Resolve Applicant ownership server-side, lock applicant then booking, reauthorize ownership, compare expected identity, and invoke the shared transition.

**Inspect:** M1 ownership guard, M4 DTO identity, booking lock helpers, branch-independent applicant authorization.

**Acceptance criteria:** Owner/current identity succeeds; foreign booking, mismatched schedule, missing identity, and wrong role fail with no mutation; staff confirmation remains available.

**Required tests:** Owner/foreign/nonexistent/stale identity matrix; transaction rollback; operations regression.

**Security/concurrency:** Compare identity after locks. Do not authorize via booking email, recruiter, or schedule branch.

**Self-review:** Trace command fields, ownership predicate, stale check, error disclosure, and shared transition use.

**Deferred:** Eligibility policy refinements and UI remain next steps.

### M5-S3 — Future eligibility and concurrency-safe idempotency

**Goal:** Enforce time/state eligibility and exactly-once domain effects under concurrent confirmation.

**Implementation requirements:** Require active Applicant/User, active non-cancelled Schedule, `BOOKED` or exact-idempotent `CONFIRMED`, and appointment start strictly after one `BusinessTime` snapshot. Serialize concurrent calls through existing locks; event/history appear only for the winner.

**Inspect:** `BusinessTime`, booking future predicates, lifecycle schema, confirmation integration/notification tests.

**Acceptance criteria:** Past/starting-now/cancelled/inactive/evaluated/terminal bookings cannot confirm; future eligible succeeds; concurrent duplicate calls yield one transition, one history, one notification event.

**Required tests:** Fixed-clock boundaries, invalid-state matrix, concurrent confirmation integration test, event-after-commit/rollback test, MySQL lock behavior where needed.

**Security/concurrency:** Use business time, not JVM zone. Ensure no uniqueness exception leaks and no duplicate external delivery is intentionally triggered.

**Self-review:** Time boundary, microseconds, lock acquisition, repeated/concurrent effects, and rollback review.

**Deferred:** UI remains M5-S4; attendance recording stays staff-only.

### M5-S4 — Portal confirmation UI

**Goal:** Expose confirmation only for currently eligible appointment state.

**Implementation requirements:** Add a confirm-intent action to the appointment portal with clear wording, confirmation feedback, loading/double-submit protection, expected appointment identity from the DTO, and authoritative refresh after action/error.

**Inspect:** M4 presentation, existing booking UI action patterns, user-safe notifier.

**Acceptance criteria:** Eligible action works and refreshes to `CONFIRMED`; ineligible state hides/disables action but service still rejects; stale page shows safe refresh guidance; mobile/keyboard usage works.

**Required tests:** Component visibility/action tests, double click, stale reschedule between render/submit, safe error and refresh, role isolation.

**Security/concurrency:** Never treat button visibility as authorization or retain stale entities in the view.

**Self-review:** Wording distinguishes intent from attendance; verify identity submitted and refreshed DTO contains no internal data.

**Deferred:** Change requests remain M6.

### M5-S5 — Confirmation concurrency/regression validation

**Goal:** Validate the complete confirmation flow and existing recruiter behavior.

**Implementation requirements:** Cover owner, stale, duplicate, timing, route, event, and recruiter-view update scenarios; update docs. Confirm notification templates remain applicant-safe or defer template hardening explicitly to M8 without leaking current internals.

**Inspect:** Complete M5 diff, booking/recruiter UI, listeners/templates, lifecycle/audit queries.

**Acceptance criteria:** All M5 criteria pass; no duplicate state/history/event under retry/concurrency; recruiter/admin confirmation and grids remain passing; clean suite and MySQL gate pass.

**Required tests:** Focused security/concurrency/notification suite and milestone clean gates.

**Security/concurrency:** Test a concurrent reschedule/cancel versus confirm and assert one valid serial outcome with correct capacity/history.

**Self-review:** Full diff for ownership bypass, stale action, time source, lock order, event timing, privacy, and operations regressions.

**Deferred:** Request submission/review and new notifications remain later milestones.

---

## M6 — Submit reschedule/cancellation requests

### Milestone goal

Let Applicants request an appointment change with a reason and optional preferred windows, view their own requests, and withdraw a pending request without altering the booking.

### Milestone acceptance

- Only an eligible future owned booking can receive a request.
- At most one pending request per booking, including concurrent submission.
- Submission changes no booking/capacity and Applicant sees only owned requests.
- Withdrawal is owner-only and pending-only; source appointment snapshot is immutable.

### M6-S1 — Change-request persistence with database pending guard

**Goal:** Persist append-oriented change requests and enforce one pending request per booking in the database.

**Implementation requirements:** Add a request aggregate with type (`RESCHEDULE`/`CANCELLATION`), lifecycle statuses needed through M7 (`PENDING`, `WITHDRAWN`, `APPROVED`, `DECLINED`, `STALE`), Applicant/Booking/source Schedule references, immutable source appointment safe snapshot, reason, requester, timestamps/version, and nullable resolution metadata. Use a separate operational pending-guard row keyed uniquely by Booking (and uniquely linked to request), created/deleted atomically, so sequential historical requests remain possible without relying on MySQL partial indexes. Keep request history non-deletable.

**Inspect:** booking lifecycle/reschedule history patterns, append/query repositories, V9 snapshots, paired migrations/tests.

**Acceptance criteria:** Request history is immutable; pending guard prevents two pending rows; snapshot survives later reschedule; FKs/indexes/statuses match Hibernate; no booking/capacity field changes.

**Required tests:** Migration/Hibernate/MySQL schema; repository immutability; pending-guard uniqueness and FK tests; snapshot precision; rollback leaves no orphan guard/request.

**Security/concurrency:** The guard is the final concurrency invariant. Define lock order applicant → booking → request/guard → schedules for later resolution.

**Self-review:** MySQL/H2 parity, guard lifecycle, history mutation API, source snapshot completeness/privacy, and no capacity effects.

**Deferred:** Submission/query/UI and recruiter resolution behavior remain later steps.

### M6-S2 — Optional preferred date/time windows

**Goal:** Represent optional reschedule preferences without implying a reserved slot.

**Implementation requirements:** Add zero or more immutable preference windows owned by a request, with business-local date and optional start/end bounds, clear validation/order/maximum-count rules, and no Schedule reference/capacity semantics. Cancellation requests must not carry windows.

**Inspect:** `BusinessTime`, schedule date/time validation, child-history persistence patterns, migrations.

**Acceptance criteria:** Empty preferences allowed; valid windows persist in input order; invalid/past/reversed/excess windows fail; no schedule lookup/lock/capacity change occurs.

**Required tests:** Validation boundaries with fixed BusinessTime, persistence/order, cancellation rejection, migration parity/privacy.

**Security/concurrency:** Preferences are untrusted suggestions, not authorization or eligibility guarantees.

**Self-review:** Timezone/microseconds, bounds/max count, immutable ownership, no slot reservation, and applicant-safe fields.

**Deferred:** Automatic slot matching and direct booking are excluded.

### M6-S3 — Owned request submission

**Goal:** Submit an owned future appointment change request transactionally.

**Implementation requirements:** Command includes booking ID, expected schedule identity, type, reason, and optional windows. Resolve/lock owned Applicant then Booking, validate `BOOKED`/`CONFIRMED`, future start via one BusinessTime snapshot, active schedule/applicant, expected identity, no evaluation/terminal state, then persist request and guard. Do not invoke booking mutations or publish booking events.

**Inspect:** M5 applicant booking command, booking eligibility, M6 persistence, ownership guard.

**Acceptance criteria:** Eligible owner submission succeeds; foreign/stale/past/inactive/invalid state fails; original booking/status/schedule counters/reminder generation/history are unchanged.

**Required tests:** Ownership/state/time/stale matrix; transaction rollback; explicit before/after assertions for booking/capacity/history/events.

**Security/concurrency:** Reauthorize inside transaction. Normalize/limit reason without allowing internal/log injection.

**Self-review:** Lock order, BusinessTime, guard insertion, side-effect absence, safe errors, and reason/privacy handling.

**Deferred:** Duplicate-race translation, query/withdraw, and UI remain next steps.

### M6-S4 — Duplicate and concurrent submission handling

**Goal:** Make retries/concurrent submissions deterministic and user-safe.

**Implementation requirements:** Treat an exact duplicate command for the same pending request as idempotent or return the existing safe summary; reject conflicting type/reason/windows while pending. Translate guard uniqueness races into a domain response. Test concurrent submission against withdraw/reschedule/cancel where applicable.

**Inspect:** database exception translation patterns, M6 guard repository/service, concurrency test harnesses.

**Acceptance criteria:** Two concurrent identical submissions yield one request/guard; conflicting second submission creates nothing; no raw SQL/constraint error leaks; booking/capacity remain unchanged.

**Required tests:** H2 concurrency integration; MySQL concurrency test for unique/lock semantics; exact/conflicting retry; rollback/orphan checks.

**Security/concurrency:** Do not weaken DB guard or rely on `exists`. Avoid deadlocks using the documented lock order.

**Self-review:** Race timelines, exception translation, idempotency comparison fields, transaction cleanup, and database parity.

**Deferred:** Approval/resolution remains M7.

### M6-S5 — Applicant request query and withdrawal

**Goal:** Let Applicants view only their request history and withdraw their own pending request.

**Implementation requirements:** Add applicant-safe summaries scoped from authenticated ownership. Withdrawal locks Applicant → Booking → request/guard, verifies that the request belongs to the owned Applicant/Booking and is `PENDING`, transitions to `WITHDRAWN`, removes guard atomically, and changes no booking/capacity. Do not require the source appointment to remain current: the owner may withdraw a request that became stale. Exact repeat is safe.

**Inspect:** ownership queries, M4 state mapping, M6 entities/repositories, history patterns.

**Acceptance criteria:** Owner sees only own requests; foreign ID returns no data; the owner can withdraw any still-`PENDING` request even when its source appointment is no longer current; terminal `STALE`, resolved, withdrawn, and foreign requests cannot be withdrawn; guard removal allows a later new request; booking/capacity remain unchanged.

**Required tests:** A/B query/withdraw, lifecycle/idempotency, guard release/new submission, concurrent withdraw/submit, DTO privacy/order.

**Security/concurrency:** Query by owned Applicant ID at database boundary; reauthorize withdrawal inside transaction.

**Self-review:** Field whitelist, state transition, guard atomicity, lock order, and no mutation side effects.

**Deferred:** Recruiter queue/resolution remains M7.

### M6-S6 — Portal request UI and milestone validation

**Goal:** Add responsive submit/view/withdraw flows and prove the M6 invariants.

**Implementation requirements:** Add clear request type/reason/preferences UI, explain that appointment remains active and preferences do not reserve capacity, prevent obvious double-submit, show status/history, and refresh authoritative state after actions.

**Inspect:** appointment portal, Binder/dialog patterns, user-safe notifier, complete M6 diff.

**Acceptance criteria:** Applicant can submit/view/withdraw with clear states; foreign/internal data never renders; no UI implies approval or capacity reservation; all M6 criteria and operations regressions pass.

**Required tests:** Component validation/state tests; browser/component happy path; ownership/stale/double-submit cases; clean suite and MySQL gate.

**Security/concurrency:** UI sends expected appointment identity and consumes safe DTOs only.

**Self-review:** Full milestone review for pending invariant, side-effect absence, privacy, BusinessTime, lock order, responsive UX, and regressions.

**Deferred:** Staff review and appointment mutation remain M7.

---

## M7 — Recruiter review and resolution

### Milestone goal

Provide a current-branch recruiter queue and atomic approve/decline actions that reuse established booking/capacity workflows.

### Milestone acceptance

- Recruiter sees requests for Applicants currently in their branch; Admin sees all.
- Approval revalidates current appointment and destination/capacity; cancellation/reschedule reuse booking workflows.
- Booking mutation and request resolution commit atomically; decline leaves booking unchanged.
- Repeated/stale resolution cannot change capacity twice.
- Supported A → B transfer removes A authority and gives B authority; active-booking transfer prohibition remains intact.

### M7-S1 — Resolution lifecycle fields and controlled transitions

**Goal:** Implement controlled resolution transitions and immutable resolution evidence.

**Implementation requirements:** Add domain methods/factories for `PENDING` → `APPROVED`/`DECLINED`/`STALE`, resolver or system reason as appropriate, resolution timestamp, safe staff response, and selected destination reference/snapshot where applicable. Use schema added in M6 or a new paired migration only if evidence shows fields were intentionally deferred. Remove pending guard only in the same transaction as a terminal transition. No generic setters/repository mutation APIs.

**Inspect:** M6 request model, audit/history factories, User/BusinessTime, repository contracts.

**Acceptance criteria:** Only pending requests transition; resolution metadata is complete/immutable; repeated exact result is idempotent and conflicting result rejected; guard removal is atomic.

**Required tests:** Transition matrix, immutability/API tests, idempotency/conflict, rollback retains pending guard, timestamp normalization.

**Security/concurrency:** Resolver identity is operational User; applicant-facing DTO excludes internal actor/response details not approved for disclosure.

**Self-review:** Controlled mutation surface, terminal-state immutability, guard lifecycle, audit/privacy, and migration need.

**Deferred:** Queue authorization and booking mutations remain later M7 steps.

### M7-S2 — Current-Applicant-branch scoped queue

**Goal:** Query pending requests by the Applicant's authoritative current branch.

**Implementation requirements:** Add paginated fetch/count queries using `request.applicant.branch`; Admin branch filter is unrestricted and Recruiter requires current branch. Include only fields needed for review, with stable ordering and no applicant credential/token data. Detail lookup uses the same scope.

**Inspect:** paginated Applicant/Booking/Hiring queries, recruiter workbench, branch-transfer tests.

**Acceptance criteria:** Same-branch Recruiter sees request; cross-branch does not; Admin sees all; transfer in a supported no-active-booking state immediately changes authority; fetch/count filters match.

**Required tests:** Repository/service pagination/count parity; role/branch/detail matrix; supported transfer A→B; no branchless recruiter access.

**Security/concurrency:** Never scope by source schedule/recruiter/history branch snapshot. Reauthorize again on mutation.

**Self-review:** Query joins/predicates/count parity, stable order, N+1, data minimization, and transfer behavior.

**Deferred:** Resolution actions remain M7-S3–S5.

### M7-S3 — Stale request validation

**Goal:** Detect and terminalize requests whose source appointment or eligibility no longer matches current state.

**Implementation requirements:** Centralize transactional validation comparing request source Booking/Schedule identity and snapshot/generation to the current booking, request pending state, Applicant active/current branch, future BusinessTime, and absence of incompatible terminal/evaluation state. When the source appointment has changed or become terminal/ineligible, atomically transition the request to `STALE` and remove its pending guard without mutating booking/capacity; return a safe non-actionable outcome so that terminalization commits rather than being rolled back with an exception. A new request is then required.

**Inspect:** M6 snapshots, booking reschedule/cancel/confirm paths, reminder generation, BusinessTime.

**Acceptance criteria:** Rescheduled/cancelled/past/replaced/evaluated pending requests become `STALE`, release the guard, and are non-actionable; withdrawn/already-resolved requests remain non-actionable; current valid request remains actionable; no booking/capacity/booking-history/booking-event side effect occurs during stale terminalization.

**Required tests:** Complete stale matrix, guard release followed by a new eligible request, concurrent appointment change versus resolution, fixed-clock boundaries, and rollback/guard retention.

**Security/concurrency:** Validate after locks, not from queue DTO. Use current Applicant branch for actor authority.

**Self-review:** Identity comparison strength, lock point, time source, no false use of snapshots as authority, atomic request/guard terminalization, and absence of booking-side effects.

**Deferred:** Actual approve/decline transitions remain next steps.

### M7-S4 — Atomic reschedule approval

**Goal:** Approve a reschedule request by reusing the established reschedule/capacity/history/event workflow atomically.

**Implementation requirements:** Accept request ID, expected request version/source schedule, and explicitly selected destination Schedule. Lock in the compatible order: Applicant → Booking → request/guard → source/destination schedules sorted by ID. Reauthorize current branch, run M7-S3 validation, and invoke a refactored shared booking reschedule transition inside the same transaction. Resolve request only after the booking transition succeeds; any failure rolls back both.

**Inspect:** `BookingService.reschedule`, schedule sorted locking, reschedule history/event/listener, M5 refactor pattern, M6/M7 model.

**Acceptance criteria:** Valid approval transfers capacity once, updates booking/recruiter/reminder generation, appends one reschedule history, resolves request/removes guard, and publishes one event. A full/invalid destination or cross-branch attempt changes nothing. An authorized stale request follows M7-S3 by transitioning only the request to `STALE` and releasing its guard, with no booking/capacity/reschedule-history/event effect. Exact/concurrent repeat cannot alter capacity twice.

**Required tests:** Transactional integration success/rollback; same/cross/admin authorization; sorted lock verification; concurrent duplicate approval and competing destination capacity in H2/MySQL; event AFTER_COMMIT; existing staff reschedule regression.

**Security/concurrency:** Preserve applicant → booking → sorted schedule ordering while consistently placing request/guard before schedules. Never reserve during review-page selection.

**Self-review:** Atomic boundary, reuse vs duplication, capacity arithmetic, status/history/event counts, stale/version checks, branch authority, and deadlock risk.

**Deferred:** Cancellation approval, decline, and review UI remain M7-S5/S6.

### M7-S5 — Atomic cancellation approval and decline

**Goal:** Resolve cancellation approvals through existing cancellation logic and allow safe decline without booking changes.

**Implementation requirements:** Refactor/reuse shared cancellation transition under the same Applicant → Booking → request/guard → Schedule order. Reauthorize current branch and run M7-S3 validation after locking. Approval atomically cancels/releases capacity/appends lifecycle history/publishes event/resolves request. Decline transitions only request metadata/removes guard and never changes booking/capacity/history/events. Exact retries are idempotent; conflicts fail.

**Inspect:** `BookingService.cancel`, cancellation integration/concurrency tests, lifecycle unique constraint/listener.

**Acceptance criteria:** Cancellation approval has one set of effects; decline has none on appointment; concurrent repeat/approve-vs-decline has one terminal winner; an authorized stale request terminalizes as `STALE` and releases only its guard; cross-branch access fails without change; staff direct cancellation remains unchanged.

**Required tests:** Approval/decline matrices, rollback, concurrent races, event/history/capacity counts, branch transfer authority, existing cancellation regression.

**Security/concurrency:** Reauthorize current branch after locks and contain constraint/optimistic failures as safe domain responses.

**Self-review:** Side-effect separation, guard removal, capacity floor, event timing, idempotency, lock order, and operations regressions.

**Deferred:** UI and outcome notifications remain M7-S6/M8.

### M7-S6 — Review UI, authorization/concurrency tests, and milestone validation

**Goal:** Deliver the branch-scoped review experience and validate M7 end to end.

**Implementation requirements:** Add paginated queue/detail actions in the appropriate operations UI, destination selection through existing eligible-schedule patterns, safe decline response, stale refresh guidance, and action double-submit protection. Service authorization remains decisive.

**Inspect:** recruiter workbench/paginated grids, booking reschedule dialog, user-safe notifier, complete M7 diff.

**Acceptance criteria:** Recruiter/Admin can review within scope and complete valid actions; cross-branch/stale actions fail safely; applicant portal reflects committed outcomes; all M7 criteria pass; clean suite and MySQL gate pass.

**Required tests:** Component/route/service matrix; browser/component staff flow; H2/MySQL concurrency suite; clean milestone gates.

**Security/concurrency:** Test current branch transfer only in states the application supports; do not weaken active-booking transfer prohibition.

**Self-review:** Full milestone review for query scope, mutation reauthorization, stale state, transaction/lock order, capacity, idempotency, safe UI data, and regressions.

**Deferred:** Outcome notification hardening and release E2E remain M8.

---

## M8 — Notifications and release validation

### Milestone goal

Deliver applicant-safe request outcomes after commit and validate the complete browser-based MVP for release.

### Milestone acceptance

- Messages contain only applicant-safe information in the configured timezone.
- Notifications occur after commit; failure never rolls back an approved/declined change; portal stays authoritative.
- Browser flow covers activation → login → view → confirm → request → recruiter resolution.
- Ownership, stale, duplicate, and relevant MySQL concurrency tests pass; both CI jobs pass on the release revision.

### M8-S1 — Applicant-safe outcome context, events, and templates

**Goal:** Define minimal notification contracts for request approval/decline.

**Implementation requirements:** Add ID-only domain events and narrow immutable notification contexts assembled from committed safe data. Add seeded/idempotent templates and event enum migrations for request outcomes. Include only Applicant name/email, request type/outcome, safe appointment details, safe staff response if policy allows, and configured timezone. Never pass Request/Booking entities to applicant template rendering.

**Inspect:** notification enum/data generator/templates, hiring/reset context patterns, TemplateRenderService, paired migrations.

**Acceptance criteria:** Templates render required safe fields; no internal notes/reasons/actors/scores/hiring/audit/other IDs are available; timezone is explicit/correct; seed remains idempotent.

**Required tests:** Migration/schema enum parity; template seed/render tests; context field whitelist; timezone and escaping tests; existing template regressions.

**Security/concurrency:** Context construction must re-check ownership/current committed state and avoid logging body/recipient/token data.

**Self-review:** Field-by-field privacy review, template variable allowlist, migration parity, seed idempotency, and safe formatting.

**Deferred:** Delivery timing and failure behavior remain M8-S2.

### M8-S2 — AFTER_COMMIT outcome delivery

**Goal:** Send outcome notifications only for committed resolution and contain delivery failures.

**Implementation requirements:** Publish once from successful terminal resolution and handle with `@TransactionalEventListener(AFTER_COMMIT)`; reload by stable ID in a new read-only transaction, build M8-S1 context, and contain failures. Decline and approval produce the correct outcome; rollback/no-op/idempotent retry produces no duplicate event.

**Inspect:** existing booking/hiring/reset listeners, async config, M7 resolution services, email service.

**Acceptance criteria:** Committed outcome triggers one attempt; rollback triggers none; email failure leaves booking/request outcome committed and visible; exact retry does not notify twice within domain-event semantics.

**Required tests:** Annotation phase, commit/rollback, success/failure containment, duplicate resolution, deleted/missing record, no real SMTP.

**Security/concurrency:** Portal is authoritative; document current best-effort/non-durable delivery limitations.

**Self-review:** Event publication point/count, separate transaction, failure logs, context safety, and no rollback coupling.

**Deferred:** General durable outbox/exactly-once external delivery remains post-MVP.

### M8-S3 — Headless browser happy-path E2E

**Goal:** Automate the complete MVP across anonymous, Applicant, Recruiter, and Admin surfaces.

**Implementation requirements:** Add a Maven-managed headless browser harness compatible with Vaadin/Spring Boot (prefer the repository's adopted tool; if none, make the smallest test-scoped choice and document it). Use isolated deterministic test data and mocked/captured email, never real SMTP or a developer DB. Cover invitation link capture, activation, login, appointment display/timezone, confirmation, request submission, recruiter resolution, and Applicant refresh.

**Inspect:** POM/test profiles, Vaadin routes/components, test application configuration, notification fakes, CI capabilities.

**Acceptance criteria:** One deterministic happy path runs headlessly and proves persisted outcomes; screenshots/logs on failure contain no secrets; test is runnable locally/CI with documented command.

**Required tests:** The E2E itself plus harness startup/isolation check; CI workflow update if a browser dependency/service is required.

**Security/concurrency:** Redact activation/reset tokens from retained artifacts and use ephemeral isolated storage.

**Self-review:** Dependency scope/version compatibility, flake controls/waits, data cleanup/isolation, secret handling, and reproducible command.

**Deferred:** Cross-browser matrix, visual regression service, calendar/SMS flows remain excluded.

### M8-S4 — Negative and release regression cases

**Goal:** Prove the MVP fails safely under ownership, stale, duplicate, inactive, and concurrent scenarios.

**Implementation requirements:** Assemble a release test matrix linking every milestone acceptance criterion to automated evidence. Add missing browser/service/repository/MySQL cases for foreign IDs, stale appointment/request, duplicate activation/confirmation/request/resolution, deactivation, branch transfer in supported states, capacity contention, rollback, and privacy.

**Inspect:** All milestone tests and status logs, route matrix, MySQL concurrency suite, privacy field lists.

**Acceptance criteria:** Every criterion has passing automated evidence or an explicit release blocker; no flaky quarantine/ignored security test; existing recruiter/admin workflows remain green.

**Required tests:** Focused negative E2E/component cases, complete ownership/security suites, relevant MySQL concurrency tests, clean standard and MySQL gates.

**Security/concurrency:** Treat any cross-applicant disclosure, duplicate capacity mutation, missing reauthorization, or migration mismatch as release-blocking.

**Self-review:** Map requirements to tests, inspect skipped tests, rerun race-prone cases, and review artifacts for secrets/personal data.

**Deferred:** Post-MVP features stay excluded even if easy to add during release hardening.

### M8-S5 — Deployment documentation and final release gate

**Goal:** Produce accurate rollout/operation guidance and determine release readiness.

**Implementation requirements:** Update current-state, account security, migration, business-time, configuration, invitation/recovery, notification, and production checklist docs. Document migration order/data preflights, required secrets, email limitations/retry, business zone, session limitation, monitoring, rollback/mitigation, and CI/E2E commands. Run the final gates on the release revision and record exact evidence in `STATUS.md`.

**Inspect:** All existing docs, `.github/workflows/ci.yml`, POM/properties, migrations, complete MVP diff/status.

**Acceptance criteria:** Documentation matches code; all M1–M8 steps are `DONE`; `./mvnw clean test`, `./mvnw clean verify -Pmysql-it`, headless E2E, and both CI jobs pass; independent review has no blocking issue; no secrets/generated noise/unrelated source changes are present.

**Required tests:** Final standard/MySQL/E2E commands and documentation link/config validation; production checklist review.

**Security/concurrency:** Require rehearsal/backups for real migration, never perform rollout without separate authorization, and retain explicit best-effort email/session limitations.

**Self-review:** Fresh-clone command check, roadmap/status consistency, config/secret audit, full privacy/authorization/lock/migration review, and release evidence accuracy.

**Deferred:** All post-MVP capabilities remain separate future roadmap work.
