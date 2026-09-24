# Applicant Self-Service MVP status

> Baseline inspected: `origin/main` at `d582df195232b0e2baf5ea989d07994571bed237` (2026-09-24, `feat: standardize business time handling (#24)`).
>
> This file tracks implementation state. It is not evidence that a step passed; each `DONE` entry requires a completion-log record with actual test/review evidence.

## Current pointer

- `ACTIVE`: M1 — Define applicant identity and access
- `NEXT`: M1-S2 — Applicant authentication/identity resolver
- Recommended branch: `feat/applicant-self-service-m1`
- Allowed statuses: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`

The inspected baseline defines the `APPLICANT` role but did not provide applicant self-service. M1-S1 is complete; later steps have not started.

## Pointer rules

- `NEXT` is the first incomplete, unblocked step in `ACTIVE` unless a prerequisite is blocked.
- Set a step to `IN_PROGRESS` only while actively implementing it.
- Set `BLOCKED` only with a blocker-log entry describing evidence and the decision/input required.
- Set `DONE` only after all step acceptance criteria, required focused tests, self-review, and in-scope fixes pass.
- Advance `ACTIVE` only when every step in the current milestone is `DONE` and its milestone gate passes.
- One milestone uses one focused branch/PR. Do not advance to a later milestone silently.

## Progress

| Step | Status | Summary |
|---|---|---|
| M1-S1 | DONE | Database-backed User → Applicant ownership |
| M1-S2 | TODO | Applicant authentication/identity resolver |
| M1-S3 | TODO | Applicant ownership guard |
| M1-S4 | TODO | Applicant landing route and portal shell |
| M1-S5 | TODO | Route isolation/regression coverage and milestone validation |
| M2-S1 | TODO | Invitation persistence and secure token service |
| M2-S2 | TODO | Branch-scoped invitation issuance |
| M2-S3 | TODO | Email delivery state and retry |
| M2-S4 | TODO | Single-use account activation |
| M2-S5 | TODO | Staff invitation controls and milestone validation |
| M3-S1 | TODO | Applicant login and navigation |
| M3-S2 | TODO | Applicant password change through existing lifecycle service |
| M3-S3 | TODO | Non-enumerating Applicant forgot-password flow |
| M3-S4 | TODO | Staff Applicant account deactivate/reactivate |
| M3-S5 | TODO | Email/ownership regression rules and milestone validation |
| M4-S1 | TODO | Explicit applicant-safe Schedule details |
| M4-S2 | TODO | Applicant-safe appointment DTO/query service |
| M4-S3 | TODO | Current/empty/cancelled/rescheduled state mapping |
| M4-S4 | TODO | Responsive appointment portal UI |
| M4-S5 | TODO | Privacy regression tests and milestone validation |
| M5-S1 | TODO | Refactor confirmation transition for safe reuse |
| M5-S2 | TODO | Applicant-owned confirmation command with appointment identity |
| M5-S3 | TODO | Future eligibility and concurrency-safe idempotency |
| M5-S4 | TODO | Portal confirmation UI |
| M5-S5 | TODO | Confirmation concurrency/regression validation |
| M6-S1 | TODO | Change-request persistence with database pending guard |
| M6-S2 | TODO | Optional preferred date/time windows |
| M6-S3 | TODO | Owned request submission |
| M6-S4 | TODO | Duplicate and concurrent submission handling |
| M6-S5 | TODO | Applicant request query and withdrawal |
| M6-S6 | TODO | Portal request UI and milestone validation |
| M7-S1 | TODO | Resolution lifecycle fields and controlled transitions |
| M7-S2 | TODO | Current-Applicant-branch scoped queue |
| M7-S3 | TODO | Stale request validation |
| M7-S4 | TODO | Atomic reschedule approval |
| M7-S5 | TODO | Atomic cancellation approval and decline |
| M7-S6 | TODO | Review UI, authorization/concurrency tests, and milestone validation |
| M8-S1 | TODO | Applicant-safe outcome context, events, and templates |
| M8-S2 | TODO | AFTER_COMMIT outcome delivery |
| M8-S3 | TODO | Headless browser happy-path E2E |
| M8-S4 | TODO | Negative and release regression cases |
| M8-S5 | TODO | Deployment documentation and final release gate |

## Completion log

Append one entry only after a step becomes `DONE`:

```markdown
### YYYY-MM-DD — Mx-Sy DONE

