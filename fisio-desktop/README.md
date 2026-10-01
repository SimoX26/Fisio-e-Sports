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

Aprire **Rubrica**, cercare un paziente e cambiare l'ordinamento. Confrontare
l'elenco con la webapp legacy usando lo stesso terapista. Ripetere con un
secondo terapista: le schede dell'altro account non devono comparire.
Selezionare un nome per aprire il modulo della scheda e confrontarlo con la
webapp. La scheda si apre in modalità lettura con i soli campi compilati.
**Modifica** mostra tutti i campi, anche quelli vuoti; **Annulla modifica**
ricarica i valori salvati. Provare su una scheda di test a compilare un campo
anamnestico vuoto e a salvare: la vista deve tornare al riepilogo e mostrare
il nuovo valore. Provare anche una scheda senza anamnesi e i campi delle
condizioni.
Con due contatti di test con lo stesso nome, verificare i candidati di unione
e la conferma prima di eseguire l'operazione. Provare l'eliminazione su una
scheda di test senza appuntamenti e su una con appuntamenti, verificando la
conferma estesa e il mantenimento dello storico.
Provare **Nuovo paziente** dalla home e dalla rubrica: dopo il salvataggio la
scheda deve comparire nella rubrica del terapista e nella webapp legacy, ma
non nella rubrica di un secondo terapista. Email e telefono sono facoltativi.

Nella home provare la lista d'attesa: aggiungere un contatto, controllare che
appaia nella webapp legacy con lo stesso terapista, rimuoverlo dal desktop e
verificare che scompaia anche nella webapp. Con un altro terapista il contatto
non deve comparire. La trasformazione in appuntamento arriverà con l'API di
scrittura del calendario.

La password non viene salvata su disco; la credenziale HTTP resta nella memoria
della finestra fino al Logout o alla chiusura. Questa prova usa il backend
locale su `127.0.0.1:8081`. La home contiene per ora i dati coperti
dalle API calendario e lista d'attesa; ricerca globale, altre scritture, altre schermate e pacchetto Windows
richiedono i prossimi incrementi. Le relative voci sono solo etichette.

Le licenze degli asset incorporati sono in `src/main/resources/desktop/vendor/`.
