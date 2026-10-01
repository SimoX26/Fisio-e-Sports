USE fisio_e_sport;

START TRANSACTION;

ALTER TABLE waitlist_entries
  ADD COLUMN full_name VARCHAR(180) NULL AFTER therapist_id;

UPDATE waitlist_entries
SET full_name = TRIM(CONCAT(COALESCE(first_name, ''), ' ', COALESCE(last_name, '')))
WHERE full_name IS NULL;

ALTER TABLE waitlist_entries
  MODIFY full_name VARCHAR(180) NOT NULL;

ALTER TABLE waitlist_entries
  DROP COLUMN first_name,
  DROP COLUMN last_name;

COMMIT;

SELECT COUNT(*) AS waitlist_entries_count FROM waitlist_entries;
