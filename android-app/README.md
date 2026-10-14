# Fisio e Sports Android

Client Android nativo in migrazione. Sono disponibili l'accesso del terapista,
la home con l'agenda di oggi, il calendario mensile, la rubrica, la scheda
anagrafica del paziente in sola lettura e l'uscita. I dati arrivano dal backend;
la webapp legacy non viene caricata in una WebView. In alto a destra, il pulsante della sezione
corrente apre un menu sovrapposto con Home, Calendario, Rubrica, Storico, Dati,
Impostazioni ed Esci; la voce corrente resta selezionata. Le sezioni non ancora
migrate mostrano una schermata informativa.
Anamnesi, modifica paziente e le altre azioni sugli appuntamenti arriveranno
nei passi successivi.

## Requisiti e configurazione

- JDK 17 e Android SDK con API 35;
- dispositivo Android 7.0 (API 24) o successivo, oppure emulatore;
- backend autonomo avviato e raggiungibile dal dispositivo.

Apri `android-app` in Android Studio. Se necessario, crea
`local.properties` da `local.properties.example` e imposta `sdk.dir`.
Il file locale non è versionato.

La build legge `FISIO_API_BASE_URL`. Il valore predefinito
`http://192.168.1.52:8081` punta al backend nella LAN di test. Se
l'indirizzo cambia, passane uno raggiungibile dal telefono:

```bash
./gradlew assembleDebug -PFISIO_API_BASE_URL=http://192.168.1.50:8081
```

La URL deve essere l'origine delle API, senza il contesto
`/Fisio-e-Sports`. La build release accetta solo HTTPS. La build debug
consente HTTP per le prove sulla rete locale; evita reti non fidate durante
l'accesso perché le credenziali viaggiano in Basic.
Per un emulatore Android con backend sul computer host, usa
`-PFISIO_API_BASE_URL=http://10.0.2.2:8081`.

## Build e prova manuale

Dalla directory `android-app`:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/FisioESport.apk
```

Oppure dalla radice del repository:

```bash
./deploy-apk.sh --android-url http://192.168.1.50:8081 --apk-output-dir /percorso/output
```

Prima della prova verifica `http://192.168.1.52:8081/ready` dal dispositivo:
deve rispondere `{"status":"ok"}`. Avvia l'app, inserisci le credenziali
di un terapista esistente e verifica che compaiano il suo username e gli
appuntamenti di oggi. Confronta orari, pazienti, stati e conteggi con il
desktop. Dall'inizio della home trascina la schermata verso il basso per
aggiornare l'agenda; prova anche una password errata ed Esci nel menu.

Apri **Calendario**: il mese corrente mostra un punto sui giorni con
appuntamenti. Tocca un giorno per vedere gli eventi e gli orari sotto la
griglia; usa le frecce per cambiare mese e **Oggi** per tornare alla data
attuale. Trascina verso il basso per aggiornare il mese. Confronta un giorno
con più appuntamenti, uno vuoto e un evento a cavallo di due giorni con il
desktop. Tocca la scheda di un appuntamento: si aprono orario, tipo, stato,
telefono e note; se è collegato a un paziente, **Dettagli paziente** apre la
sua scheda. Verifica anche un evento generico senza paziente. Nei dettagli,
**Modifica** apre lo stesso modulo con i dati precompilati per gli eventi
programmati di un solo giorno; per gli appuntamenti completati il pulsante
non compare. Cambia l'ora di inizio di un appuntamento di test: la fine deve
spostarsi automaticamente un'ora dopo, anche se cade nel giorno successivo.
Modifica il telefono e salva: verifica il nuovo orario sul desktop e il numero
aggiornato nella Rubrica Android e desktop.
**Elimina** chiede conferma e sposta l'appuntamento nel cestino; non compare
per un trattamento completato. Prova con un appuntamento di test: deve sparire
dal calendario Android. Apri **Cestino** e usa **Ripristina**: l'appuntamento
deve tornare nel calendario Android e sul desktop. Prova anche un ripristino
con fascia oraria occupata: l'appuntamento deve restare nel cestino.
L'eliminazione definitiva e lo svuotamento sono disponibili solo sul desktop.
**+ Nuovo** apre il modulo per creare
appuntamenti con paziente o eventi generici, anche tutto il giorno. Prova un
appuntamento di test con orari a scatti di 15 minuti e verifica che appaia
nel giorno scelto e sul desktop; verifica anche un orario occupato e gli
orari non validi. Gli eventi tutto il giorno con paziente richiedono un
paziente già presente, come nel desktop.
Gli eventi importati che attraversano più giorni restano per ora in sola
lettura nel client Android.
Il modulo usa una scheda chiara con campi arrotondati e azioni blu: prova a
scorrerlo fino a **Salva** con la tastiera aperta, e verifica che gli errori
restino leggibili senza chiudere il modulo.

Dopo un accesso riuscito, chiudi completamente l'app e riaprila: con backend
raggiungibile deve entrare senza chiedere la password. Ripeti la prova dopo
aver effettuato l'accesso anche dal desktop: entrambe le sessioni devono
restare utilizzabili. Esci da Android e riaprilo: deve mostrare il login,
mentre il desktop deve restare connesso.

Apri **Rubrica** dal menu di navigazione: devono comparire solo i pazienti
del terapista con nome, data di creazione e telefono. Cerca un nome e premi
Invio sulla tastiera oppure la lente nel campo; svuota il campo per tornare
all'elenco completo. La barra riprende il campo arrotondato del desktop, con
testo di ricerca leggibile e lente a destra. Dalla
cima della schermata trascina verso il basso per ricaricare la ricerca
corrente. Tocca tutta la riga di un paziente: la scheda mostra stato, telefono,
email, data di creazione e numero di appuntamenti collegati. Verifica che nome
e dati siano leggibili nelle schede chiare con titoli blu e separatori. Il tasto Indietro
torna alla Rubrica; aprendo il menu dalla scheda e chiudendolo, la scheda deve
restare visibile. Tocca il pulsante della sezione corrente in alto a destra:
il menu deve sovrapporsi alla pagina, selezionare la voce attiva e aggiornare
il pulsante quando cambi sezione. La tendina usa la stessa palette delle
schede, evidenzia la voce selezionata e mostra **Esci** in rosso. Storico,
Dati e Impostazioni mostrano per ora
una schermata di migrazione. Verifica che intestazione e
contenuto inizino sotto la barra delle notifiche e restino sopra la barra di
navigazione Android. Esci deve comparire soltanto nel menu; dopo l'uscita il
pulsante della sezione non deve più comparire.

Login, home e rubrica usano la stessa palette del desktop: sfondo grigio chiaro, schede
bianche con bordo, azioni blu e stati completato/cancellato in verde/rosso.
I controlli restano nativi Android.

I conteggi della home seguono il desktop: appuntamenti restituiti dall'API,
pazienti distinti associati a quegli appuntamenti e promemoria ancora
potenzialmente inviabili per appuntamenti futuri di oggi, non cancellati e
con paziente. La home non apre ancora i dettagli né modifica gli appuntamenti.

La password non viene salvata. Android cifra il token nel proprio archivio
privato usando Android Keystore e lo separa per URL del backend; il backup
dell'app è disabilitato perché la chiave non è trasferibile. Alla riapertura
verifica la sessione con `/api/me`: se il token è scaduto o revocato torna
al login. Se il backend non è raggiungibile, resta sul login e conserva il
token per un tentativo successivo. Esci elimina il token locale e chiede al
backend di revocarlo quando la rete è disponibile. Il token scade dopo 30
giorni; ogni dispositivo ha una sessione distinta.
