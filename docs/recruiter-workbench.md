# Secure Recruiter Workbench

The recruiter workbench is available at `/workbench`. It shows the recruiter's interviews today,
upcoming assigned interviews, branch-scoped pending confirmations, attendance actions, and overdue
evaluations. Each row includes the explicit interview stage and a link to the authorized applicant
profile. Recruiters can only list or mutate operational records within their assigned branch;
administrator operational access remains global.

The workbench does not yet provide a dedicated follow-up queue for applicants whose completed
evaluation moved them to `FOR_FINAL_INTERVIEW` or `FOR_CLIENT_INTERVIEW` but who do not yet have the
next booking. That guided queue is recommended next work; the existing booking service already
enforces the eligible `FINAL` or `CLIENT` stage when a booking is created.

## Applicant branch ownership

`applicants.branch_id` is required. Recruiter-created applicants use the recruiter's assigned branch, while
administrators must explicitly select the owning branch. No branch is inferred for administrators and no
placeholder branch is created. Legacy branchless applicants must be reconciled before Flyway V2 can complete;
see [database-migrations.md](database-migrations.md) for the controlled rollout procedure.

## Database invariants

Flyway owns schema changes and Hibernate validates the migrated schema. The applicant branch foreign key is
non-null after V2, and the named unique constraint on `interview_evaluations.booking_id` enforces one
evaluation per booking.
