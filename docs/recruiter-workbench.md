# Secure Recruiter Workbench

The recruiter workbench is available at `/workbench`. Recruiters can only list or mutate applicants,
bookings, schedules, and evaluations owned by their assigned branch. Administrator access remains global.

## Applicant branch rollout

`applicants.branch_id` is intentionally nullable during the legacy-data reconciliation stage. New applicants
must have a branch: recruiter-created applicants inherit the recruiter's branch, while administrators select
the owning branch. Existing branchless applicants remain visible to administrators for reconciliation but are
never returned to recruiters and cannot be booked until assigned.

Before making the column `NOT NULL` in a future migration, administrators should assign every legacy applicant
to an authoritative branch and verify that each active booking's applicant branch matches its schedule branch.

## Database invariants

Hibernate schema management remains unchanged for this MVP. The entity model adds the nullable applicant
branch foreign key and a named unique constraint on `interview_evaluations.booking_id`, enforcing one
evaluation per booking. A migration framework was not introduced because the current application has no
complete migration baseline and production still uses `spring.jpa.hibernate.ddl-auto=update`.
