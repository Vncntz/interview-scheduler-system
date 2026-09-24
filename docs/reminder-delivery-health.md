# Reminder Delivery Health

The administrator-only **Reminder Delivery Health** screen is available at
`/reminder-delivery-health`. It is a read-only view of persisted
`interview_reminder_deliveries` records. The route and every service read require the `ADMIN` role;
recruiters and applicants cannot use the screen or its backing queries.

The grid is paged in the database and ordered by the persisted appointment snapshot descending, then
delivery ID descending. Filters are also applied in the database:

- persisted delivery status: `PENDING`, `SENT`, `FAILED`, or `SKIPPED`;
- reminder type: 24-hour or 2-hour; and
- scheduled appointment date from/through.

The date range is inclusive of both selected calendar dates in the canonical business timezone. It
filters the immutable `scheduled_start_at` snapshot using `[from 00:00, day after through
00:00)`. Claim, next-attempt, and sent timestamps are stored as UTC values and converted to that same
business timezone for display. `INTERVIEW_REMINDER_BUSINESS_ZONE` inherits the effective canonical
`iss.business-time.zone` and may only override it with equivalent zone behavior. See
[`business-time.md`](business-time.md). Missing
timestamps display as **Unavailable**.

Each row shows the booking reference, reminder type, appointment snapshot, persisted status, attempt
count, available claim/retry/sent times, a controlled human-readable reason, and any health indicators
supported by stored evidence. Persisted status remains distinct from derived health:

The scheduler's current windows are `(now + 2 hours, now + 24 hours]` for a 24-hour reminder and
`(now, now + 2 hours]` for a 2-hour reminder, evaluated in the configured business timezone. The
lower boundary is exclusive and the upper boundary is inclusive.

- **Retry scheduled** applies only to a failed record with a next-attempt timestamp when reminders are
  enabled, attempts remain, the generation is current, current booking/applicant/schedule eligibility
  still matches the scheduler, and the current appointment will be inside the reminder window at the
  later of now or the next-attempt time. This is a current eligibility assessment, not a delivery
  guarantee; recipient and SMTP/template validation occur only when an attempt is claimed.
- **Attempts exhausted** applies to pending or failed records whose attempt count has reached the
  currently configured maximum.
- **Stale pending claim** applies to a pending record with a claim timestamp at least as old as the
  configured stale-claim timeout. A pending row without a claim timestamp is not labeled stale.
- **Expired reminder window** applies to an outstanding pending/failed record whose immutable
  appointment snapshot is at or past its reminder window's lower boundary, or to a record carrying the
  persisted `REMINDER_WINDOW_EXPIRED` reason.
- **Obsolete after rescheduling** applies when the delivery generation differs from the booking's
  current reminder generation.

Indicators are recalculated at read time. Runtime changes to enablement, maximum attempts, stale-claim
timeout, or business timezone can therefore change the displayed health after Refresh; persisted
delivery history is not rewritten. A retry-delay change affects newly scheduled retries only; an
existing persisted `next_attempt_at` is not recomputed.

Status reasons use an allowlisted mapping of the application's controlled reason codes. Unknown or
missing values display **Delivery status detail unavailable**. The screen does not show recipient
addresses, message bodies, claim tokens, provider responses, credentials, or exception details. A
`SENT` record means the SMTP server accepted the message; it does not prove delivery to the recipient's
inbox.

Manual **Refresh** keeps all filters, reloads current runtime metadata, and updates **Last refreshed**
only after a successful database load. Loading, no-results, and user-safe failure states are shown
separately.

This monitoring screen cannot resend, retry, reset, delete, or repair a delivery. It does not add a
durable notification outbox, dead-letter queue, exactly-once delivery, inbox-delivery confirmation, or
provider-side reconciliation. Existing reminder scheduler, claim, retry, and completion behavior is
unchanged.
