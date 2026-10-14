# Desktop: login e prime schermate

La finestra JavaFX carica HTML/JavaScript locale con Bootstrap 5.3.2,
FullCalendar 6.1.11 e una copia del CSS della webapp. Si apre sul login.
Il backend verifica l'account nel database; i terapisti vedono home e calendario,
gli amministratori le richieste di accesso. Il desktop non accede a MySQL.

Avviare il desktop dalla radice del repository:

```bash
./run-desktop-locale.sh
```

Lo script apre la finestra JavaFX con `GDK_SCALE=2` anche se il backend non è
disponibile. Per effettuare il login e usare i dati, avviare il backend con
`./run-backend-locale.sh` in un altro terminale e controllare che
`http://127.0.0.1:8081/ready` risponda `{"status":"ok"}`. Per chiudere il
desktop usare la finestra o `Ctrl+C` nel terminale di avvio.

Per scegliere un backend diverso, impostare `FISIO_BACKEND_URL` prima di avviare
il desktop, per esempio:

```bash
FISIO_BACKEND_URL=https://backend.example.com ./run-desktop-locale.sh
```

Senza la variabile usa `http://127.0.0.1:8081`. Per un indirizzo remoto serve
HTTPS con certificato attendibile; HTTP è ammesso solo su localhost. L'URL deve
essere l'origine del backend, senza percorso, query o credenziali. Su Windows
impostare la stessa variabile d'ambiente prima di avviare l'app installata.
I token protetti sono separati per indirizzo: al primo collegamento a un
backend diverso occorre fare login, mentre il token locale esistente resta
disponibile per il backend locale. Il server remoto deve esporre le API tramite
un proxy HTTPS; il backend Java continua ad ascoltare su loopback nel server.
Se `/ready` risponde `ok` ma il login fallisce, controllare l'indirizzo mostrato
nel messaggio del desktop. «Configurazione del backend non disponibile» indica
che il collegamento fra JavaFX e la pagina locale non è stato inizializzato;
riavviare il desktop dopo la build aggiornata. Se persiste, controllare il
terminale del desktop per «pagina locale non riconosciuta».

Per provare **Richiedi accesso**, aprire il link sotto il login, compilare i
cinque campi e inviare. Il desktop deve tornare al login con conferma; la nuova
richiesta deve comparire fra le pendenti nella webapp legacy, senza permettere
ancora l'accesso. Dopo l'approvazione da parte di un amministratore, provare
il login desktop. Verificare anche username già esistente, richiesta pendente
duplicata e backend spento: il modulo deve mostrare un errore e conservare i
dati compilati, tranne la password. Accedere poi con un account `ADMIN`: la
home deve mostrare il numero di richieste in attesa, aggiornato a ogni apertura.
**Gestisci richieste** apre la lista con la richiesta appena inviata. Provare **Approva** su
una richiesta di test e verificare, dopo conferma, che passi nello storico e
che il nuovo terapista possa accedere. Provare **Rifiuta** su una seconda
richiesta di test: deve passare nello storico senza creare un account. Un
terapista non deve poter aprire le API `/api/admin`; dopo Logout da admin, il
token automatico deve essere revocato.

Per verificare **Dati e Statistiche**, aprire la voce nel menu dopo il login:
confrontare le schede del mese corrente, il grafico e il dettaglio mensile con
la pagina legacy `/dashboard/insights`. Provare **I miei dati** e **Dati
globali**, poi 6, 12 e 24 mesi. Controllare che il titolo sia a sinistra dei
filtri nella barra superiore e che `Ultimo calcolo` compaia sotto il titolo
con ore e minuti, senza secondi,
che i dati personali cambino con un secondo terapista e che al logout i dati
del primo non ricompaiano durante un nuovo caricamento. Se il backend non è
raggiungibile, la schermata deve mostrare un errore senza dati precedenti.
Nella navigazione desktop, verificare che la voce corrente resti evidenziata
passando tra Home, Calendario, Rubrica, Storico trattamenti, Dati e Statistiche
e Impostazioni. Il Cestino mantiene evidenziato Calendario. I titoli ripetuti
delle altre pagine non sono visibili; pulsanti e filtri restano disponibili. Nel
dettaglio mensile delle statistiche, le intestazioni non devono spezzare le
parole a metà.

