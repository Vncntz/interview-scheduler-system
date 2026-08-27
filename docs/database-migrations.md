# Database migrations

Flyway owns the application schema. Production uses the MySQL migrations under
`db/migration/mysql`; automated tests use the equivalent H2 migrations under `db/migration/h2`.
Hibernate runs with `ddl-auto=validate` and must never be used to repair a production schema.

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
application with normal datasource credentials. Flyway applies V1 and V2 in order, after which
Hibernate validates the resulting schema. The fresh schema has `applicants.branch_id NOT NULL`.

## Existing current-schema rollout

The one supported baseline case is a known current database whose schema has been reconciled to V1
and has no Flyway history table.

1. Complete backup, structural comparison, and applicant reconciliation.
2. For one controlled deployment only, set `FLYWAY_BASELINE_ON_MIGRATE=true` and
   `FLYWAY_BASELINE_VERSION=1`.
3. Start one application instance. Flyway records version 1 as the baseline and then runs V2.
4. Verify `flyway_schema_history` contains the version 1 baseline and successful version 2 migration.
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

## Failure, rollback, and recovery

If V2 fails because branchless rows remain, keep the application stopped, reconcile those rows, and
rerun the migration. MySQL DDL may auto-commit, so do not assume a surrounding transaction rolls back
the statement. Inspect both `INFORMATION_SCHEMA.COLUMNS` and `flyway_schema_history` before retrying.

If the database or migration state is uncertain, restore the pre-deployment backup to a new database,
verify it, redirect the application only through the approved operational process, and repeat the
preflight. Do not make `branch_id` nullable again while the application entity requires it. A rollback
of application binaries therefore requires restoring the matching pre-migration database backup or a
separately reviewed forward recovery migration.
