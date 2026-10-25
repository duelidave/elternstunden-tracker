-- Architecture review (Lastenheft 0.4.10).
-- D-09: no entry is longer than 255 characters any more, so the constraint from V6 can be validated. Afterwards
-- PostgreSQL relies on it for every row, and no old row can block an update.
ALTER TABLE time_entry VALIDATE CONSTRAINT time_entry_description_ck;

-- Admin overview (5.6) and the check of new cut-off dates filter entries by school year only
CREATE INDEX time_entry_year_idx ON time_entry (school_year_id) WHERE deleted_at IS NULL;

-- Dashboard widgets (7.6) look up audit entries by action and time
CREATE INDEX audit_log_action_idx ON audit_log (action, occurred_at DESC);
