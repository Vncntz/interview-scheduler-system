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
- **Outstanding offers** includes `OFFERED` decisions, ordered by offer time descending and decision
  ID descending by default.
- **Completed decisions** includes `HIRED`, `DECLINED`, and `WITHDRAWN` decisions, ordered by
  resolution time descending and decision ID descending by default.

Each grid requests at most 50 rows from its Vaadin callback provider. The service rejects offsets
outside the JPA-supported integer range and page sizes outside 1 through 100. User-selected sorting is
restricted to the columns exposed by that grid and always adds a unique ID tie-breaker.

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
All offer, hire, decline, withdraw, headcount-locking, audit, idempotency, and notification behavior
continues through the existing service operations.

Loading, empty, and failed-query states are shown separately for each worklist. Failure details are
passed through the existing user-safe notification boundary.
