# Backend Java

Il processo HTTP autonomo espone `/health`, `/ready`, `/api/me`,
`/api/auth/remember`, `/api/calendar`, `/api/waitlist`, `/api/patients` e `/api/kpi`. Le API sono limitate ai terapisti
autenticati; calendario, rubrica e lista d'attesa mostrano solo i loro dati. Il login può aggiornare un vecchio hash
password, come nella webapp legacy.

`GET /api/kpi?months=12` legge gli snapshot mensili del terapista autenticato
(da 1 a 36 mesi; predefinito 12). Restituisce solo i valori salvati e
`computedAt`: l'aggiornamento automatico è ancora eseguito dalla webapp legacy.
Per una prova locale, con il backend avviato:

```bash
curl -u marco 'http://127.0.0.1:8081/api/kpi?months=12'
```

`curl` chiede la password senza includerla nel comando. Confrontare anno, mese,
conteggi e data di calcolo con gli snapshot usati dalla pagina legacy.

## Configurazione

Il file operativo del backend è `fisio-backend/config.properties`, ignorato da
Git. [config.properties.example](config.properties.example) è il modello
versionato. In questo ambiente locale il file operativo è già stato copiato
dalla configurazione della webapp; in futuro potrà avere credenziali DB proprie.

Per un nuovo ambiente:

```bash
cp fisio-backend/config.properties.example fisio-backend/config.properties
```

Compilare `db.url`, `db.username` e `db.password`. Le variabili `FISIO_DB_URL`,
`FISIO_DB_USER` e `FISIO_DB_PASSWORD`, se presenti, prevalgono sul file.
`FISIO_DB_CONFIG_FILE` permette di indicare un percorso esplicito.

## Avvio locale

Richiede JDK 15 o successivo e Maven. Dalla radice del repository:

```bash
./run-backend-locale.sh
```

Lo script compila il backend, carica `fisio-backend/config.properties` e
ascolta su `127.0.0.1:8081`. Fermare un'eventuale istanza precedente prima di
avviarlo. Controlli:

```bash
curl -i http://127.0.0.1:8081/health
curl -i http://127.0.0.1:8081/ready
```

`/ready` deve rispondere HTTP 200 con `{"status":"ok"}`. HTTP 503 con
`{"status":"unavailable"}` indica che il backend non raggiunge il database.

## Pacchetto server

```bash
mvn -pl fisio-backend -am -DskipTests package
```

Distribuire `fisio-backend/target/fisio-backend-0.1.0.jar` e il proprio
`config.properties` nella stessa directory. Il backend rileva automaticamente
il file accanto al JAR; si avvia con `java -jar fisio-backend-0.1.0.jar`.
Il file con le credenziali non va caricato su GitHub. In questa fase il processo
ascolta solo su loopback; accesso remoto, HTTPS e sessioni sono passi separati.

## Deploy sul server attuale

`./deploy-remoto.sh` usa come impostazione iniziale SSH `root@31.70.74.92:22`,
compila solo `fisio-backend`, installa il JAR in `/opt/fisio-backend` e gestisce
`fisio-backend.service` con systemd. Il servizio gira come utente dedicato,
legge `/opt/fisio-backend/config.properties` e ascolta su `127.0.0.1:8081`.
Non distribuisce WAR, Baileys o migrazioni SQL e non modifica Tomcat.

Per la prima installazione, preparare un file privato con le credenziali del DB
raggiungibile dal server e passarlo una volta allo script:

```bash
./deploy-remoto.sh --config /percorso/privato/config.properties
```

Gli aggiornamenti successivi usano `./deploy-remoto.sh` senza `--config`: il file
remoto non viene sovrascritto. Si può usare una chiave SSH oppure impostare
`DEPLOY_SSH_PASSWORD` nell'ambiente (serve `sshpass`); `--host`, `--user` e
`--port` modificano la destinazione. Il server richiede Java, `systemd` e
`curl`. Lo script verifica `/ready` sul server e ripristina il JAR precedente
se il nuovo non diventa pronto. Il backend resta raggiungibile solo dal server:
per i client Windows remoti serviranno un endpoint HTTPS e la configurazione
dell'URL nel desktop.

`POST /api/auth/remember` richiede Basic e rilascia un token di accesso automatico
valido 30 giorni; `DELETE` con Bearer lo revoca. Il backend conserva solo l'hash
del token. Le API protette accettano Basic o Bearer e verificano sempre ruolo e
scadenza. Il client desktop conserva il token nell'archivio protetto dell'utente
del sistema operativo. L'endpoint è utilizzabile solo sul backend locale; prima
di esporlo in rete servono HTTPS e una revisione dell'autenticazione remota.

