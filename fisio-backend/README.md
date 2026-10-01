# Backend Java

Il processo HTTP autonomo espone `/health`, `/ready`, `/api/me` e
`/api/calendar`. Le API sono limitate ai terapisti autenticati; il calendario
mostra solo i loro appuntamenti. Il login può aggiornare un vecchio hash
password, come nella webapp legacy.

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

L'API calendario accetta `GET` con autenticazione Basic e un intervallo massimo
di 62 giorni. L'ID del terapista è ricavato dall'account, mai dal client.
