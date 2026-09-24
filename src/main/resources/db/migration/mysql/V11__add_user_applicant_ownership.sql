ALTER TABLE users
    ADD COLUMN applicant_id BIGINT NULL,
    ADD CONSTRAINT uk_users_applicant UNIQUE (applicant_id),
    ADD CONSTRAINT fk_users_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id),
    ADD CONSTRAINT chk_users_role_applicant_link CHECK (
        (role = 'APPLICANT') = (applicant_id IS NOT NULL)
    );
