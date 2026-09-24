-- Fail before changing the table when a legacy APPLICANT account cannot be
-- linked authoritatively. Ownership must never be inferred from email.
ALTER TABLE users
    ADD CONSTRAINT chk_users_v11_no_legacy_applicant CHECK (
        CAST(role AS VARCHAR) <> 'APPLICANT'
    );

ALTER TABLE users
    DROP CONSTRAINT chk_users_v11_no_legacy_applicant;

ALTER TABLE users
    ADD COLUMN applicant_id BIGINT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_applicant UNIQUE (applicant_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (id);

ALTER TABLE users
    ADD CONSTRAINT chk_users_role_applicant_link CHECK (
        (CAST(role AS VARCHAR) = 'APPLICANT') = (applicant_id IS NOT NULL)
    );
