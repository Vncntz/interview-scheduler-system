# ADR 0001: Interview stage ownership

- Status: Accepted
- Date: 2026-08-30

## Context

Applicant status previously carried progression intent such as `FOR_FINAL_INTERVIEW` or
`FOR_CLIENT_INTERVIEW`, but booking creation replaced that status with `SCHEDULED`. The appointment
then had no persistent record of which interview actually occurred. Schedules currently model
generic recruiter availability, branch, mode, time, and capacity; the same slot can serve different
interview types.

## Decision

Store `InterviewStage` (`INITIAL`, `FINAL`, or `CLIENT`) as a required, immutable snapshot on each
`Booking`. Booking creation infers the one eligible stage from the locked applicant state and
validates the explicit command value. Rescheduling changes only the schedule/recruiter assignment and
preserves the stage.

Do not add stage to `Schedule`. A schedule remains a generic capacity window because the current
scheduling UI and overlap/capacity model do not create dedicated stage-specific slots. If the product
later introduces dedicated panels or client-only capacity, that would require a separate migration,
schedule workflow, and compatibility rules.

Keep applicant lifecycle status separate from interview stage. Applicant status answers where the
candidate is in recruitment (`SCHEDULED`, `PASSED`, `ON_HOLD`, and so on); booking stage answers what
kind of interview the appointment represents and provides historical evidence after applicant status
changes.

## Consequences

- Evaluation result validity can be enforced against the persisted booking stage.
- Final/client intent survives scheduling and rescheduling.
- Existing bookings are conservatively backfilled to `INITIAL`; mutable current status cannot safely
  reconstruct historical stage.
- `ON_HOLD` remains non-bookable until a separate, explicit resume decision is designed.
- Schedules can still hold bookings from different stages, while branch authorization, locking,
  capacity, and overlap rules remain unchanged.
