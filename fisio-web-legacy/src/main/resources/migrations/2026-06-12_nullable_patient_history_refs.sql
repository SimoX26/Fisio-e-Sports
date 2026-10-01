USE fisio_e_sport;

ALTER TABLE appointments
  DROP FOREIGN KEY fk_appointments_patient;

ALTER TABLE appointments
  MODIFY patient_id BIGINT NULL;

ALTER TABLE appointments
  ADD CONSTRAINT fk_appointments_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE treatment_sessions
  DROP FOREIGN KEY fk_treatment_sessions_patient;

ALTER TABLE treatment_sessions
  MODIFY patient_id BIGINT NULL;

ALTER TABLE treatment_sessions
  ADD CONSTRAINT fk_treatment_sessions_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;

ALTER TABLE treatment_plans
  DROP FOREIGN KEY fk_treatment_plans_patient;

ALTER TABLE treatment_plans
  MODIFY patient_id BIGINT NULL;

ALTER TABLE treatment_plans
  ADD CONSTRAINT fk_treatment_plans_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE;
