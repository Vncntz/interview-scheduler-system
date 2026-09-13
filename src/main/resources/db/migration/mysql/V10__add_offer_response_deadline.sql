ALTER TABLE hiring_decisions
    ADD COLUMN response_due_at DATETIME(6) NULL;

CREATE INDEX ix_hiring_decision_status_response_due_id
    ON hiring_decisions (status, response_due_at, id);
