CREATE TABLE booking_lifecycle_history (
    action ENUM ('ATTENDANCE_RECORDED','BOOKING_CANCELLED','BOOKING_CONFIRMED','BOOKING_CREATED','NO_SHOW_RECORDED') NOT NULL,
    appointment_date DATE NOT NULL,
    end_time TIME(0) NOT NULL,
    actor_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    schedule_id BIGINT NOT NULL,
    start_time TIME(0) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    booking_reference VARCHAR(50) NOT NULL,
    branch_display_name VARCHAR(255),
    recruiter_display_name VARCHAR(255),
    interview_mode ENUM ('ONLINE','ONSITE','PHONE') NOT NULL,
    interview_stage ENUM ('CLIENT','FINAL','INITIAL') NOT NULL,
    new_status ENUM ('ATTENDED','BOOKED','CANCELLED','CONFIRMED','FAILED','FOR_CLIENT_INTERVIEW','FOR_FINAL_INTERVIEW','NO_SHOW','ON_HOLD','PASSED','RESCHEDULED') NOT NULL,
    previous_status ENUM ('ATTENDED','BOOKED','CANCELLED','CONFIRMED','FAILED','FOR_CLIENT_INTERVIEW','FOR_FINAL_INTERVIEW','NO_SHOW','ON_HOLD','PASSED','RESCHEDULED'),
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_lifecycle_action UNIQUE (booking_id, action),
    CONSTRAINT chk_booking_lifecycle_transition CHECK (
        CASE CAST(action AS CHAR)
            WHEN 'BOOKING_CREATED' THEN
                previous_status IS NULL AND CAST(new_status AS CHAR) = 'BOOKED'
            WHEN 'BOOKING_CONFIRMED' THEN
                previous_status IS NOT NULL
                    AND CAST(previous_status AS CHAR) = 'BOOKED'
                    AND CAST(new_status AS CHAR) = 'CONFIRMED'
            WHEN 'ATTENDANCE_RECORDED' THEN
                previous_status IS NOT NULL
                    AND CAST(previous_status AS CHAR) = 'CONFIRMED'
                    AND CAST(new_status AS CHAR) = 'ATTENDED'
            WHEN 'NO_SHOW_RECORDED' THEN
                previous_status IS NOT NULL
                    AND CAST(previous_status AS CHAR) = 'CONFIRMED'
                    AND CAST(new_status AS CHAR) = 'NO_SHOW'
            WHEN 'BOOKING_CANCELLED' THEN
                previous_status IS NOT NULL
                    AND (CAST(previous_status AS CHAR) = 'BOOKED'
                    OR CAST(previous_status AS CHAR) = 'CONFIRMED')
                    AND CAST(new_status AS CHAR) = 'CANCELLED'
            ELSE FALSE
        END
    ),
    CONSTRAINT fk_booking_lifecycle_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_booking_lifecycle_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_lifecycle_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    INDEX idx_booking_lifecycle_timeline (booking_id, occurred_at, id)
) ENGINE=InnoDB;

ALTER TABLE booking_reschedule_history
    ADD COLUMN snapshot_version SMALLINT NULL,
    ADD COLUMN source_appointment_date DATE NULL,
    ADD COLUMN source_start_time TIME(0) NULL,
    ADD COLUMN source_end_time TIME(0) NULL,
    ADD COLUMN source_interview_mode ENUM ('ONLINE','ONSITE','PHONE') NULL,
    ADD COLUMN source_recruiter_display_name VARCHAR(255) NULL,
    ADD COLUMN source_branch_display_name VARCHAR(255) NULL,
    ADD COLUMN destination_appointment_date DATE NULL,
    ADD COLUMN destination_start_time TIME(0) NULL,
    ADD COLUMN destination_end_time TIME(0) NULL,
    ADD COLUMN destination_interview_mode ENUM ('ONLINE','ONSITE','PHONE') NULL,
    ADD COLUMN destination_recruiter_display_name VARCHAR(255) NULL,
    ADD COLUMN destination_branch_display_name VARCHAR(255) NULL;
