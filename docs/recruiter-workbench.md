# Secure Recruiter Workbench

The recruiter workbench is available at `/workbench`. Recruiters can only list or mutate applicants,
bookings, schedules, and evaluations owned by their assigned branch. Administrator access remains global.

## Applicant branch ownership

`applicants.branch_id` is required. Recruiter-created applicants use the recruiter's assigned branch, while
administrators must explicitly select the owning branch. No branch is inferred for administrators and no
placeholder branch is created. Legacy branchless applicants must be reconciled before Flyway V2 can complete;
see [database-migrations.md](database-migrations.md) for the controlled rollout procedure.

## Database invariants

Flyway owns schema changes and Hibernate validates the migrated schema. The applicant branch foreign key is
non-null after V2, and the named unique constraint on `interview_evaluations.booking_id` enforces one
evaluation per booking.
