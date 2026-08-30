ALTER TABLE bookings
    ADD COLUMN interview_stage ENUM ('INITIAL','FINAL','CLIENT') NULL AFTER status;

UPDATE bookings
SET interview_stage = 'INITIAL'
WHERE interview_stage IS NULL;

ALTER TABLE bookings
    MODIFY COLUMN interview_stage ENUM ('INITIAL','FINAL','CLIENT') NOT NULL;
