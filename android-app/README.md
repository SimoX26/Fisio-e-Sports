# Fisio e Sports Android

Client Android nativo in migrazione. Sono disponibili l'accesso del terapista,
la home con l'agenda di oggi, la rubrica in sola lettura e l'uscita. I dati
arrivano dal backend; la webapp legacy non viene caricata in una WebView.
Scheda paziente, calendario completo e le altre funzioni arriveranno nei
passi successivi.

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
aggiornare l'agenda; prova anche una password errata e il pulsante Esci.

Apri **Rubrica** dalla barra Home/Rubrica: devono comparire solo i pazienti
del terapista con nome, data di creazione e telefono. Cerca un nome e premi
Invio sulla tastiera; svuota il campo per tornare all'elenco completo. Dalla
cima della schermata trascina verso il basso per ricaricare la ricerca
corrente. Torna a Home con la barra o con il tasto Indietro di Android.
Le schede paziente non sono ancora apribili in questo passo.

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
