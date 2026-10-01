USE fisio_e_sport;

START TRANSACTION;

CREATE TABLE IF NOT EXISTS waitlist_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  therapist_id BIGINT NOT NULL,
  full_name VARCHAR(180) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_waitlist_therapist_created (therapist_id, created_at),

  CONSTRAINT fk_waitlist_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);

COMMIT;

SELECT COUNT(*) AS waitlist_entries_count FROM waitlist_entries;