L'API calendario accetta `GET` con autenticazione Basic o Bearer e un intervallo massimo
di 62 giorni. `POST /api/calendar` crea un appuntamento con campi form
`patientName`, `patientPhone`, `start`, `end`, `allDay`, `nonTreatmentEvent` e
`notes`. Usa le regole legacy per paziente, scatti di 15 minuti e conflitti
(HTTP 409). `PUT /api/calendar/{id}` modifica un appuntamento pianificato;
`DELETE /api/calendar/{id}` lo sposta nel cestino. Entrambe le operazioni
verificano il proprietario, rispondono 404 per gli ID non accessibili e 409
per uno stato o una fascia non validi. L'ID del terapista è ricavato
dall'account, mai dal client.
Il cestino del calendario usa `GET /api/calendar/trash`,
`PUT /api/calendar/trash/{id}` per ripristinare e `DELETE` sul singolo ID o
sull'intero cestino. Il ripristino controlla i conflitti; la lettura applica
la scadenza automatica di 30 giorni, come nel legacy.
`GET /api/reminders/preview?date=YYYY-MM-DD` legge il modello salvato e
compone l'anteprima dei promemoria degli appuntamenti programmati del
terapista. `POST /api/reminders/template` salva il modello del terapista;
un testo vuoto ripristina quello predefinito. `POST /api/reminders/send` riceve
`date`, uno o più `appointmentId` e l'eventuale `template` come form. Salva il
modello fornito e invia soltanto agli appuntamenti selezionati e ancora accessibili.
Per abilitarlo, configurare `whatsapp.baileys.enabled=true` nel file privato
del backend; `whatsapp.baileys.therapistId` limita opzionalmente l'account.
`gatewayBaseUrl` deve puntare al servizio Baileys raggiungibile dal backend.
In caso di risposta incerta all'invio, verificare sul gateway prima di
riprovare per evitare doppioni. In locale si può avviare il servizio
separatamente con `./run-baileys-locale.sh` dalla radice.
`GET /api/whatsapp/status` legge stato e QR dal gateway usando la configurazione
privata del backend. Il QR è disponibile solo per il terapista autorizzato e
collega la sessione WhatsApp centrale del server.
Sul server impostare `whatsapp.baileys.managementMode=systemd`: il gateway parte
con `fisio-baileys.service` e il desktop mostra solo stato e QR. Per abilitare
**Avvia** e **Arresta** nell'ambiente locale, scegliere
`whatsapp.baileys.managementMode=manual` e impostare
`whatsapp.baileys.serviceDirectory` al percorso assoluto della cartella
`baileys-service` nel file privato del backend. L'utente che esegue il backend
deve poter scrivere nella cartella, nel log e nella directory di sessione.
`POST /api/whatsapp/control` esegue lo script `start-baileys.sh` o chiede al
gateway di arrestarsi tramite `/api/shutdown`. Risponde 202 alla richiesta;
controllare poi lo stato. Il deploy remoto distribuisce solo il backend:
l'installazione di Baileys sul server è gestita separatamente da
`./deploy-baileys-remoto.sh`. La procedura completa è nella
[guida WhatsApp](../docs/operations/whatsapp-baileys.md).
La lista d'attesa usa `GET /api/waitlist`, `POST /api/waitlist` con campi form
`fullName` e `phone`, e `DELETE /api/waitlist/{id}`. Anche in scrittura il
terapista viene ricavato dall'account. Dopo ogni modifica il desktop rilegge
la lista dal backend.

`GET /api/treatments` legge lo storico del terapista; `patientId` limita la
cronologia al paziente proprietario. `POST /api/treatments/appointments/{id}`
completa un appuntamento pianificato e registra piano e seduta usando i campi
form del legacy (`planTitle`, `totalSessionsPlanned`, `goals`, `frequencyPerWeek`,
`expectedEndDate`, `painScorePre`, `painScorePost`, `sessionOutcome`,
`homeExercises`, `notes`). L'ID del terapista deriva dall'autenticazione.

La rubrica usa `GET /api/patients` con `q` (ricerca), `sort` (nome o data
creazione) e, facoltativamente, `treatedDate` oppure `treatedMonth`. L'API
restituisce solo i pazienti del terapista autenticato.
`GET /api/patients/{id}` legge il dettaglio anagrafico della scheda;
un ID appartenente a un altro terapista restituisce 404.
`GET /api/patients/{id}/anamnesis` restituisce l'ultima anamnesi e le
condizioni della scheda in sola lettura; se manca l'anamnesi restituisce `{}`.
`POST /api/patients` accetta campi form `fullName`, `email` e `phone` e
restituisce HTTP 201 con l'ID della scheda creata. Il proprietario è ricavato
dall'autenticazione.
`PUT /api/patients/{id}` aggiorna anagrafica e anamnesi, oppure unisce il
contatto quando è presente `mergeTargetId`. I candidati all'unione arrivano da
`GET /api/patients/{id}/merge-candidates?fullName=...`.
`DELETE /api/patients/{id}` elimina la scheda; con appuntamenti collegati richiede
`force=1` dopo la conferma mostrata dal client. Tutte le operazioni verificano
il terapista proprietario tramite i servizi condivisi.
