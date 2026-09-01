ALTER TABLE bookings
    ADD COLUMN reminder_generation INTEGER NULL AFTER interview_stage;

UPDATE bookings
SET reminder_generation = 0
WHERE reminder_generation IS NULL;

ALTER TABLE bookings
    MODIFY COLUMN reminder_generation INTEGER NOT NULL;

ALTER TABLE notification_templates
    MODIFY COLUMN event ENUM (
        'BOOKING_CANCELLED',
        'BOOKING_CONFIRMED',
        'BOOKING_CREATED',
        'BOOKING_RESCHEDULED',
        'HIRED',
        'INTERVIEW_RESULT',
        'INTERVIEW_REMINDER_24H',
        'INTERVIEW_REMINDER_2H',
        'JOB_OFFERED',
        'PASSWORD_RESET'
    ) NOT NULL;

CREATE TABLE interview_reminder_deliveries (
    attempt_count INTEGER NOT NULL,
    reminder_generation INTEGER NOT NULL,
    booking_id BIGINT NOT NULL,
    claimed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    next_attempt_at DATETIME(6),
    scheduled_start_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    claim_token VARCHAR(36),
    reminder_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_reason VARCHAR(80),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_reminder_booking_generation_type
        UNIQUE (booking_id, reminder_generation, reminder_type),
    CONSTRAINT fk_interview_reminder_deliveries_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE INDEX idx_interview_reminder_retry_claim
    ON interview_reminder_deliveries (status, next_attempt_at, claimed_at, attempt_count, id);

CREATE INDEX idx_schedules_reminder_scan
    ON schedules (schedule_date, start_time, id);
