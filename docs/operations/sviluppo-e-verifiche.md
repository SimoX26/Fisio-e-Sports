# Sviluppo e verifiche

## Requisiti

JDK 15 o successivo, Maven 3.8 o successivo e MySQL 8 per la webapp. Il wrapper
Android richiede inoltre JDK 17 e Android SDK, secondo la sua guida dedicata.

## Configurazione locale

```bash
cp fisio-web-legacy/src/main/resources/config.properties.example \
   fisio-web-legacy/src/main/resources/config.properties
```

Impostare `db.url`, `db.username` e `db.password` nel file locale. Il file reale
è ignorato da Git. `db.sql` elimina e ricrea il database: usarlo solo per un
ambiente di sviluppo vuoto. Le migrazioni per dati esistenti si trovano in
`fisio-web-legacy/src/main/resources/migrations/` e non vengono applicate da
Maven.

## Build

```bash
mvn clean package
```

Il comando compila tutti i moduli. Il WAR si trova in
`fisio-web-legacy/target/Fisio-e-Sports.war`; il JAR autonomo si trova in
`fisio-backend/target/`. Per il deploy Tomcat locale è disponibile
`./deploy-locale.sh --help`; `./deploy-remoto.sh --help` distribuisce solo il
backend Java sul server attuale.
Il deploy locale compila solo `fisio-web-legacy` e i moduli da cui dipende,
poi copia `fisio-web-legacy/target/Fisio-e-Sports.war` in Tomcat.

Il backend autonomo si avvia seguendo [la guida del modulo](../../fisio-backend/README.md).
Per provarlo localmente, configurare `fisio-backend/config.properties`, usare
`./run-backend-locale.sh` e verificare che `/ready` risponda `ok` prima del login desktop.
In un secondo terminale `./run-desktop-locale.sh` verifica il backend e apre
il desktop JavaFX.
Per i promemoria WhatsApp, avviare separatamente il gateway con
`./run-baileys-locale.sh` dalla radice del progetto. Lo script usa
`baileys-service/start-baileys.sh`, installa le dipendenze Node.js se mancano e
resta in primo piano. Il gateway risponde su `127.0.0.1:3001`; lo stato si
controlla con `curl http://127.0.0.1:3001/api/status`. Fermarlo con
`./baileys-service/stop-baileys.sh` da un altro terminale.
`/health` controlla il processo; `/ready` controlla la connessione MySQL.
La configurazione DB del backend usa variabili d'ambiente separate dalla
configurazione della webapp.
Il pacchetto desktop Windows si crea su Windows con
[`deploy-desktop-windows.ps1`](../../deploy-desktop-windows.ps1); requisiti e
prova sono nella [guida desktop](../../fisio-desktop/README.md).

## Verifiche prima di un deploy

1. Eseguire la build aggregata e verificare il WAR.
2. Applicare solo le migrazioni richieste, dopo backup e in finestra senza
   scritture applicative.
3. Avviare la webapp e provare login, rubrica, calendario, anamnesi,
   trattamenti, KPI e promemoria su dati di test.
4. Provare due account terapista distinti: il secondo non deve leggere,
   cercare, aggiornare, unire o eliminare il paziente del primo, nemmeno
   inviando direttamente l'ID nelle richieste HTTP.
5. Verificare che il nuovo backend risponda a `/health` e `/ready`.

I test JUnit del servizio di autenticazione sono in `fisio-application/src/test`.
Se Maven non può scaricare il provider JUnit 3 in un ambiente offline, la build
con `-DskipTests` compila comunque i test ma non li esegue. In quel caso vanno
eseguiti separatamente e l'esito va dichiarato con precisione.
