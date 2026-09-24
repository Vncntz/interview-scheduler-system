# Business Time

The application uses one canonical business time zone for interview appointments and newly recorded
zone-less recruitment events. Configure it with `BUSINESS_TIME_ZONE`; the Spring property is
`iss.business-time.zone`. `Asia/Manila` is the safe default only for a new installation with no schedules.

## Timestamp contract

- Schedule date/start/end values are business-local appointment times in the canonical zone.
- New booking, booking lifecycle, reschedule, and evaluation timestamps are captured from one injected
  clock instant, converted to the canonical zone, and truncated to database microsecond precision.
- Dashboard, recruiter workbench, booking availability, rescheduling, scheduling UI minimum dates, and
  follow-up calculations derive related date and time values from one operation snapshot.
- Reminder appointment windows use the canonical zone. Their existing lower-exclusive/upper-inclusive
  boundaries are unchanged.
- Reminder claim, retry, lease, and send timestamps and account-security timestamps remain UTC technical
  instants. `BaseEntity` audit timestamps are unchanged.
- Hiring offer timing remains independent under `OFFER_RESPONSE_TIMESTAMP_ZONE` and retains its own
  historical compatibility guard.

Appointments must start strictly after the current business time to be booked or selected as a
reschedule destination. Follow-up elapsed durations are calculated on the instant timeline after a strict
conversion of their stored local timestamp. The configured canonical zone must therefore have no
daylight-saving gaps or overlaps from `2026-01-01T00:00:00Z` onward. The application rejects unsupported
zones rather than choosing an offset for an ambiguous or nonexistent local time.

## Deployment setup

For a new empty installation, leaving `BUSINESS_TIME_ZONE` absent selects `Asia/Manila`. Set it explicitly
when another transition-free zone is required.

For an installation containing schedules, determine the zone historically used to interpret the existing
zone-less appointment and recruitment timestamps and set either `BUSINESS_TIME_ZONE` or the direct Spring
property `iss.business-time.zone` explicitly before startup. The effective Spring property follows normal
Spring precedence. The application refuses to start when neither form was explicitly supplied. It does not
reinterpret or backfill existing timestamps. Changing the setting is not a data migration; a historical-zone
change requires an approved timestamp backfill/storage migration before startup.

The legacy compatibility variables remain accepted:

- `INTERVIEW_FOLLOW_UP_TIMESTAMP_ZONE`
- `INTERVIEW_REMINDER_BUSINESS_ZONE`

When absent, both inherit the effective `iss.business-time.zone`, including a higher-precedence direct
Spring override; that canonical property in turn uses `BUSINESS_TIME_ZONE` and then the `Asia/Manila`
new-installation default. If set, their effective zone rules must be equivalent to the canonical business
zone from the supported record boundary onward. Equivalent aliases such as `Asia/Manila` and `+08:00` are
accepted. Conflicting effective Spring overrides fail startup. Explicitly present blank variables are invalid.

`OFFER_RESPONSE_TIMESTAMP_ZONE` does not inherit the canonical setting. Keep following the hiring
deployment procedure for installations with existing hiring decisions.

No Flyway migration accompanies this contract because the existing local date/time schema is preserved.
