# Desktop: login e prime schermate

La finestra JavaFX carica HTML/JavaScript locale con Bootstrap 5.3.2,
FullCalendar 6.1.11 e una copia del CSS della webapp. Si apre sul login del
terapista. Il backend verifica l'account nel database e restituisce i dati
mostrati nella home e nel calendario. Il desktop non accede a MySQL.

Prima interrompere l'eventuale vecchio backend, avviare
`./run-backend-locale.sh` in un terminale e controllare che
`http://127.0.0.1:8081/ready` risponda `{"status":"ok"}`. Poi, in un
secondo terminale dalla radice del repository:

```bash
mvn -pl fisio-desktop javafx:run
```

Accedere con un terapista attivo. Dopo il login si apre la home con il saluto,
gli appuntamenti e i pazienti di oggi e l'agenda, letti dal backend. Dal menu
aprire il calendario e controllare giorno, settimana, mese, dettaglio evento e
ritorno alla home. Verificare anche credenziali errate e Logout: i dati
precedenti non devono restare visibili dopo l'uscita. Ripetere con un secondo
terapista per verificare la separazione degli appuntamenti.

La password non viene salvata su disco; la credenziale HTTP resta nella memoria
della finestra fino al Logout o alla chiusura. Questa prova usa il backend
locale su `127.0.0.1:8081`. La home contiene per ora solo i dati coperti
dall'API calendario; ricerca, scritture, altre schermate e pacchetto Windows
richiedono i prossimi incrementi. Le relative voci sono solo etichette.

Le licenze degli asset incorporati sono in `src/main/resources/desktop/vendor/`.
