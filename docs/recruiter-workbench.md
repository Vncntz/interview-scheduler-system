# Secure Recruiter Workbench

The recruiter workbench is available at `/workbench`. It shows the recruiter's interviews today,
upcoming assigned interviews, branch-scoped pending confirmations, attendance actions, and overdue
evaluations. Each row includes the explicit interview stage and a link to the authorized applicant
profile. It also includes separate `FINAL` and `CLIENT` follow-up queues with position, client, last
interview, waiting duration, and a guided **Schedule Interview** action. Recruiters can only list or
mutate operational records within their assigned branch. The workbench route and read model remain
recruiter-only; administrators continue to use the organization-wide dashboard and management views.

## Interview follow-up qualification

The database-backed queue includes active applicants in the recruiter's authoritative branch when:

- a matching evaluation moved the applicant to `FOR_FINAL_INTERVIEW` or
  `FOR_CLIENT_INTERVIEW`; or
- the applicant remains `SCHEDULED` and their most recent `FINAL` or `CLIENT` booking was
  `CANCELLED` or `NO_SHOW`.

An applicant is excluded if any `BOOKED`, `CONFIRMED`, or legacy `RESCHEDULED` booking exists.
Cancelled or missed `INITIAL` interviews are not shown in this queue because this workbench section
is specifically for final/client follow-up. The repository returns a scalar projection ordered by the
derived waiting timestamp and applicant ID, without loading all applicants or filtering in the UI.

Waiting time is derived rather than persisted: progression items use the matching evaluation date,
while cancelled/no-show replacements use the most recent booking update timestamp. These timestamps
are operational indicators, not an enforced SLA.

The required stage is derived through `BookingStageEligibilityPolicy`. The dialog displays that stage
read-only, but the UI value is never trusted: `BookingService` locks and reloads the applicant, checks
branch scope and active-booking uniqueness, revalidates the requested stage, then locks the schedule
and allocates capacity transactionally. A stale queue item, cross-branch attempt, wrong stage, or
concurrent duplicate is rejected by the backend. A successful booking refreshes the workbench and
removes the applicant from the queue.

Remaining limitations: the queues are currently unpaged, no follow-up SLA is configured, and no
reminder or durable-notification workflow is implied by this feature.

## Applicant branch ownership

`applicants.branch_id` is required. Recruiter-created applicants use the recruiter's assigned branch, while
administrators must explicitly select the owning branch. No branch is inferred for administrators and no
placeholder branch is created. Legacy branchless applicants must be reconciled before Flyway V2 can complete;
see [database-migrations.md](database-migrations.md) for the controlled rollout procedure.

## Database invariants

Flyway owns schema changes and Hibernate validates the migrated schema. The applicant branch foreign key is
non-null after V2, and the named unique constraint on `interview_evaluations.booking_id` enforces one
evaluation per booking.
