# Fisio e Sports Android

Client Android nativo in migrazione. Sono disponibili l'accesso del terapista,
la home con l'agenda di oggi, la rubrica e la scheda anagrafica del paziente
in sola lettura, e l'uscita. I dati arrivano dal backend; la webapp legacy
non viene caricata in una WebView. In alto a destra, il pulsante della sezione
corrente apre un menu sovrapposto con Home, Calendario, Rubrica, Storico, Dati,
Impostazioni ed Esci; la voce corrente resta selezionata. Le sezioni non ancora
migrate mostrano una schermata informativa.
Anamnesi, modifica paziente, calendario completo e le altre funzioni
arriveranno nei passi successivi.

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

Apri **Rubrica** dal menu di navigazione: devono comparire solo i pazienti
del terapista con nome, data di creazione e telefono. Cerca un nome e premi
Invio sulla tastiera; svuota il campo per tornare all'elenco completo. Dalla
cima della schermata trascina verso il basso per ricaricare la ricerca
corrente. Tocca tutta la riga di un paziente: la scheda mostra stato, telefono,
email, data di creazione e numero di appuntamenti collegati. Il tasto Indietro
torna alla Rubrica; aprendo il menu dalla scheda e chiudendolo, la scheda deve
restare visibile. Tocca il pulsante della sezione corrente in alto a destra:
il menu deve sovrapporsi alla pagina, selezionare la voce attiva e aggiornare
il pulsante quando cambi sezione. Calendario, Storico, Dati e Impostazioni
mostrano per ora una schermata di migrazione. Verifica che intestazione e
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

Il token di accesso resta soltanto in memoria. Chiudere o ricreare l'Activity
richiede un nuovo accesso; la persistenza sicura è un passo successivo.
L'uscita chiede al backend di revocare il token corrente quando la rete è
disponibile.
