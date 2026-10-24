-- D-09 (2026-10-24): the description of an entry has 3 to 255 characters after trimming (500 before).
-- NOT VALID keeps older, longer texts readable (backwards compatible migration); every new or changed entry is
-- checked. The service enforces the same rule (5.1).
ALTER TABLE time_entry DROP CONSTRAINT time_entry_description_ck;
ALTER TABLE time_entry ADD CONSTRAINT time_entry_description_ck
    CHECK (char_length(btrim(description)) BETWEEN 3 AND 255) NOT VALID;
