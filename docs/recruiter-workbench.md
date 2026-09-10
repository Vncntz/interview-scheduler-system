# Secure Recruiter Workbench

The recruiter workbench is available at `/workbench`. It shows the recruiter's interviews today,
upcoming assigned interviews, branch-scoped pending confirmations, attendance actions, and overdue
evaluations. Each row includes the explicit interview stage and a link to the authorized applicant
profile. It also includes separate, lazily paged `FINAL` and `CLIENT` follow-up queues with position,
client, previous appointment, waiting duration, deadline, SLA status, and a guided **Schedule
Interview** action. Recruiters can only list or
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
is specifically for final/client follow-up. The repository returns stage-specific scalar projection
pages filtered and ordered in the database, without loading all applicants or filtering in the UI.
Matching aggregate queries show total, on-track, due-soon, overdue, and timing-unavailable counts.

Waiting time is derived rather than persisted. Progression items use the matching evaluation date.
For progression, follow-up starts at the qualifying evaluation event; the related appointment is read
only from that booking's immutable `ATTENDANCE_RECORDED` snapshot. For replacement scheduling,
follow-up starts at the matching cancellation or no-show event and the related appointment comes from
that same immutable lifecycle row. A record without the required historical evidence stays visible and
schedulable, shows **Timing unavailable**, and is excluded from deadline classifications. Mutable
booking update timestamps and current schedule values are never used to reconstruct history.

The provisional defaults are 72 elapsed calendar hours for `FINAL` and 120 hours for `CLIENT`, with a
24-hour due-soon window. `Overdue` begins exactly at the deadline. `Due soon` includes the instant at
which remaining time reaches the due-soon window and ends just before the deadline; earlier items are
`On track`. Future source timestamps display zero elapsed time. These indicators are
informational only and do not block, automatically schedule, or notify anyone.

Override the targets with `INTERVIEW_FOLLOW_UP_FINAL_TARGET`,
`INTERVIEW_FOLLOW_UP_CLIENT_TARGET`, and `INTERVIEW_FOLLOW_UP_DUE_SOON_WINDOW` using Spring
duration syntax such as `72h`. All three durations must be positive, and the due-soon window must be
shorter than both targets. `INTERVIEW_FOLLOW_UP_TIMESTAMP_ZONE` defaults to the JVM timestamp zone
and must match the zone used for existing zone-less evaluation and lifecycle timestamps. Deadlines are
derived at query time, so a configuration change reclassifies all existing timed rows after application
configuration reload or restart; it does not rewrite history.

The required stage is derived through `BookingStageEligibilityPolicy`. The dialog displays that stage
read-only, but the UI value is never trusted: `BookingService` locks and reloads the applicant, checks
branch scope and active-booking uniqueness, revalidates the requested stage, then locks the schedule
and allocates capacity transactionally. A stale queue item, cross-branch attempt, wrong stage, or
concurrent duplicate is rejected by the backend. A successful booking refreshes the workbench and
removes the applicant from the queue.

Remaining limitation: no reminder or durable-notification workflow is implied by this informational
feature.

## Applicant branch ownership

`applicants.branch_id` is required. Recruiter-created applicants use the recruiter's assigned branch, while
administrators must explicitly select the owning branch. No branch is inferred for administrators and no
placeholder branch is created. Legacy branchless applicants must be reconciled before Flyway V2 can complete;
see [database-migrations.md](database-migrations.md) for the controlled rollout procedure.

## Database invariants

Flyway owns schema changes and Hibernate validates the migrated schema. The applicant branch foreign key is
non-null after V2, and the named unique constraint on `interview_evaluations.booking_id` enforces one
evaluation per booking.