Accedere con un terapista attivo. Dopo il login si apre la home con il saluto,
gli appuntamenti e i pazienti di oggi e l'agenda, letti dal backend.
Confrontare i tre contatori della home con la webapp legacy: i promemoria
contano gli appuntamenti futuri di oggi con paziente. Provare **Nuovo
appuntamento**, **Nuovo paziente** e **Invia promemoria** dalla barra superiore;
il contatore dei promemoria deve aprire l'anteprima di oggi.
Premere **Pazienti oggi**: la rubrica deve mostrare solo i pazienti con
appuntamenti odierni, con avviso del filtro attivo. **Rimuovi filtro** e una
ricerca normale devono ripristinare l'elenco completo; cambiare ordinamento
deve conservare il filtro finché è attivo.
Nell'agenda della home, un appuntamento completato deve mostrare l'etichetta
**COMPLETATO**, come nella webapp legacy; uno programmato non mostra etichette.
Nella lista d'attesa premere **Trasforma in appuntamento** su un contatto di
prova: nome e telefono devono comparire nel modulo. Salvare una fascia libera,
verificare il messaggio di conferma nella home, l'appuntamento nel calendario e
la rimozione del contatto dalla lista. Ripetere con un secondo
terapista per controllare che non compaiano i contatti del primo. Dal menu
aprire il calendario e controllare giorno, settimana, 2 settimane, mese, dettaglio evento e
ritorno alla home. Le quattro viste devono stare a sinistra del periodo
centrato; **Cestino** rosso e **+ Nuovo** celeste devono stare a destra nella
stessa barra. La barra ha un riquadro chiaro e le quattro viste formano un gruppo
con quella corrente evidenziata, anche dopo un cambio di vista. La vista
**2 settimane** deve mostrare 14 giorni consecutivi nella stessa griglia oraria;
le frecce devono avanzare o tornare di due settimane. Tutti i 14 giorni devono
entrare nella larghezza della finestra, senza scorrimento orizzontale. Le card
mostrano l'ora iniziale; il dettaglio conserva l'intervallo completo. I giorni
senza eventi visibili devono avere colonne più strette, mostrare solo il numero
del giorno e non avere la campanella; il layout deve aggiornarsi cambiando
periodo o aggiungendo un appuntamento.
Verificare anche credenziali errate e Logout: i dati
precedenti non devono restare visibili dopo l'uscita. Ripetere con un secondo
terapista per verificare la separazione degli appuntamenti. Nel dettaglio di un
appuntamento controllare data, orario, telefono, note e pulsanti visibili secondo lo stato;
la finestra deve restare compatta anche su uno schermo ampio;
accanto ai giorni con appuntamenti delle viste settimana e 2 settimane, la
campanella deve aprire l'anteprima dei promemoria con quel giorno già
selezionato, anche dopo aver cambiato settimana. Nella settimana singola i
giorni vuoti conservano la stessa larghezza degli altri. Verificare che la
campanella non compaia nei giorni vuoti né nelle viste giorno e mese;
**Dettagli paziente** deve aprire la scheda nella rubrica. Provare anche un evento
generico e uno tutto il giorno. **+ Nuovo** apre il modulo legacy: verificare i
suggerimenti dei pazienti, la creazione di un paziente nuovo con telefono, un
evento generico e uno tutto il giorno con paziente già presente. Provare una
fascia occupata: il modulo deve restare aperto e mostrare l'errore. Controllare
che l'appuntamento salvato compaia anche nella webapp legacy dello stesso
terapista e non nel calendario di un altro. Nelle viste giorno, settimana e 2 settimane,
verificare che le righe orarie siano compatte e che orario e nome restino
leggibili anche negli appuntamenti di 15 minuti. Provare un nome lungo in un
appuntamento di un'ora: deve andare a capo entro la card, con spazio sopra
l'orario e tra orario e nome. Nella vista settimana,
trascinare un appuntamento programmato su un altro orario o giorno: deve
mantenere la durata e aggiornarsi anche nel calendario Android. Provare una
fascia occupata: la card deve tornare al punto di partenza e comparire un
errore. Nelle viste giorno, 2 settimane e mese provare a trascinare una card: deve restare
ferma, senza lasciare copie grafiche. Tornando alla settimana, il trascinamento
deve funzionare ancora. Nella vista mese, controllare che anche i giorni pieni
mostrino tutti gli appuntamenti e che i nomi lunghi vadano a capo senza invadere
le celle vicine. Verificare che il ridimensionamento non sia disponibile
in nessuna vista e che trattamenti completati ed eventi tutto il giorno non si
possano trascinare. Le card mostrano il telefono per gli appuntamenti di almeno
90 minuti;
passando il puntatore si leggono nome, telefono e note completi.
Provare **Modifica** sullo stesso
appuntamento: cambiare orario e note, verificare il risultato anche nel legacy
e provare una fascia occupata. Provare **Elimina** su un appuntamento di test:
la conferma deve precedere la cancellazione e l'evento deve sparire dal
calendario. Aprire **Cestino**: nella scheda, **Torna al calendario** deve stare
a sinistra, **Svuota cestino** a destra e l'avviso dei 30 giorni sotto i pulsanti.
Ordinare per paziente e ripristinare l'evento;
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
In modalità locale `managementMode=manual`, con `serviceDirectory` configurata
e scrivibile dall'utente del backend, provare **Arresta servizio**, attendere
**Non attivo**, poi **Avvia servizio WhatsApp** e attendere il QR o
**Connesso**. Questi comandi agiscono sulla sessione WhatsApp centrale. Sul
server `managementMode=systemd` li nasconde; stato e QR restano disponibili.
Configurazione e verifica sono nella [guida WhatsApp](../docs/operations/whatsapp-baileys.md).
Chiudere e riaprire
il desktop: con backend attivo deve entrare automaticamente nello stesso account.
Premere **Logout**: la conferma deve richiedere la spunta della casella prima
di abilitare **Logout sicuro**. **Annulla** deve lasciare aperta la sessione.
Confermare, poi riaprire: deve comparire il login. Ripetere con un secondo
terapista e verificare che non riappaiano i dati del primo. Se il backend è
spento, l'app deve restare sul login senza mostrare dati precedenti.

