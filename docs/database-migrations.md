# Database migrations

Flyway owns the application schema. Production uses the MySQL migrations under
`db/migration/mysql`; fast/default tests use the equivalent H2 migrations under `db/migration/h2`.
Hibernate runs with `ddl-auto=validate` and must never be used to repair a production schema.

## Automated database validation

The default Java 25 suite remains Docker-free and uses isolated H2 in MySQL compatibility mode:

```powershell
.\mvnw.cmd clean test
```

The opt-in integration profile requires Docker and starts an isolated `mysql:8.4.6` Testcontainer.
Spring Boot supplies the container JDBC connection through a service connection; the suite never uses
developer datasource variables or a fixed host port. It applies the production MySQL V1-through-V8
migrations to an empty schema, validates their checksums and current version, starts the complete Spring
context with Hibernate `ddl-auto=validate`, and exercises V8's critical reminder mappings, uniqueness,
foreign key, enum values, microsecond timestamps, and processing indexes:

```powershell
.\mvnw.cmd clean verify -Pmysql-it
```

On Unix-like systems, use `./mvnw` in the same commands. The `verify` command runs the fast H2 tests
before the MySQL Failsafe tests, so it is the complete local database verification gate. Docker being
unavailable is a test-environment failure; the MySQL suite is not silently skipped.

This fresh-schema Testcontainers gate proves MySQL syntax and schema/mapping compatibility. It does not
replace an isolated production-sized restoration rehearsal for upgrade-path data, metadata-lock duration,
disk use, query plans, backup restoration, or rollback evidence.

## Before rollout

1. Stop application writes and take a tested, restorable MySQL backup (physical snapshot or a
   consistent logical dump including routines, triggers, and the Flyway history table if present).
2. Confirm the backup can be restored to an isolated database.
3. Compare the existing database with the V1 migration. The existing database must already contain
   all V1 tables, columns, indexes, unique constraints, foreign keys, enum values, lengths, and
   nullability. Resolve every difference before baselining; Flyway baselining records history but
   does not validate or create the V1 objects.
4. Run the reconciliation queries below and keep the results with the deployment record.

Use the production schema name explicitly when running operational SQL. Useful preflight checks are:

```sql
SELECT COUNT(*) AS branchless_applicants
FROM applicants
WHERE branch_id IS NULL;

SELECT a.id, a.email, a.branch_id, s.branch_id AS schedule_branch_id, b.id AS booking_id, b.status
FROM applicants a
JOIN bookings b ON b.applicant_id = a.id
JOIN schedules s ON s.id = b.schedule_id
WHERE b.status IN ('BOOKED', 'CONFIRMED')
  AND (a.branch_id IS NULL OR a.branch_id <> s.branch_id);

SELECT a.branch_id, COUNT(*) AS applicant_count
FROM applicants a
GROUP BY a.branch_id
ORDER BY a.branch_id;
```

## Explicit applicant reconciliation

V2 does not infer, assign, or rewrite applicant ownership. An administrator must determine each
branchless applicant's authoritative branch from business records. Apply reviewed assignments by
primary key, preferably in small transactions, for example:

```sql
START TRANSACTION;
UPDATE applicants SET branch_id = :authoritative_branch_id WHERE id = :applicant_id AND branch_id IS NULL;
COMMIT;
```

Do not derive ownership from the currently signed-in user, create a placeholder branch, or bulk-copy
a schedule branch without business verification. After reconciliation, both preflight queries must
return zero rows/counts before V2 is allowed to run.

## Fresh database rollout

For an empty database, keep `FLYWAY_BASELINE_ON_MIGRATE` unset (its default is `false`). Start the
application with normal datasource credentials. Flyway applies V1 through V8 in order, after which
Hibernate validates the resulting schema. The fresh schema has `applicants.branch_id NOT NULL`, the
final hiring decision workflow tables, secure account lifecycle tables, and no persisted notification
credential columns.

## Existing current-schema rollout

The one supported baseline case is a known current database whose schema has been reconciled to V1
and has no Flyway history table.

1. Complete backup, structural comparison, and applicant reconciliation.
2. For one controlled deployment only, set `FLYWAY_BASELINE_ON_MIGRATE=true` and
   `FLYWAY_BASELINE_VERSION=1`.