- Baseline/branch: `<commit or branch>`
- Behavior delivered: <concise outcome>
- Files changed: <paths or grouped areas>
- Acceptance criteria: PASS — <evidence summary>
- Tests run:
  - `<exact command>` — PASS (`<tests>`, 0 failures, 0 errors, `<skipped>` skipped)
- Self-review: <findings and fixes, or “no in-scope blocking/should-fix findings”>
- Migration/security/concurrency notes: <relevant evidence or N/A>
- Deferred: <items intentionally left to later stable IDs>
- Next: `Mx-Sy`
```

Do not copy historical test counts as new evidence. If a required command could not run, the step is not `DONE` unless the roadmap explicitly permits that limitation; final milestone MySQL CI is always required before merge.

## Blocker log

Append an entry whenever a step becomes `BLOCKED`:

```markdown
### YYYY-MM-DD — Mx-Sy BLOCKED

- Blocking condition: <specific unmet requirement or repository contradiction>
- Evidence: <file/test/command and observed result>
- Work completed safely: <what is usable, if anything>
- Decision or external change required: <exact user/owner action>
- Status impact: `ACTIVE=<milestone>`, `NEXT=<blocked step>`
```

When resolved, add a dated resolution note to the same entry, return the step to `TODO` or `IN_PROGRESS`, and resume the normal execution loop. Never skip a blocked prerequisite.

## Completion log entries

### 2026-09-24 — M1-S1 DONE

- Baseline/branch: `origin/main@d582df195232b0e2baf5ea989d07994571bed237` / `feat/applicant-self-service-m1`
- Behavior delivered: Added an immutable, database-backed `User` → `Applicant` ownership relationship with one-user-per-applicant uniqueness, role/link consistency constraints, recruiter validation, and migration rollout guidance.
- Files changed: `User`, `RecruiterService`, paired H2/MySQL V11 migrations, migration/repository/service tests, application-context configuration, and migration/status documentation.
- Acceptance criteria: PASS — H2 and MySQL migrations apply through V11; legacy-invalid data is rejected; ownership uniqueness, foreign-key integrity, role/link consistency, immutable service behavior, and unchanged operations-user creation are covered.
- Tests run:
  - `.\mvnw.cmd clean test` — PASS (502 tests, 0 failures, 0 errors, 0 skipped)
  - `.\mvnw.cmd clean verify -Pmysql-it` — PASS (502 Surefire tests and 27 Failsafe tests, 0 failures, 0 errors, 0 skipped)
- Self-review: No in-scope blocking or should-fix findings after the final MySQL evidence review.
- Migration/security/concurrency notes: Paired V11 migrations enforce the ownership invariant in both databases; MySQL 8.4.6 Testcontainers migration and concurrency verification passed. V10 remains byte-for-byte aligned with `origin/main`.
- Deferred: Applicant identity resolution, ownership guards, portal routes, and later self-service behavior remain assigned to M1-S2 and subsequent stable steps.
- Next: `M1-S2`

## Blocker log entries

### 2026-09-24 — M1-S1 BLOCKED

- Blocking condition: Required MySQL migration/schema assertions have not executed because no Docker-, Podman-, or other Testcontainers-compatible runtime is installed or available on `PATH` or in standard installation locations.
- Evidence: Java 25 focused tests passed 23/23, the targeted application-context test passed 2/2, and `.\mvnw.cmd clean test` passed 502/502. `docker`, `podman`, `nerdctl`, and `colima` are unavailable, so `.\mvnw.cmd clean verify -Pmysql-it` cannot run locally. Independent final review classified the missing MySQL execution as blocking under the status contract.
- Work completed safely: Implemented the immutable User → Applicant mapping, paired V11 migrations, H2/MySQL test coverage, recruiter validation, and rollout documentation. V10 migrations were restored byte-for-byte from current `origin/main`; generated-file hashes remain unchanged from preflight.
- Decision or external change required: Run `.\mvnw.cmd clean verify -Pmysql-it` with Java 25 in an environment with Docker/Testcontainers, fix any in-scope failure, and independently review the resulting evidence before marking M1-S1 `DONE`.
- Status impact: `ACTIVE=M1`, `NEXT=M1-S1`
- Resolution (2026-09-24): Docker Desktop became available after WSL 2/virtualization initialization. `.\mvnw.cmd clean verify -Pmysql-it` then passed with 502 Surefire and 27 Failsafe tests, all with zero failures, errors, or skips. Independent review found no remaining in-scope blocker; M1-S1 advanced to `DONE` and `NEXT` advanced to M1-S2.
