-- Migrazione step 1: tabella snapshot KPI mensili (performance-first)
-- Eseguire con: source /percorso/2026-05-13_kpi_monthly_snapshot.sql

USE fisio_e_sport;

START TRANSACTION;

CREATE TABLE IF NOT EXISTS kpi_monthly_snapshot (
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

COMMIT;

SELECT COUNT(*) AS kpi_snapshot_rows FROM kpi_monthly_snapshot;
