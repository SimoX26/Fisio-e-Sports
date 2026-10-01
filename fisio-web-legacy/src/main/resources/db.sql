/* Drop and recreate database */
DROP DATABASE IF EXISTS fisio_e_sport;

CREATE DATABASE fisio_e_sport
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

/* Optional local DB user bootstrap */
CREATE USER IF NOT EXISTS 'fisio_e_sport'@'localhost'
IDENTIFIED BY 'password_123';

GRANT ALL PRIVILEGES ON fisio_e_sport.* TO 'fisio_e_sport'@'localhost';
FLUSH PRIVILEGES;

USE fisio_e_sport;

/* =========================
   USERS
   ========================= */
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash CHAR(64) NOT NULL,
  role ENUM('THERAPIST', 'ADMIN') NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

/* =========================
   ACCESS REQUESTS
   ========================= */
CREATE TABLE access_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL,
  username VARCHAR(50) NOT NULL,
  password_hash CHAR(64) NOT NULL,
  requested_role ENUM('THERAPIST', 'ADMIN') NOT NULL DEFAULT 'THERAPIST',
  status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  reviewed_by_user_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_access_requests_status_created (status, created_at),
  INDEX idx_access_requests_username (username),
  INDEX idx_access_requests_email (email),

  CONSTRAINT fk_access_requests_reviewer
    FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
);

/* =========================
   REMEMBER ME TOKENS
   ========================= */
CREATE TABLE remember_me_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_remember_me_user_id (user_id),

  CONSTRAINT fk_remember_me_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);

/* =========================
   PATIENTS
   ========================= */
