# Configurare WhatsApp con Baileys

Baileys è un processo Node.js separato. Ascolta solo su `127.0.0.1:3001` e
mantiene **una sessione WhatsApp condivisa**. Il backend Java usa quel gateway
per stato, QR e invio dei promemoria; il desktop parla solo con il backend.
Il database non contiene le credenziali WhatsApp. La cartella `auth-session`
le conserva e non deve essere pubblicata su GitHub.

## Prova locale

1. Installare Node.js **20.9 o successivo** e npm. Dalla radice del progetto
   avviare `./run-baileys-locale.sh` in un terminale. Al primo avvio lo script
   installa le dipendenze in `baileys-service/node_modules` e usa
   `baileys-service/auth-session` per la sessione. Lasciare il terminale aperto.
2. In un secondo terminale controllare:

   ```bash
   curl http://127.0.0.1:3001/api/status
   ```

   `ready: true` significa connesso; `qrRequired: true` richiede la scansione
   del QR. Il QR è disponibile anche in `http://127.0.0.1:3001/api/qr`.
3. Nel file privato `fisio-backend/config.properties` impostare:

   ```properties
   whatsapp.baileys.enabled=true
   whatsapp.baileys.gatewayBaseUrl=http://127.0.0.1:3001
   whatsapp.baileys.gatewayTimeoutMs=4000
   whatsapp.baileys.managementMode=manual
   whatsapp.baileys.serviceDirectory=/percorso/assoluto/Fisio-e-Sports/baileys-service
   ```

   `therapistId` è facoltativo: vuoto permette a tutti i terapisti di usare
   l'unica sessione; un ID limita anteprima di stato, controllo e invio a
   quell'account. Il backend legge il file all'avvio: riavviarlo dopo le
   modifiche. La directory indicata deve essere scrivibile dall'utente del
   backend. Con `managementMode=manual` il desktop mostra Avvia/Arresta.
4. Avviare `./run-backend-locale.sh`, poi `./run-desktop-locale.sh`. Il desktop
   può aprire il login anche senza backend; per accedere ai dati il backend
   deve rispondere su `/ready`. In **Impostazioni** controllare stato e QR.
   Verificare prima il salvataggio del modello promemoria, poi inviare solo
   verso un numero di prova. Il desktop mostra quanti invii sono riusciti.

Per arrestare il gateway avviato da terminale, usare
`./baileys-service/stop-baileys.sh` da un altro terminale. Non impostare
`BAILEYS_RESET_SESSION=1` se si vuole conservare l'associazione WhatsApp.

## Server remoto: systemd automatico

Il server usa `fisio-baileys.service` per avviare Baileys al boot e riavviarlo
se termina. Il codice è in `/opt/fisio-baileys`; la sessione privata è in
`/var/lib/fisio-baileys/auth-session`, di proprietà dell'utente
`fisio-baileys`. Il backend e Baileys devono girare **sulla stessa macchina**:
entrambi usano l'indirizzo locale `127.0.0.1:3001`. La porta 3001 non va
esposta pubblicamente. Il servizio non stampa il QR nei log di systemd; leggerlo
dal desktop autenticato.

1. Sul server installare Node.js **20.9 o successivo**, npm e systemd. Se il
   vecchio gateway è in uso, fermarlo in una finestra senza invii e verificare
   che la porta 3001 sia libera. Non avviare due gateway con la stessa sessione.
2. Se esiste `/opt/baileys-service/auth-session`, conservarla prima del nuovo
   deploy. A vecchio gateway fermo, come root sul server:

   ```bash
   id fisio-baileys >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin fisio-baileys
   install -d -o fisio-baileys -g fisio-baileys -m 0700 /var/lib/fisio-baileys
   cp -a /opt/baileys-service/auth-session /var/lib/fisio-baileys/
   chown -R fisio-baileys:fisio-baileys /var/lib/fisio-baileys
   ```

   Se non c'è una sessione precedente, il servizio ne creerà una nuova e
   mostrerà il QR. Lo script di deploy si ferma se trova una sessione legacy
   non trasferita o un gateway già attivo durante la prima installazione.
3. Dalla root del repository locale eseguire `./deploy-baileys-remoto.sh`.
   Usa lo stesso host SSH predefinito di `deploy-remoto.sh`; `--host`, `--user`
   e `--port` permettono di cambiarlo. Distribuisce solo il gateway, esegue
   `npm ci` sul server, installa il servizio systemd e verifica `/api/status`.
   Non copia né sovrascrive la directory della sessione.
4. Nel file **privato** `/opt/fisio-backend/config.properties` impostare
   `whatsapp.baileys.enabled=true`, `gatewayBaseUrl=http://127.0.0.1:3001` e
   `whatsapp.baileys.managementMode=systemd`. `serviceDirectory` non serve in
   questa modalità. Riavviare `fisio-backend.service` dopo aver aggiornato il
   file. Se la webapp legacy resta in uso, impostare anche nel suo file di
   configurazione `managementMode=systemd` e ridistribuire il WAR: i pulsanti
   Avvia/Arresta legacy scompaiono.
5. Sul server verificare:

   ```bash
   systemctl status fisio-baileys.service
   curl http://127.0.0.1:3001/api/status
   journalctl -u fisio-baileys.service -n 50 --no-pager
   ```

   Nel desktop **Impostazioni** restano visibili stato, QR e Aggiorna stato;
   i pulsanti Avvia/Arresta sono nascosti in modalità systemd. Il QR collega
   la sessione centrale del server. Dopo la scansione attendere `ready: true`.

Il client desktop attuale usa ancora `127.0.0.1:8081` per il backend: per
collegarsi da un PC diverso dal server serviranno l'URL remoto configurabile
e HTTPS. Questo passaggio di rete è separato dalla gestione Baileys.

## Se qualcosa non funziona

- **Non configurato:** controllare `whatsapp.baileys.enabled`, il file privato
  caricato dal backend e l'eventuale `therapistId`; poi riavviare il backend.
- **Non attivo:** controllare il servizio systemd, la porta 3001 e i log sopra.
- **QR assente:** il gateway può ancora inizializzarsi; aggiornare lo stato e
  controllare `qrRequired` in `/api/status`.
- **Invio fallito:** controllare `ready: true`, numero internazionale del
  paziente e log del gateway. Se la risposta è incerta, verificare il messaggio
  sul telefono prima di riprovare per evitare duplicati.
