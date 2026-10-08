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

Lo script verifica `/ready` e apre la finestra JavaFX con `GDK_SCALE=2`; il backend resta nel
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
terapista e non nel calendario di un altro. Provare **Modifica** sullo stesso
appuntamento: cambiare orario e note, verificare il risultato anche nel legacy
e provare una fascia occupata. Provare **Elimina** su un appuntamento di test:
la conferma deve precedere la cancellazione e l'evento deve sparire dal
calendario. Aprire **Cestino**, ordinare per paziente e ripristinare l'evento;
deve tornare nel calendario e nel legacy. Riprovare con una fascia occupata:
deve comparire un conflitto e l'evento deve restare nel cestino. Per una prova
separata, eliminare definitivamente un evento di test e svuotare il cestino
solo se contiene esclusivamente dati eliminabili. Verificare che un appuntamento
completato non mostri Modifica o Elimina, mentre un evento generico completato
può essere eliminato. Da un appuntamento pianificato con paziente, provare
**Completa trattamento** con un piano da una seduta e verificare che appaia
nella cronologia del paziente, nello storico generale e nella webapp legacy.
Per un appuntamento che termina più tardi oggi o in un giorno futuro il
pulsante non deve comparire; dopo l'orario di fine può comparire.
Nel calendario la scheda di ogni appuntamento completato deve diventare verde;
nel dettaglio i pulsanti devono restare dentro il riquadro anche con finestra stretta.
Nel desktop tutte le modali si aprono senza animazione e rotellina e touchpad
usano lo scorrimento nativo. Provare lo scorrimento nella pagina, nel calendario
e nei moduli lunghi.
Confrontare anche uno storico già presente, poi ripetere con un secondo
terapista: la cronologia del primo non deve essere accessibile. Eventi generici
e tutto il giorno non devono mostrare il pulsante di anteprima.
Da **Promemoria** nella home o **Anteprima promemoria** nel dettaglio di un
appuntamento programmato, confrontare modello e messaggi con la webapp legacy.
Modificare il modello e controllare che l'anteprima cambi subito. Premere
**Salva modello**, chiudere e riaprire la finestra: il testo deve persistere.
Lasciare vuoto e salvare per ripristinare il modello predefinito. Verificare
che il secondo terapista conservi un modello separato.
Cambiare giorno e selezione degli appuntamenti; con un secondo terapista non
devono comparire i pazienti del primo. Con gateway Baileys configurato nel
backend, selezionare un proprio appuntamento di prova, premere **Invia
selezionati** dopo una modifica del modello e confrontare testo e conteggio con
il messaggio ricevuto. L'invio salva anche il modello corrente. Senza
configurazione il pulsante resta disabilitato. Se l'esito è incerto, controllare
il gateway prima di ripetere l'invio.
Aprire **Impostazioni**: verificare lo stato WhatsApp e usare **Aggiorna stato**.
Con gateway non autenticato compare il QR da scansionare sul telefono; dopo
l'accesso deve apparire **Connesso**. Senza configurazione, lo stato è
**Non configurato**. La pagina aggiorna lo stato ogni cinque secondi.
Chiudere e riaprire
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
dalle API calendario e lista d'attesa; ricerca globale, statistiche e
impostazioni richiedono i prossimi incrementi. Le relative voci sono solo etichette.

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
