/* Seed utenti test per login applicativo
   Database target: fisio_e_sport
   Tabella target: users

   Password in chiaro:
   Marco  -> 1234
   Andrea -> 1234
   Simone -> 1234 */

USE fisio_e_sport;

INSERT INTO users (username, password_hash, role, active)
VALUES
    ('Marco', SHA2('1234', 256), 'THERAPIST', TRUE),
    ('Andrea', SHA2('1234', 256), 'THERAPIST', TRUE),
    ('Simone', SHA2('1234', 256), 'ADMIN', TRUE)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    active = VALUES(active);

/* Verifica rapida post-seed (password_ok deve essere 1 per tutti) */
SELECT username, role, active, (password_hash = SHA2('1234', 256)) AS password_ok
FROM users
WHERE username IN ('Marco', 'Andrea', 'Simone')
ORDER BY username;
