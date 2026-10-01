# Backend Java — primi incrementi

Questo modulo avvia un piccolo servizio HTTP autonomo. `GET /health` conferma
l'avvio del processo. `GET /ready` controlla che MySQL accetti una connessione;
non modifica dati o schema. Nessun dato applicativo è ancora esposto.

Richiede JDK 15 o successivo e Maven. Dalla radice del repository:

```bash
mvn -f fisio-backend/pom.xml package
java -jar fisio-backend/target/fisio-backend-0.1.0.jar
```

In un altro terminale:

```bash
curl -i http://127.0.0.1:8081/health
curl -i http://127.0.0.1:8081/ready
```

Per verificare il database, configura `FISIO_DB_URL`, `FISIO_DB_USER` e
`FISIO_DB_PASSWORD` nell'ambiente del processo prima di avviarlo. Esempio con
valori di sviluppo, da sostituire con quelli del proprio database di test:

```bash
export FISIO_DB_URL='jdbc:mysql://127.0.0.1:3306/fisio_e_sport?connectTimeout=3000'
export FISIO_DB_USER='utente_di_test'
export FISIO_DB_PASSWORD='password_di_test'
java -jar fisio-backend/target/fisio-backend-0.1.0.jar
```

`/health` risponde HTTP 200 con `{"status":"ok"}`. `/ready` risponde HTTP 200
con lo stesso JSON quando MySQL è disponibile, oppure HTTP 503 con
`{"status":"unavailable"}` se la configurazione manca o la connessione fallisce.
Il servizio ascolta solo su `127.0.0.1` in questa fase; la porta si può cambiare
con `FISIO_BACKEND_PORT`. Si arresta con Ctrl+C.
