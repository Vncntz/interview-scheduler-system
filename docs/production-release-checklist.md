# Production release checklist

Use this checklist for a controlled production deployment. It documents required evidence; it does
not authorize application startup, database migration, credential changes, or notification delivery.

## 1. Release and recovery readiness

- Identify the exact reviewed application commit and confirm the Java 25 clean test suite passed.
- Review the final diff for secrets, debug code, generated frontend noise, and unrelated changes.
- Take a consistent MySQL backup and prove that it restores into an isolated database.
- Keep the matching pre-release binary and backup together for recovery.
- Review `docs/database-migrations.md`, including the V5 through V8 rollback constraints.
- Define the maintenance window, write freeze, deployment owner, rollback owner, and stop criteria.

## 2. Runtime configuration contract

Supply values from the approved environment or secret provider. Record only whether each value is
present; never record its value in release evidence or command output.

- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Email credential, when email is required: `SMTP_PASSWORD`.
- Interview reminders, when explicitly enabled: `INTERVIEW_REMINDERS_ENABLED`, business zone, scan
  interval, bounded batch/attempt settings, retry delay, and stale-claim timeout.
- Set the stale-claim timeout comfortably above the total configured SMTP connection, read, and write
  timeout budget plus expected processing margin. These settings are validated individually; the
  application does not cross-validate their combined budget.
- Password reset: `APP_BASE_URL`, `ACCOUNT_RESET_TOKEN_SECRET`.
- Optional first administrator bootstrap: `ADMIN_EMAIL`, `ADMIN_PASSWORD`; remove both after the
  account is created and require credential rotation.
- Flyway baseline variables are unset for a normal Flyway-managed database. Use
  `FLYWAY_BASELINE_ON_MIGRATE` and `FLYWAY_BASELINE_VERSION` only for the one controlled legacy
  baseline procedure documented in `docs/database-migrations.md`.

Production must not activate the `dev` profile. Keep `DEMO_DATA_ENABLED=false` (or unset) and ensure
`SPRING_PROFILES_ACTIVE` does not contain `dev`. Demo loading requires both
`SPRING_PROFILES_ACTIVE=dev` and `DEMO_DATA_ENABLED=true`; use that combination only in an explicitly
approved development environment. Never enable demo loading in production or against production
data.

## 3. Isolated MySQL rehearsal

- Use an exact, isolated target that cannot route to a developer or production schema.
- Restore representative pre-release data and verify application/database version compatibility.
- Rehearse a fresh V1-to-V8 migration and, when upgrading an existing installation, V7-to-V8.
- Confirm Flyway reports version 8 and every migration checksum validates.
- Confirm `notification_settings.smtp_password` and `notification_settings.sms_api_key` are absent.
- Confirm all `notification_settings.sms_enabled` values are false and non-secret settings remain.
- Confirm SMTP provider/security backfills, sender-address backfills, and the settings audit table.
- Confirm the runtime SMTP password is reported as present without displaying it, and verify the
  configured timeout values are appropriate for the environment.
- Confirm every booking has `interview_stage`, the column is non-null, and no database default remains.
- Confirm every booking has `reminder_generation`, the column is non-null with no database default,
  and reminder delivery uniqueness, booking foreign key, and scan/retry indexes exist.
- Confirm Hibernate schema validation succeeds with the release binary.
- Run controlled smoke checks with email delivery disabled or replaced by a safe test double. Runtime
  SMS delivery does not exist.
- Record duration, locks, disk use, errors, and recovery evidence without recording credentials or
  applicant contact data.

H2 MySQL mode is useful automated coverage but is not evidence that the MySQL DDL is production-safe.

## 4. Controlled production migration

- Obtain explicit authorization for the exact production target and maintenance window.
- Stop writes and all but one migration-owning application instance.
- Reconfirm the restorable backup, matching rollback binary, and runtime secret presence.
- Start the single approved instance and allow Flyway to migrate through V8.
- Verify `flyway_schema_history` is successful at version 8 before scaling out.
- Verify the two legacy notification secret columns are absent and SMS is disabled.
- Verify historical bookings are readable with the conservative `INITIAL` stage backfill.
- Verify SMTP provider/security metadata, sender addresses, settings audit persistence, and the
  administrator-only connection diagnostic. Send a test email only when that exact recipient and
  external delivery action are approved.
- Keep interview reminders disabled until schedule timezone semantics, templates, SMTP readiness,
  bounded retry settings, and the stale-claim-to-SMTP-timeout margin are verified. Enable them as a
  separately controlled rollout step.
- Verify Hibernate validation, application readiness, authentication, recruiter branch scope, booking,
  evaluation, hiring, password change, and password reset readiness.
- Perform at most the specifically approved notification delivery check; do not send test messages to
  real applicants.

## 5. Rollback and post-release

- If migration or schema state is uncertain, stop. Do not run Flyway clean or repair and do not
  manually recreate dropped columns.
- A V5/V6/V7/V8 rollback requires the matching pre-migration backup and old binary together. Restoring only one side
  leaves the schema and entity mappings incompatible.
- After a successful rollout, rotate or revoke legacy SMTP/SMS credentials that may have existed in
  the removed columns and apply secret-level retention controls to historic backups.
- Remove temporary bootstrap and baseline environment variables.
- Confirm production still has demo loading disabled and no `dev` profile active.
- Monitor authentication failures, database errors, notification failure categories, migration health,
  and application readiness without logging secrets or unnecessary personal data.