3. Start one application instance. Flyway records version 1 as the baseline and then runs V2 through V8.
4. Verify `flyway_schema_history` contains the version 1 baseline and successful version 2 through 8 migrations.
5. Stop the instance, remove the baseline override, and restart with
   `FLYWAY_BASELINE_ON_MIGRATE=false` (or the variable unset) before scaling out.

Never leave baseline-on-migrate enabled. Never baseline an empty database or a schema whose exact
provenance and V1 compatibility have not been established.

## MySQL DDL caveats

`ALTER TABLE ... MODIFY COLUMN ... NOT NULL` may rebuild or copy the table, take metadata locks, and
consume significant temporary disk depending on MySQL version, table size, and online-DDL support.
Test V2 against a production-sized restored copy. Schedule a maintenance window, monitor lock waits,
replication lag, free disk, and migration duration, and ensure application writes are stopped. A
branchless row causes V2 to fail; that is intentional protection against silent ownership changes.

Flyway migrations are forward-only. Do not edit an applied migration or use `flyway repair` to hide a
real checksum/schema mismatch. Investigate and reconcile the database instead.

## V3 final hiring workflow rollout

V3 expands the MySQL `applicants.status` enum and creates `hiring_decisions` plus append-only
`hiring_decision_audits`. It does not backfill or infer offers from existing `PASSED` or `HIRED`
applicants. Before rollout, verify position counters are trustworthy and no position has
`hired_count > required_headcount`:

```sql
SELECT id, title, hired_count, required_headcount
FROM position_openings
WHERE hired_count > required_headcount;
```

MySQL enum changes and table creation may auto-commit and take metadata locks. Test V3 against a
production-sized restored copy, keep application writes stopped during migration, and retain the
pre-deployment backup until hiring workflow smoke tests pass. Rollback requires restoring the
matching database backup and prior application binaries; dropping the new tables would destroy
audit history and is not an approved rollback strategy.

## V4 secure account lifecycle rollout

V4 expands the notification event enum and creates `password_reset_requests` and append-only
`account_security_audits`. It preserves every existing password hash, lockout value, login counter,
and password-change flag; it performs no account-state inference or backfill.

Before applying V4, configure a trusted externally visible `APP_BASE_URL` and a base64-encoded random
`ACCOUNT_RESET_TOKEN_SECRET` containing at least 32 bytes. Missing values safely disable reset
initiation without preventing startup. Use HTTPS outside local development. Rotating the signing
secret immediately invalidates all outstanding reset links, so coordinate rotation with users and
issue new links afterward. Never print or record either the signing secret or reset links in rollout
evidence.

The MySQL notification enum alteration may take a metadata lock. Rehearse V4 against an isolated,
production-sized restored copy and inspect the two new tables, foreign keys, uniqueness constraints,
and indexes before scheduling the real rollout. Keep application writes stopped while the migration
runs. V4 is forward-only: destructive table removal would erase security audit history and is not an
approved rollback. Restore the pre-deployment backup together with the prior application binaries if
a rollback is required.

## V5 notification secret removal rollout

V5 first disables SMS delivery for every notification settings row and then drops the legacy
`smtp_password` and `sms_api_key` columns. It preserves company name, email enablement, SMTP host,
port, username, sender name, SMS provider metadata, SMS sender metadata, activity state, timestamps,
and optimistic-lock version. The SMTP password must be supplied to the application process through
`SMTP_PASSWORD`; there is no runtime SMS sender or `SMS_API_KEY` replacement.

Before applying V5, provision `SMTP_PASSWORD` in the approved external secret source for deployments
that require email. Rehearse the complete V1-to-V8 path and a V4-to-V8 upgrade against an isolated
MySQL database restored from representative data. Confirm that non-secret notification settings are
preserved, SMS is disabled, both legacy columns are absent, Hibernate validation succeeds, and no
real notification is delivered during rehearsal.

V5 is incompatible with pre-V5 application binaries because those binaries still map the removed
columns. The only supported rollback is to restore the matching pre-V5 database backup and deploy
the matching old binary together. Do not manually recreate the dropped columns, edit an applied
migration, or use Flyway repair as a rollback mechanism. Backups taken before V5 may contain live or
historic credentials; protect them as secrets and revoke those provider credentials after migration.

