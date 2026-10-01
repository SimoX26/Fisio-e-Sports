-- Applicare una sola volta, con le scritture applicative sospese.
-- Le schede condivise vengono duplicate per terapista; i riferimenti clinici
-- restano collegati alla copia del rispettivo terapista. Le schede senza
-- riferimenti sono assegnate allo username configurato qui sotto.
SET @fallback_therapist_username = 'marco';

-- Interrompere prima di qualsiasi ALTER se il fallback o i legami sono incoerenti.
CREATE TEMPORARY TABLE patient_ownership_preflight (
  issue_count INT NOT NULL CHECK (issue_count = 0)
);
INSERT INTO patient_ownership_preflight (issue_count)
SELECT
  IF((SELECT COUNT(*) FROM users
      WHERE username = @fallback_therapist_username AND role = 'THERAPIST' AND active = TRUE) = 1, 0, 1)
  + (SELECT COUNT(*) FROM treatment_sessions s
     JOIN treatment_plans p ON p.id = s.treatment_plan_id
     WHERE s.therapist_id <> p.therapist_id
        OR (s.patient_id IS NOT NULL AND p.patient_id IS NOT NULL AND s.patient_id <> p.patient_id))
  + (SELECT COUNT(*) FROM treatment_sessions s
     JOIN appointments a ON a.id = s.appointment_id
     WHERE s.therapist_id <> a.therapist_id
        OR (s.patient_id IS NOT NULL AND a.patient_id IS NOT NULL AND s.patient_id <> a.patient_id));
DROP TEMPORARY TABLE patient_ownership_preflight;

-- Le due colonne migration_* servono a ritrovare gli ID delle copie appena
-- inserite, senza assumere che gli AUTO_INCREMENT siano consecutivi.
ALTER TABLE patients
  ADD COLUMN therapist_id BIGINT NULL,
  ADD COLUMN migration_source_id BIGINT NULL,
  ADD COLUMN migration_owner_id BIGINT NULL,
  ADD INDEX idx_patients_therapist_name (therapist_id, last_name, first_name);

CREATE TEMPORARY TABLE patient_therapist_migration_map (
  old_patient_id BIGINT NOT NULL,
  therapist_id BIGINT NOT NULL,
  new_patient_id BIGINT NULL,
  PRIMARY KEY (old_patient_id, therapist_id)
);

INSERT INTO patient_therapist_migration_map (old_patient_id, therapist_id)
SELECT patient_id, therapist_id FROM appointments WHERE patient_id IS NOT NULL
UNION
SELECT patient_id, therapist_id FROM patient_anamneses
UNION
SELECT patient_id, therapist_id FROM treatment_plans WHERE patient_id IS NOT NULL
UNION
SELECT patient_id, therapist_id FROM treatment_sessions WHERE patient_id IS NOT NULL;

START TRANSACTION;

-- Il terapista con ID minore conserva l'ID originale della scheda.
UPDATE patient_therapist_migration_map m
JOIN (
  SELECT old_patient_id, MIN(therapist_id) AS first_owner_id
  FROM patient_therapist_migration_map
  GROUP BY old_patient_id
) first_owner ON first_owner.old_patient_id = m.old_patient_id
SET m.new_patient_id = m.old_patient_id
WHERE m.therapist_id = first_owner.first_owner_id;

UPDATE patients p
JOIN patient_therapist_migration_map m ON m.old_patient_id = p.id AND m.new_patient_id = p.id
SET p.therapist_id = m.therapist_id;

INSERT INTO patients (
  first_name, last_name, email, phone, state, created_at,
  therapist_id, migration_source_id, migration_owner_id
)
SELECT p.first_name, p.last_name, p.email, p.phone, p.state, p.created_at,
       m.therapist_id, m.old_patient_id, m.therapist_id
FROM patient_therapist_migration_map m
JOIN patients p ON p.id = m.old_patient_id
WHERE m.new_patient_id IS NULL;

UPDATE patient_therapist_migration_map m
JOIN patients p ON p.migration_source_id = m.old_patient_id
               AND p.migration_owner_id = m.therapist_id
SET m.new_patient_id = p.id
WHERE m.new_patient_id IS NULL;

UPDATE appointments a
JOIN patient_therapist_migration_map m ON m.old_patient_id = a.patient_id AND m.therapist_id = a.therapist_id
SET a.patient_id = m.new_patient_id;
UPDATE patient_anamneses a
JOIN patient_therapist_migration_map m ON m.old_patient_id = a.patient_id AND m.therapist_id = a.therapist_id
SET a.patient_id = m.new_patient_id;
UPDATE treatment_plans p
JOIN patient_therapist_migration_map m ON m.old_patient_id = p.patient_id AND m.therapist_id = p.therapist_id
SET p.patient_id = m.new_patient_id;
UPDATE treatment_sessions s
JOIN patient_therapist_migration_map m ON m.old_patient_id = s.patient_id AND m.therapist_id = s.therapist_id
SET s.patient_id = m.new_patient_id;

UPDATE patients
SET therapist_id = (SELECT id FROM users WHERE username = @fallback_therapist_username)
WHERE therapist_id IS NULL;

COMMIT;
DROP TEMPORARY TABLE patient_therapist_migration_map;

ALTER TABLE patients
  MODIFY therapist_id BIGINT NOT NULL,
  DROP COLUMN migration_source_id,
  DROP COLUMN migration_owner_id,
  ADD CONSTRAINT fk_patients_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE;