Aprire **Rubrica**, cercare un paziente e cambiare l'ordinamento. Confrontare
l'elenco con la webapp legacy usando lo stesso terapista. Ripetere con un
secondo terapista: le schede dell'altro account non devono comparire.
Nelle finestre larghe l'ordinamento deve stare a sinistra, la barra di ricerca
al centro con la lente cliccabile al suo interno e **Nuovo paziente** a destra.
La ricerca deve funzionare sia con Invio sia con la lente. Nelle finestre
strette i controlli devono disporsi su più righe senza sovrapporsi. Il filtro
**Pazienti oggi**, se attivo, deve restare visibile sotto la barra.
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
non deve comparire. Trasformare un contatto in appuntamento e verificare che,
dopo il salvataggio, scompaia dalla lista d'attesa.

La password non viene salvata su disco. Il login crea un token revocabile di
30 giorni: Windows lo protegge con DPAPI nel profilo utente, Linux usa
Secret Service tramite `secret-tool`. Se l'archivio protetto non è disponibile,
il desktop segnala che l'accesso automatico non è attivo e la sessione corrente
resta utilizzabile. Dopo la conferma, Logout elimina il token locale e ne chiede la revoca al
backend; se la revoca fallisce viene mostrato un avviso. La ricerca dei pazienti resta nella rubrica;
la barra di ricerca globale non è presente nel desktop.

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
pacchetto. La build Windows non è eseguibile da Linux. L'app desktop usa
`http://127.0.0.1:8081` per impostazione predefinita; `FISIO_BACKEND_URL`
permette di indicare il backend HTTPS remoto.
L'installer non include il backend o il database e non modifica i dati.
