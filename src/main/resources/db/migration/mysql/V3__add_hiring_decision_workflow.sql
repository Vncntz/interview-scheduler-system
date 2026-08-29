ALTER TABLE applicants
    MODIFY COLUMN status ENUM (
        'FAILED',
        'FOR_CLIENT_INTERVIEW',
        'FOR_FINAL_INTERVIEW',
        'HIRED',
        'INTERVIEWED',
        'NEW',
        'OFFERED',
        'OFFER_DECLINED',
        'ON_HOLD',
        'PASSED',
        'SCHEDULED',
        'SCREENING',
        'WITHDRAWN'
    ) NOT NULL;

CREATE TABLE hiring_decisions (
    applicant_id BIGINT NOT NULL,
    evaluation_id BIGINT NOT NULL,
    offered_at DATETIME(6) NOT NULL,
    offered_by_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    resolved_at DATETIME(6),
    resolved_by_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    status ENUM ('DECLINED','HIRED','OFFERED','WITHDRAWN') NOT NULL,
    offered_remarks VARCHAR(1000),
    resolution_remarks VARCHAR(1000),
    PRIMARY KEY (id),
    CONSTRAINT uk_hiring_decision_applicant UNIQUE (applicant_id),
    CONSTRAINT uk_hiring_decision_evaluation UNIQUE (evaluation_id),
    CONSTRAINT fk_hiring_decision_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (id),
    CONSTRAINT fk_hiring_decision_evaluation FOREIGN KEY (evaluation_id) REFERENCES interview_evaluations (id),
    CONSTRAINT fk_hiring_decision_offered_by FOREIGN KEY (offered_by_id) REFERENCES users (id),
    CONSTRAINT fk_hiring_decision_position FOREIGN KEY (position_id) REFERENCES position_openings (id),
    CONSTRAINT fk_hiring_decision_resolved_by FOREIGN KEY (resolved_by_id) REFERENCES users (id),
    INDEX ix_hiring_decision_status (status),
    INDEX ix_hiring_decision_position_status (position_id, status)
) ENGINE=InnoDB;

CREATE TABLE hiring_decision_audits (
    actor_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    id BIGINT NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    action ENUM ('ACCEPTED_AND_HIRED','DECLINED','OFFER_ISSUED','WITHDRAWN') NOT NULL,
    previous_status ENUM ('DECLINED','HIRED','OFFERED','WITHDRAWN'),
    new_status ENUM ('DECLINED','HIRED','OFFERED','WITHDRAWN') NOT NULL,
    remarks VARCHAR(1000),
    PRIMARY KEY (id),
    CONSTRAINT fk_hiring_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_hiring_audit_decision FOREIGN KEY (decision_id) REFERENCES hiring_decisions (id),
    INDEX ix_hiring_audit_decision_occurred (decision_id, occurred_at)
) ENGINE=InnoDB;
