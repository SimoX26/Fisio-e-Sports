-- Migrazione non distruttiva: passaggio invio WhatsApp al Servizio WhatsApp locale.
-- Non sono richieste modifiche schema per il Servizio WhatsApp:
-- - la configurazione del gateway vive in src/main/resources/config.properties;
-- - la sessione WhatsApp vive in baileys-service/auth-session/;
-- - i dati applicativi esistenti restano invariati.
--
-- Nota: se in un database esistente e ancora presente la vecchia tabella
-- whatsapp_business_configs, questa migrazione la lascia intatta per evitare
-- qualunque perdita di dati storici/configurativi.

USE fisio_e_sport;

START TRANSACTION;

SELECT 'Servizio WhatsApp: nessuna modifica schema necessaria, dati esistenti non toccati.' AS migration_note;

COMMIT;
