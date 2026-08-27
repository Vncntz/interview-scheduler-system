CREATE TABLE branches (
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    branch_code VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    branch_name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_branches_branch_code UNIQUE (branch_code)
) ENGINE=InnoDB;

CREATE TABLE clients (
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    contact_number VARCHAR(30),
    company_name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(150),
    email VARCHAR(150),
    address VARCHAR(250) NOT NULL,
    notes VARCHAR(1000),
    PRIMARY KEY (id),
    CONSTRAINT uk_clients_company_name UNIQUE (company_name)
) ENGINE=InnoDB;

CREATE TABLE notification_settings (
    active BIT NOT NULL,
    email_enabled BIT NOT NULL,
    sms_enabled BIT NOT NULL,
    smtp_port INTEGER,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    smtp_password VARCHAR(1000),
    sms_api_key VARCHAR(2000),
    company_name VARCHAR(255) NOT NULL,
    sms_provider VARCHAR(255),
    sms_sender_name VARCHAR(255),
    smtp_from_name VARCHAR(255),
    smtp_host VARCHAR(255),
    smtp_username VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE notification_templates (
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    subject VARCHAR(500),
    body VARCHAR(5000) NOT NULL,
    channel ENUM ('EMAIL','SMS') NOT NULL,
    event ENUM ('BOOKING_CANCELLED','BOOKING_CONFIRMED','BOOKING_CREATED','BOOKING_RESCHEDULED','HIRED','INTERVIEW_RESULT','JOB_OFFERED') NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE users (
    active BIT NOT NULL,
    failed_login_attempts INTEGER NOT NULL,
    must_change_password BIT NOT NULL,
    branch_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_login_at DATETIME(6),
    lockout_until DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM ('ADMIN','APPLICANT','RECRUITER') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE=InnoDB;

CREATE TABLE position_openings (
    active BIT NOT NULL,
    applied_count INTEGER NOT NULL,
    hired_count INTEGER NOT NULL,
    interviewed_count INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    required_headcount INTEGER NOT NULL,
    client_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    title VARCHAR(150) NOT NULL,
    work_location VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    employment_type ENUM ('CONTRACTUAL','FULL_TIME','PART_TIME','PROJECT_BASED','SEASONAL') NOT NULL,
    status ENUM ('CANCELLED','CLOSED','FILLED','ON_HOLD','OPEN') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_position_openings_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE=InnoDB;

CREATE TABLE applicants (
    active BIT NOT NULL,
    branch_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    position_opening_id BIGINT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    mobile_number VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    source VARCHAR(100),
    email VARCHAR(150) NOT NULL,
    remarks VARCHAR(1000),
    status ENUM ('FAILED','FOR_CLIENT_INTERVIEW','FOR_FINAL_INTERVIEW','HIRED','INTERVIEWED','NEW','ON_HOLD','PASSED','SCHEDULED','SCREENING','WITHDRAWN') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_applicants_email UNIQUE (email),
    CONSTRAINT fk_applicants_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_applicants_position_opening FOREIGN KEY (position_opening_id) REFERENCES position_openings (id)
) ENGINE=InnoDB;

CREATE TABLE schedules (
    active BIT NOT NULL,
    booked_count INTEGER NOT NULL,
    end_time TIME(0) NOT NULL,
    schedule_date DATE NOT NULL,
    slot_capacity INTEGER NOT NULL,
    start_time TIME(0) NOT NULL,
    branch_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruiter_id BIGINT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    notes VARCHAR(500),
    interview_mode ENUM ('ONLINE','ONSITE','PHONE') NOT NULL,
    status ENUM ('CANCELLED','CLOSED','FULL','OPEN') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedules_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_schedules_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE bookings (
    applicant_id BIGINT,
    booked_date_time DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruiter_id BIGINT,
    schedule_id BIGINT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    booking_reference VARCHAR(50) NOT NULL,
    remarks VARCHAR(1000),
    status ENUM ('ATTENDED','BOOKED','CANCELLED','CONFIRMED','FAILED','FOR_CLIENT_INTERVIEW','FOR_FINAL_INTERVIEW','NO_SHOW','ON_HOLD','PASSED','RESCHEDULED') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT fk_bookings_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (id),
    CONSTRAINT fk_bookings_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
) ENGINE=InnoDB;

CREATE TABLE booking_reschedule_history (
    actor_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    destination_schedule_id BIGINT NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    rescheduled_at DATETIME(6) NOT NULL,
    source_schedule_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    reason VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reschedule_history_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_reschedule_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_reschedule_history_destination FOREIGN KEY (destination_schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_reschedule_history_source FOREIGN KEY (source_schedule_id) REFERENCES schedules (id)
) ENGINE=InnoDB;

CREATE TABLE interview_evaluations (
    attitude_score INTEGER NOT NULL,
    communication_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    applicant_id BIGINT,
    booking_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    evaluation_date DATETIME(6) NOT NULL,
    evaluator_id BIGINT,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    remarks VARCHAR(1000),
    result ENUM ('FAIL','FOR_CLIENT_INTERVIEW','FOR_FINAL_INTERVIEW','ON_HOLD','PASS') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_evaluation_booking UNIQUE (booking_id),
    CONSTRAINT fk_interview_evaluations_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (id),
    CONSTRAINT fk_interview_evaluations_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_interview_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES users (id)
) ENGINE=InnoDB;
