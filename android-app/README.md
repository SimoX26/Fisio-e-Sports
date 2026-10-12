# Fisio e Sports Android

Client Android nativo in migrazione. Il primo flusso disponibile è l'accesso del
terapista tramite le API del backend, la visualizzazione dello username e
l'uscita. Non carica più la webapp legacy in una WebView. Home, pazienti,
calendario e le altre funzioni arriveranno nei passi successivi.

## Requisiti e configurazione

- JDK 17 e Android SDK con API 35;
- dispositivo Android 7.0 (API 24) o successivo, oppure emulatore;
- backend autonomo avviato e raggiungibile dal dispositivo.

Apri `android-app` in Android Studio. Se necessario, crea
`local.properties` da `local.properties.example` e imposta `sdk.dir`.
Il file locale non è versionato.

La build legge `FISIO_API_BASE_URL`. Il valore predefinito
`http://10.0.2.2:8081` punta al backend avviato sul computer host
dell'emulatore Android. Su un telefono fisico, usa l'indirizzo LAN del
computer, per esempio:

```bash
./gradlew assembleDebug -PFISIO_API_BASE_URL=http://192.168.1.50:8081
```

La URL deve essere l'origine delle API, senza il contesto
`/Fisio-e-Sports`. La build release accetta solo HTTPS. La build debug
consente HTTP per le prove sulla rete locale; evita reti non fidate durante
l'accesso perché le credenziali viaggiano in Basic.

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

Prima della prova verifica `http://<host>:8081/ready` dal dispositivo:
deve rispondere `{"status":"ok"}`. Avvia l'app, inserisci le credenziali
di un terapista esistente e verifica che compaia il suo username. Prova anche
una password errata e il pulsante Esci.

Il token di accesso resta soltanto in memoria. Chiudere o ricreare l'Activity
richiede un nuovo accesso; la persistenza sicura è un passo successivo.
L'uscita chiede al backend di revocare il token corrente quando la rete è
disponibile. L'app non contiene ancora le altre schermate.