## V6 explicit interview stage rollout

V6 adds the required `bookings.interview_stage` enum with `INITIAL`, `FINAL`, and `CLIENT`. The
migration intentionally adds the column as nullable, backfills every existing booking to `INITIAL`,
then removes nullability without leaving a database default. Historical final/client intent cannot be
reconstructed safely from mutable applicant status, so the conservative `INITIAL` backfill avoids
inventing unsupported history.

Before rollout, record the booking row count and verify no application instance can write during the
column addition/backfill/nullability sequence. Rehearse V5-to-V6 against an isolated,
production-sized MySQL restoration and confirm all rows have a stage, no default remains, Hibernate
schema validation succeeds, and the application can read historical bookings. MySQL enum/column
changes may take metadata locks or rebuild the table depending on server version and table size.

V6 application binaries require the new non-null column. Rollback therefore requires restoring the
matching pre-V6 backup and deploying the pre-V6 binary together; dropping the column while V6 code is
running is not supported.

## V7 SMTP settings hardening rollout

V7 adds non-secret SMTP provider, security mode, and sender-address metadata. It backfills
`STARTTLS`, infers Gmail and Microsoft 365 providers from their standard hosts, treats all other hosts
as custom, and copies a nonblank SMTP username into the sender address. It also adds the append-only
notification settings audit table. The migration does not add or restore any SMTP password column.

Before rollout, provision `SMTP_PASSWORD` externally wherever email is enabled. Rehearse V6-to-V7
and the fresh V1-to-V7 path against an isolated MySQL restoration. Confirm provider/security values are
non-null, sender-address backfills are correct, the audit foreign keys and index exist, legacy secret
columns remain absent, and Hibernate validation succeeds. Existing rows with a blank username retain a
null sender address; administrators must complete that value before email can be enabled or tested.

Rollback should restore the matching pre-V7 backup and deploy the pre-V7 binary together if the schema
must be reversed. Do not drop V7 columns or its audit table while V7 code is running, and never use
Flyway clean or edit the applied migration.

## V8 duplicate-safe interview reminder rollout

V8 adds and backfills the required `bookings.reminder_generation` column, expands the notification
event enum for 24-hour and 2-hour reminders, creates `interview_reminder_deliveries`, and adds the
schedule scan index. Delivery identity is unique by booking, reminder generation, and reminder type.
Rescheduling the same booking advances its generation, so the new appointment receives an independent
pair of reminder identities while old-generation delivery rows remain diagnostic history.

The scheduler is disabled by default. Enable it only after both reminder templates are present, email
settings are valid, and the runtime SMTP credential is provisioned. The default business zone is
`Asia/Manila`; verify the configured zone matches the business interpretation of schedule date/time.
Configure the stale-claim timeout comfortably above the sum of the SMTP connection, read, and write
timeout budgets plus expected processing margin; the application validates these values individually
but does not cross-validate their combined budget. Lease-token checks protect database completion state,
but recovery is at-least-once: a worker can reclaim and send again if another SMTP attempt outlives the
stale-claim timeout.

Rehearse both a fresh V1-to-V8 migration and V7-to-V8 upgrade on an isolated representative MySQL
restoration. Confirm the generation backfill has no lasting database default, the unique and booking
foreign-key constraints exist, and the retry/claim and schedule-scan indexes are present.

The MySQL enum alteration, booking backfill/nullability change, and new schedule index can take metadata
locks or rebuild structures depending on server version and data volume. Record lock time and query-plan
behavior during rehearsal before enabling the scheduler. Rollback requires restoring the matching pre-V8
backup and binary; do not drop delivery history while V8 code is running.

## Failure, rollback, and recovery

If V2 fails because branchless rows remain, keep the application stopped, reconcile those rows, and
rerun the migration. MySQL DDL may auto-commit, so do not assume a surrounding transaction rolls back
the statement. Inspect both `INFORMATION_SCHEMA.COLUMNS` and `flyway_schema_history` before retrying.

If the database or migration state is uncertain, restore the pre-deployment backup to a new database,
verify it, redirect the application only through the approved operational process, and repeat the
preflight. Do not make `branch_id` nullable again while the application entity requires it. A rollback
of application binaries therefore requires restoring the matching pre-migration database backup or a
separately reviewed forward recovery migration.
