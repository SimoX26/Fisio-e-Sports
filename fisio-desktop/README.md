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
./run-desktop-locale.sh
```

Lo script verifica `/ready` e apre la finestra JavaFX; il backend resta nel
primo terminale. Per chiudere il desktop usare la finestra o `Ctrl+C` nel
secondo terminale.

Accedere con un terapista attivo. Dopo il login si apre la home con il saluto,
gli appuntamenti e i pazienti di oggi e l'agenda, letti dal backend. Dal menu
aprire il calendario e controllare giorno, settimana, mese, dettaglio evento e
ritorno alla home. Verificare anche credenziali errate e Logout: i dati
precedenti non devono restare visibili dopo l'uscita. Ripetere con un secondo
terapista per verificare la separazione degli appuntamenti. Nel dettaglio di un
appuntamento controllare data, orario, note e pulsanti visibili secondo lo stato;
**Dettagli paziente** deve aprire la scheda nella rubrica. Provare anche un evento
generico e uno tutto il giorno. **Crea** apre il modulo legacy: verificare i
suggerimenti dei pazienti, la creazione di un paziente nuovo con telefono, un
evento generico e uno tutto il giorno con paziente già presente. Provare una
fascia occupata: il modulo deve restare aperto e mostrare l'errore. Controllare
che l'appuntamento salvato compaia anche nella webapp legacy dello stesso
terapista e non nel calendario di un altro. Modifica, cancellazione,
completamento e promemoria sono ancora disabilitati: richiedono le rispettive
API e saranno collegati nei prossimi passi. Chiudere e riaprire
il desktop: con backend attivo deve entrare automaticamente nello stesso account.
Premere **Logout** e riaprire: deve comparire il login. Ripetere con un secondo
terapista e verificare che non riappaiano i dati del primo. Se il backend è
spento, l'app deve restare sul login senza mostrare dati precedenti.

Aprire **Rubrica**, cercare un paziente e cambiare l'ordinamento. Confrontare
l'elenco con la webapp legacy usando lo stesso terapista. Ripetere con un
secondo terapista: le schede dell'altro account non devono comparire.
Selezionare una riga della rubrica per aprire il modulo della scheda e confrontarlo con la
webapp. La scheda si apre in modalità lettura con i soli campi compilati.
**Modifica** mostra tutti i campi, anche quelli vuoti; **Annulla modifica**
ricarica i valori salvati. Provare su una scheda di test a compilare un campo
anamnestico vuoto e a salvare: la vista deve tornare al riepilogo e mostrare
il nuovo valore. Provare anche una scheda senza anamnesi e i campi delle
condizioni. In modalità **Modifica**, scorrere fino alle note libere in fondo
al modulo e verificare che i pulsanti di salvataggio restino raggiungibili.
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

La password non viene salvata su disco. Il login crea un token revocabile di
30 giorni: Windows lo protegge con DPAPI nel profilo utente, Linux usa
Secret Service tramite `secret-tool`. Se l'archivio protetto non è disponibile,
il desktop segnala che l'accesso automatico non è attivo e la sessione corrente
resta utilizzabile. Logout elimina il token locale e ne chiede la revoca al
backend; se la revoca fallisce viene mostrato un avviso. Questa prova usa il backend
locale su `127.0.0.1:8081`. La home contiene per ora i dati coperti
dalle API calendario e lista d'attesa; ricerca globale, altre scritture e altre
schermate richiedono i prossimi incrementi. Le relative voci sono solo etichette.

Le licenze degli asset incorporati sono in `src/main/resources/desktop/vendor/`.

## Pacchetto Windows

Su Windows, con JDK 21 o successivo (`JAVA_HOME`), Maven e WiX compatibile con
il JDK per il formato EXE, eseguire dalla radice del repository:

```powershell
.\deploy-desktop-windows.ps1
```

Lo script compila il desktop, copia le dipendenze JavaFX per Windows e crea un
installer EXE con runtime Java incluso in
`fisio-desktop/target/windows-dist/1.0.0/`. Per una prima prova senza WiX:

```powershell
.\deploy-desktop-windows.ps1 -Type app-image
```

Il risultato portabile è una cartella con launcher `.exe` e runtime incluso:
va copiata per intero. `-AppVersion 1.0.1` permette di cambiare la versione del
pacchetto. La build Windows non è eseguibile da Linux. L'app desktop usa ancora
`http://127.0.0.1:8081`: per provarla serve il backend avviato sullo stesso PC.
L'installer non include il backend o il database e non modifica i dati.
