# Hiring Decisions Workspace

The hiring decisions workspace is available at `/hiring-decisions` to administrators and recruiters.
Administrators see organization-wide results. Recruiters see only applicants assigned to their
authoritative branch. The service resolves the current operations user for every page and count query;
the same branch predicate is applied to both.

## Worklists

The workspace contains three independently paged grids:

- **Eligible passed candidates** includes active passed applicants whose matching booking and latest
  qualifying evaluation are passed, whose position is active and open with remaining headcount, and
  who have no existing hiring decision. The default order is evaluation time descending, then
  evaluation ID descending.
- **Outstanding offers** includes `OFFERED` decisions. Its default database order is overdue, due soon,
  on track, then no deadline; each timed group uses the earliest response deadline first and decision ID
  is the deterministic tie-breaker.
- **Completed decisions** includes `HIRED`, `DECLINED`, and `WITHDRAWN` decisions, ordered by
  resolution time descending and decision ID descending by default.

Each grid requests at most 50 rows from its Vaadin callback provider. The service rejects offsets
outside the JPA-supported integer range and page sizes outside 1 through 100. User-selected sorting is
restricted to the columns exposed by that grid and always adds a unique ID tie-breaker.

## Offer response deadlines and aging

An administrator or authorized recruiter may choose an optional response deadline while issuing an
offer. A supplied deadline must be strictly later than the current time in the configured hiring
timestamp zone. Existing and new offers without one remain valid and display `No deadline`.

The Outstanding Offers grid shows the response deadline, elapsed offer age, and a text badge for the
derived deadline state:

- `Overdue`: the deadline is at or before the current time.
- `Due soon`: the deadline is after the current time and at or before the due-soon cutoff.
- `On track`: the deadline is after the cutoff.
- `No deadline`: no deadline was supplied.

The default due-soon window is 24 hours. It can be changed with
`OFFER_RESPONSE_DUE_SOON_WINDOW`. Offer, response-deadline, and resolution timestamps remain zone-less
in persistence, so deadline and age calculations interpret them in one configured business zone. When
`OFFER_RESPONSE_TIMESTAMP_ZONE` is absent, empty or new installations may use the implicit `Asia/Manila`
default; an explicitly present blank value is invalid. Before upgrading a deployment with existing
hiring decisions, explicitly set `OFFER_RESPONSE_TIMESTAMP_ZONE` to the zone historically used to interpret
those timestamps. A higher-precedence override of `iss.hiring.offer-deadline.timestamp-zone` must use time-zone
behavior equivalent to that explicit historical zone from the 2026-01-01 supported hiring-record boundary
onward; safe pairs such as `Asia/Manila` and the fixed offset `+08:00` are accepted when their supported-era
behavior is identical. The compatibility guard runs before the web server is initialized. Do not substitute
Manila merely to pass startup validation. Zones with fall-back
overlaps in the supported hiring-record era are rejected because their repeated local times cannot be
reconstructed unambiguously; such historical data requires a separately designed, approved timestamp backfill/storage
migration before startup. True arbitrary-DST-zone support would require persisting an authoritative `Instant`
or offset.

Both page and count queries apply the selected `All`, `Overdue`, `Due soon`, `On track`, or `No
deadline` filter in the database, together with search and the authoritative administrator/recruiter
branch scope. `Response Due` is an explicitly whitelisted grid sort.

These states are informational. Passing a response deadline does **not** automatically expire,
decline, withdraw, or otherwise transition the hiring decision. Overdue offers can still be hired,
declined, or withdrawn under the existing rules. This release does not provide post-issuance deadline
editing or reminder delivery.

## Search behavior

The shared search field is applied independently to every page and count query. It searches applicant
name, branch, position, and client without case sensitivity. Decision worklists also search applicable
decision status names. Search is trimmed, limited to 100 characters, and delayed for 350 milliseconds
while the user types.

Search uses literal substring matching. Characters used as SQL wildcard syntax, including `%` and
`_`, and backslashes have no special meaning. A decision-status match applies only to the matching
status; for example, searching for `hired` does not include declined or withdrawn decisions.

## Refresh and actions

Refresh, offer issuance, and terminal hiring actions invalidate all three providers and their counts.
The current search and column sort choices remain in place. After a successful action the grids return
to the first result window, which prevents a removed last row from leaving the user on an empty page.
The selected outstanding-offer deadline filter also remains in place.
All offer, hire, decline, withdraw, headcount-locking, audit, idempotency, and notification behavior
continues through the existing service operations.
An exact offer retry must use the same evaluation and stored response deadline, including a deadline
that has since passed. Supplying a different deadline is a conflicting reissue rather than an edit; a
different deadline at or before the current time fails deadline validation.

Loading, empty, and failed-query states are shown separately for each worklist. Failure details are
passed through the existing user-safe notification boundary.

## Manual acceptance scenarios

1. Issue an offer with the response deadline blank; verify `No deadline` appears for both the due time
   and state and that all terminal actions remain available.
2. Issue an offer with a future deadline; verify it is persisted and displays as `On track` or `Due
   soon` according to the configured window.
3. Attempt issuance with a deadline at or before the current time; verify the field error is clear and
   no decision, audit, applicant-state change, or notification is produced.
4. Verify exact current-time and due-soon-cutoff boundaries classify as `Overdue` and `Due soon`.
5. Verify an overdue decision remains `OFFERED`, appears under the `Overdue` filter, and can still be
   hired, declined, or withdrawn.
6. As a recruiter, verify deadline pages and counts include only the authoritative branch; as an
   administrator, verify organization-wide visibility.
7. Combine a keyword such as `java` with `Overdue`; verify both predicates apply to pages and counts.
8. Select a deadline filter and `Response Due` sort, perform an action or manual refresh, and verify
   the selections remain while the result window safely reloads.
