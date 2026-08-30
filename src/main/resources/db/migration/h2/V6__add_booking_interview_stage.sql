ALTER TABLE bookings
    ADD COLUMN interview_stage ENUM ('INITIAL','FINAL','CLIENT') NULL;

UPDATE bookings
SET interview_stage = 'INITIAL'
WHERE interview_stage IS NULL;

ALTER TABLE bookings
    ALTER COLUMN interview_stage SET NOT NULL;
