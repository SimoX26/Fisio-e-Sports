# Stato della migrazione

Aggiornato: 14 ottobre 2026. Questo registro descrive il codice del repository;
non attesta un deploy in produzione.

| Passo | Stato | Verifica o lavoro rimasto |
|---|---|---|
| Analisi e inventario | Completato | Documenti `01` e `02` in `architecture/` |
| Backend avviabile | Completato; script di deploy remoto del solo JAR preparato | Collaudare `fisio-backend.service` e `/ready` sul server con configurazione DB remota |
| Separazione Maven | Implementata, da collaudare in Tomcat | Build aggregata e contenuto WAR verificati |
| Associazione paziente-terapista | Migrazione applicata al DB di test configurato; codice pronto | Deploy WAR e prova manuale con due utenti |
| API backend | Identità e funzioni autenticate del terapista; richiesta pubblica di accesso collegata al caso d'uso legacy; calendario, cestino, promemoria, WhatsApp, trattamenti, lista d'attesa, pazienti e KPI | Collaudare richiesta di accesso e gateway systemd sul DB di test; HTTPS prima del collegamento da altri PC |
| Client desktop | Si apre sul login anche senza backend; richiesta di accesso collegata al backend; accesso automatico con archivio protetto separato per URL backend; home con lista d'attesa trasformabile in appuntamento, tre contatori, azioni rapide, filtro Pazienti oggi e stati agenda; ricerca nella rubrica, calendario, promemoria, Impostazioni WhatsApp, storico trattamenti, scheda paziente e Dati e Statistiche con filtri, grafico e tabella. URL backend configurabile tramite `FISIO_BACKEND_URL`; barra di ricerca globale rimossa su richiesta | Verificare registrazione e statistiche con dati reali, home, rubrica e isolamento fra terapisti; proxy HTTPS remoto e relativo collaudo restano da fare |
| Amministrazione desktop | Accesso ADMIN, home con numero di richieste pendenti, lista e storico recente, approvazione/rifiuto tramite backend; token revocabile al Logout | Collaudo manuale con richiesta di prova e account ADMIN; confronto con la legacy |
| Client Android autonomo | Login terapista con sessione cifrata e ripristino all'avvio, home con agenda odierna e tre conteggi, calendario mensile con dettagli, creazione e modifica degli appuntamenti programmati di un giorno, annullamento, ripristino, eliminazione definitiva e svuotamento del cestino; rubrica con ricerca arrotondata e scheda anagrafica modificabile (nome, email, telefono); menu sovrapposto, finestre e selettori data/ora in stile Material coordinato, contenuto adattato alle barre di sistema, aggiornamento trascinando verso il basso e WebView legacy rimossa | Collaudare sul telefono calendario, cestino, modifica paziente e tema delle finestre con dati di test; migrare altre azioni calendario, anamnesi e restanti funzioni |
| Dismissione webapp | Da fare | Solo dopo parità funzionale e collaudo |

La regola scelta per i pazienti è: ciascuna scheda appartiene a un terapista;
solo quel terapista può leggerla e modificarla. Eventuali schede condivise
vanno duplicate per terapista conservando i relativi riferimenti clinici.

Il 12 ottobre 2026 il database locale di test configurato conteneva di nuovo
uno schema precedente, senza `patients.therapist_id`: la query del calendario
falliva. Dopo i controlli preliminari è stata riapplicata la migrazione
versionata senza procedure. Verifica finale: 141 schede con proprietario,
nessuna senza; 12 schede prive di riferimenti assegnate a `marco`; nessuna
discrepanza tra proprietario e terapista di appuntamenti, anamnesi, piani o
sedute. La query SQL del calendario restituisce i dati. Resta da verificare
l'API dall'applicazione e da pubblicare il WAR aggiornato in Tomcat.
