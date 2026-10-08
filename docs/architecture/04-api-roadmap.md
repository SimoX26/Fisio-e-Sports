# API per la migrazione dei client

Serve coprire tutte le funzioni che i nuovi client useranno, non riprodurre
necessariamente ogni URL della webapp. Ogni operazione deve usare i servizi
condivisi, ricavare il terapista dall'autenticazione e conservare permessi e
comportamento descritti nell'[inventario](02-inventario-funzionale.md).

| Area | Stato | Prossimo criterio di parità |
|---|---|---|
| Accesso terapista | `/api/me` con Basic o Bearer; token di 30 giorni emesso e revocato da `/api/auth/remember` per il desktop locale | HTTPS, indirizzo server configurabile e autenticazione remota |
| Calendario | Lettura, dettaglio, creazione, modifica, completamento, cestino, anteprima e invio promemoria con modello modificabile | Avvio e arresto del gateway |
| Home e lista d'attesa | Lista d'attesa GET/POST/DELETE disponibile | Riepilogo completo home, conversione in appuntamento e promemoria |
| Pazienti e anamnesi | Rubrica e scheda legacy con creazione, modifica, ultima anamnesi, condizioni, unione ed eliminazione | Versioni storiche dell'anamnesi |
| Trattamenti | Storico generale e per paziente; completamento appuntamento con piano e seduta | Gestione autonoma dei piani e delle sedute |
| Statistiche | Da fare | KPI personali/globali con le formule attuali |
| Ricerca | Da fare | Risultati e navigazione al paziente o appuntamento |
| Promemoria e impostazioni | Anteprima, salvataggio modello e invio; stato e QR WhatsApp | Avvio e arresto del gateway |
| Amministrazione | Da fare | Richieste di accesso e revisione per ADMIN |

Il primo collaudo della lista d'attesa richiede due terapisti: ciascuno vede e
modifica solo i propri contatti. La trasformazione in appuntamento richiederà
l'API di creazione calendario e verrà completata insieme a quel flusso.

`GET /api/patients` accetta `q`, `sort` (`created-desc`, `created-asc`,
`name-asc`, `name-desc`), `treatedDate` (`YYYY-MM-DD`) o `treatedMonth`
(`YYYY-MM`). Usa gli stessi casi d'uso della rubrica legacy e ricava il
terapista dalle credenziali. Il desktop espone per ora elenco, ricerca e
ordinamento; i filtri sono pronti per i collegamenti dalla home.
`GET /api/patients/{id}` restituisce i dati anagrafici della singola scheda
solo al terapista proprietario; un ID non appartenente all'account restituisce 404.
`GET /api/patients/{id}/anamnesis` restituisce l'ultima anamnesi e le condizioni
raggruppate per categoria, oppure `{}` se la scheda non ne ha. Applica lo
stesso controllo di proprietà; il desktop mostra prima un riepilogo dei soli
campi compilati e apre il modulo completo con **Modifica**.
`POST /api/patients` crea una scheda con `fullName`, `email` e `phone` come la
webapp legacy. Il proprietario è sempre il terapista autenticato; il client
non invia né sceglie il suo ID.
`PUT /api/patients/{id}` aggiorna anagrafica e, quando ci sono dati clinici,
salva una nuova anamnesi. Con `mergeTargetId` unisce invece la scheda al
contatto scelto, dopo conferma. `GET /api/patients/{id}/merge-candidates`
restituisce i candidati dello stesso terapista. `DELETE /api/patients/{id}`
segue la conferma legacy; se sono presenti appuntamenti, richiede `force=1`.

Il backend attuale ascolta solo su loopback. Basic verifica il login e rilascia
un token casuale tramite `POST /api/auth/remember`; il backend conserva solo
l'hash, controlla ruolo e scadenza su ogni richiesta Bearer, e lo revoca con
`DELETE /api/auth/remember`. Il desktop salva il token nell'archivio protetto
del sistema operativo e lo elimina al Logout. Prima di usare il desktop da
altri PC servono HTTPS e la configurazione dell'indirizzo server.

`GET /api/treatments` restituisce lo storico del terapista autenticato; con
`patientId` restituisce tutte le sedute del paziente e risponde 404 se la
scheda appartiene a un altro terapista. `POST /api/treatments/appointments/{id}`
completa un appuntamento pianificato e crea piano e seduta con gli stessi campi
del modulo legacy. Il completamento è consentito solo dopo l'orario di fine
dell'appuntamento, anche nel client legacy. Rifiuta eventi generici, tutto il giorno e appuntamenti
non accessibili. Appuntamento, piano e seduta vengono salvati dai servizi
legacy con connessioni distinte: in caso di errore del database durante il
salvataggio, controllare lo stato prima di ripetere l'operazione.

`GET /api/calendar/trash` elenca gli appuntamenti cancellati del terapista e
applica la scadenza legacy di 30 giorni. `PUT /api/calendar/trash/{id}` ripristina
se la fascia è libera (409 in caso di conflitto); `DELETE` sul singolo ID elimina
definitivamente, mentre `DELETE /api/calendar/trash` svuota il cestino. Gli ID
non appartenenti al terapista restituiscono 404.

`GET /api/reminders/preview?date=YYYY-MM-DD` legge il modello salvato del
terapista (o quello predefinito) e restituisce destinatari e messaggi già
composti per gli appuntamenti programmati con paziente. Il filtro è condiviso
con la webapp legacy. Indica anche se WhatsApp è configurato per il terapista.
`POST /api/reminders/template` salva il modello del terapista autenticato;
un testo vuoto ripristina quello predefinito. `POST /api/reminders/send` riceve
`date`, uno o più `appointmentId` e, opzionalmente, `template` come form:
valida tutti gli ID prima di inviare, salva e usa il modello fornito (oppure
usa quello già salvato) e restituisce i
conteggi `processedCount`, `sentCount`, `skippedCount` e `failedCount`. L'identità
del terapista deriva dall'autenticazione; se WhatsApp è disabilitato risponde
428. Un esito HTTP incerto dopo l'invio richiede un controllo manuale prima
di ripetere la richiesta, per evitare duplicati.

`GET /api/whatsapp/status` legge lo stato del gateway configurato nel backend e
restituisce `configured`, `reachable`, `ready`, `qrRequired`, `state`, `lastError`
e, se disponibile, `qrDataUrl`. Richiede il terapista autenticato e applica
`whatsapp.baileys.therapistId`. Il desktop aggiorna lo stato mentre la pagina
Impostazioni è aperta. Il QR collega la sessione WhatsApp centrale sul server.
