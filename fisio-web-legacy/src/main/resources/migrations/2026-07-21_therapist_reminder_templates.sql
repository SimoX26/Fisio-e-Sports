USE fisio_e_sport;

START TRANSACTION;

CREATE TABLE IF NOT EXISTS therapist_reminder_templates (
  therapist_id BIGINT PRIMARY KEY,
  reminder_template TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_therapist_reminder_templates_user
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);

COMMIT;