CREATE TABLE patients (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  therapist_id BIGINT NOT NULL,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(150),
  phone VARCHAR(20),
  state ENUM('ACTIVE', 'INACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_patients_therapist_name (therapist_id, last_name, first_name),
  CONSTRAINT fk_patients_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

/* =========================
   WAITLIST ENTRIES
   ========================= */
CREATE TABLE waitlist_entries (
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

/* =========================
   THERAPIST REMINDER TEMPLATES
   ========================= */
CREATE TABLE therapist_reminder_templates (
  therapist_id BIGINT PRIMARY KEY,
  reminder_template TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_therapist_reminder_templates_user
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);

/* =========================
   PATIENT ANAMNESES
   ========================= */
CREATE TABLE patient_anamneses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  therapist_id BIGINT NOT NULL,
  assessment_date DATE NOT NULL,

  chief_complaint VARCHAR(255),
  pain_location VARCHAR(255),
  pain_quality VARCHAR(255),
  associated_symptoms VARCHAR(255),

  onset_type ENUM('ACUTE', 'SUBACUTE', 'CHRONIC'),
  onset_context VARCHAR(255),
  is_disabling BOOLEAN,
  pain_frequency ENUM('LOW', 'MEDIUM', 'HIGH'),
  pain_progression ENUM('CONSTANT', 'WORSE', 'BETTER'),
  pain_with_movement ENUM('WORSE', 'BETTER', 'UNCHANGED'),
  pain_with_rest ENUM('WORSE', 'BETTER', 'UNCHANGED'),
  night_pain BOOLEAN,
  morning_pain BOOLEAN,
  pain_intensity TINYINT UNSIGNED,
  uses_pain_meds BOOLEAN,
  pain_meds_effect ENUM('YES', 'NO', 'PARTIAL'),

  clinical_tests TEXT,
  specialist_visits TEXT,
  previous_treatments TEXT,
  pathology_history TEXT,
  current_regular_drugs TEXT,
  surgery_history TEXT,
  trauma_history TEXT,
  devices_history TEXT,
  chewing_disorders BOOLEAN,
  major_infections_history TEXT,
  family_history TEXT,

  height_cm DECIMAL(5,2),
  weight_kg DECIMAL(5,2),
  lifestyle ENUM('SPORTY', 'SEDENTARY', 'MIXED'),
  sport_practice VARCHAR(120),
  substance_use VARCHAR(255),
  sleep_quality TINYINT UNSIGNED,
  stress_level TINYINT UNSIGNED,
  diet_quality ENUM('HEALTHY', 'IMBALANCED', 'MIXED'),
  female_cycle_notes VARCHAR(255),

  free_notes_json JSON,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_patient_anamneses_patient_date (patient_id, assessment_date),

  CONSTRAINT fk_anamneses_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,

  CONSTRAINT fk_anamneses_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
);

/* =========================
   PATIENT CONDITIONS
   ========================= */
CREATE TABLE patient_conditions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  anamnesis_id BIGINT NOT NULL,
  category ENUM('PATHOLOGY', 'SYMPTOM', 'FAMILY_HISTORY', 'ALLERGY', 'DRUG', 'SYSTEM_REVIEW', 'OTHER') NOT NULL,
  code VARCHAR(80),
  label VARCHAR(120) NOT NULL,
  status ENUM('PRESENT', 'ABSENT', 'UNKNOWN') NOT NULL DEFAULT 'PRESENT',
  notes VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_patient_conditions_anamnesis_category (anamnesis_id, category),

  CONSTRAINT fk_conditions_anamnesis
    FOREIGN KEY (anamnesis_id) REFERENCES patient_anamneses(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE
);

/* =========================
   APPOINTMENTS
   ========================= */
CREATE TABLE appointments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NULL,
  therapist_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  all_day BOOLEAN NOT NULL DEFAULT FALSE,
  title VARCHAR(150),
  notes TEXT,
  state ENUM('SCHEDULED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'SCHEDULED',
  cancelled_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_appointments_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,

  CONSTRAINT fk_appointments_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
);

/* =========================
   TREATMENT PLANS
   ========================= */
CREATE TABLE treatment_plans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NULL,
  therapist_id BIGINT NOT NULL,
  title VARCHAR(150) NOT NULL,
  goals TEXT,
  frequency_per_week TINYINT UNSIGNED,
  start_date DATE NOT NULL,
  expected_end_date DATE,
  total_sessions_planned SMALLINT UNSIGNED NOT NULL,
  state ENUM('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_treatment_plans_therapist_state (therapist_id, state),
  INDEX idx_treatment_plans_patient (patient_id),

  CONSTRAINT fk_treatment_plans_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,

  CONSTRAINT fk_treatment_plans_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
);


/* =========================
   KPI MONTHLY SNAPSHOT
   ========================= */
CREATE TABLE kpi_monthly_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scope_type ENUM('GLOBAL', 'THERAPIST') NOT NULL,
  scope_id BIGINT NOT NULL,
  therapist_id BIGINT NULL,
  year SMALLINT UNSIGNED NOT NULL,
  month TINYINT UNSIGNED NOT NULL,

  appointments_created INT UNSIGNED NOT NULL DEFAULT 0,
  appointments_completed INT UNSIGNED NOT NULL DEFAULT 0,
  appointments_cancelled INT UNSIGNED NOT NULL DEFAULT 0,
  active_patients_month INT UNSIGNED NOT NULL DEFAULT 0,
  new_patients_month INT UNSIGNED NOT NULL DEFAULT 0,
  treatment_plans_started INT UNSIGNED NOT NULL DEFAULT 0,
  treatment_sessions_completed INT UNSIGNED NOT NULL DEFAULT 0,
  total_booked_minutes INT UNSIGNED NOT NULL DEFAULT 0,

  computed_at DATETIME NOT NULL,
  source_version VARCHAR(20) NOT NULL DEFAULT 'v1',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_kpi_monthly_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,

  UNIQUE KEY uk_kpi_monthly_scope_period (scope_type, scope_id, year, month),
  INDEX idx_kpi_monthly_period (year, month),
  INDEX idx_kpi_monthly_scope_period (scope_id, year, month),
  INDEX idx_kpi_monthly_therapist_period (therapist_id, year, month)
);

/* =========================
   TREATMENT SESSIONS
   ========================= */
CREATE TABLE treatment_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  treatment_plan_id BIGINT NOT NULL,
  appointment_id BIGINT NULL,
  patient_id BIGINT NULL,
  therapist_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  pain_score_pre TINYINT UNSIGNED,
  pain_score_post TINYINT UNSIGNED,
  session_outcome VARCHAR(255),
  home_exercises TEXT,
  notes TEXT,
  state ENUM('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PLANNED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_treatment_sessions_appointment
    FOREIGN KEY (appointment_id) REFERENCES appointments(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,

  CONSTRAINT fk_treatment_sessions_plan
    FOREIGN KEY (treatment_plan_id) REFERENCES treatment_plans(id)
    ON DELETE CASCADE
    ON UPDATE CASCADE,

  CONSTRAINT fk_treatment_sessions_patient
    FOREIGN KEY (patient_id) REFERENCES patients(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE,

  CONSTRAINT fk_treatment_sessions_therapist
    FOREIGN KEY (therapist_id) REFERENCES users(id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,

  -- Gli indici aiutano storico e timeline
  INDEX idx_treatment_sessions_plan_start (treatment_plan_id, start_time),
  INDEX idx_treatment_sessions_therapist_start (therapist_id, start_time),
  INDEX idx_treatment_sessions_patient_start (patient_id, start_time)
);
