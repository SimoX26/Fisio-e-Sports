# Sincronizzazione locale

## Obiettivo e decisioni

Desktop e Android conserveranno una copia locale dei dati del terapista. Le
modifiche saranno salvate prima sul dispositivo e inviate automaticamente al
backend quando la connessione torna disponibile. Il backend resta la fonte
condivisa tra dispositivi e scrive nel database centrale. Nessun client accede
direttamente a MySQL.

Se due dispositivi modificano la stessa entità, vince l'ultima modifica
**accettata dal backend**, nell'ordine delle transazioni server; l'orologio del
dispositivo non decide l'ordine. Una modifica inviata più tardi può quindi
sovrascrivere quella precedente. Errori di autorizzazione o di validazione non
sono conflitti: l'operazione resta visibile come non sincronizzata e non viene
scartata automaticamente.

## Contratto da implementare

1. Il backend espone una fotografia iniziale completa per il terapista
   autenticato e un flusso delle modifiche successive, comprese le
   eliminazioni. Ogni modifica ha un cursore server monotono; il client salva
   i dati e il cursore nella stessa transazione locale.
2. Ogni client mantiene una coda persistente di operazioni con identificatore
   univoco. Il backend applica ogni identificatore al massimo una volta e
   restituisce l'esito: i tentativi dopo timeout non creano duplicati.
3. All'avvio e al ritorno della connessione il client invia le operazioni
   pendenti nell'ordine locale, poi scarica le modifiche server. Le operazioni
   dipendenti, per esempio appuntamento e nuovo paziente, rispettano i
   riferimenti. Una risposta non accettata resta in coda con errore leggibile.
4. L'identità del terapista deriva sempre dalla sessione verificata dal
   backend. Le copie locali e le code sono separate per origine backend e
   account. Il logout o la scadenza del token non devono cancellare modifiche
   locali ancora da inviare senza un'azione esplicita dell'utente.
5. I dati clinici locali sono cifrati a riposo. Android usa Android Keystore;
   il desktop usa una chiave derivata dal token protetto dal sistema operativo
   e un file cifrato. La WebView non conserva dati clinici in `localStorage`.

Le API correnti non offrono ancora cursori, eliminazioni incrementali,
idempotenza o invio di operazioni offline. Un semplice salvataggio di risposte
HTTP non costituisce la sincronizzazione descritta sopra. Prima di accodare
scritture occorrono il contratto backend, le migrazioni versionate e prove con
due dispositivi e interruzioni di rete.

## Passo 1: Home, Calendario e Rubrica in sola lettura

Dopo un login online, l'apertura della rubrica senza filtro salva l'elenco
completo in un archivio cifrato, insieme all'identità e all'ora del download.
Se il backend è irraggiungibile, l'app riapre la sessione già salvata solo se
esiste una copia e mostra Home, Calendario e Rubrica dal dispositivo per i dati
disponibili. La ricerca per nome
funziona localmente. La scheda paziente mostra solo i dati già presenti
nell'elenco (nome, telefono e data): è marcata come parziale e non modificabile.
Una risposta del backend diversa da un errore di rete non attiva il fallback.
Al ritorno della connessione, un nuovo caricamento della rubrica sostituisce
la copia locale. Logout e sessione rifiutata dal backend eliminano la copia di
questo primo passo. Non esistono ancora modifiche locali da preservare.
Una revoca avvenuta mentre il dispositivo è offline può essere rilevata solo
quando torna la connessione.

Il desktop salva l'elenco senza filtro nel proprio file cifrato, separato per
backend e leggibile solo con lo stesso token protetto. Quando il backend non
risponde, apre la rubrica con ricerca per nome e ordinamento locali; la scheda
mostra solo nome, telefono e data, senza azioni di modifica. Il filtro per
data non è disponibile offline. Logout elimina il file. Un nuovo login che
genera un token diverso richiede un nuovo download della rubrica.

Home conserva l'agenda del giorno e, sul desktop, la lista di attesa. Il
Calendario conserva i periodi già visitati. Dopo l'accesso online entrambi i
client scaricano anche il mese precedente, quello corrente e il successivo:
il desktop usa tre richieste entro il limite di 62 giorni dell'API, Android tre
richieste.
La Home del giorno seguente usa gli eventi del calendario salvato, se il
periodo è coperto; un mese fuori dalla copia non viene presentato come vuoto,
ma mostra un errore di caricamento.
Tutti i dati sono ancora solo in lettura quando il backend non risponde.
L'interfaccia ritenta le richieste quando si apre o aggiorna una schermata e
sostituisce la copia dopo una risposta valida. Non esiste ancora una coda di
modifiche né una sincronizzazione incrementale.
Prima di introdurre modifiche offline, la coda dovrà usare una chiave locale
stabile e distinta dal token: il rinnovo della sessione non deve rendere
inaccessibile lavoro ancora da inviare.

## Passo 2: esportazione iniziale dal backend

`GET /api/offline/snapshot` richiede la sessione del terapista. Restituisce
`version: 1`, `generatedAt`, tutti i pazienti assegnati, l'ultima anamnesi e le
relative condizioni per ciascuno, tutti gli appuntamenti (anche cancellati),
piani e sedute con i dati clinici completi, e la lista di attesa. Gli ID del
terapista non sono accettati dal client; i riferimenti verso pazienti e
appuntamenti fuori dall'account vengono inviati come `null`, conservando le
sedute e gli appuntamenti appartenenti al terapista. L'esportazione è in sola
lettura e non modifica il database.

La risposta è ottenuta tramite più letture: `generatedAt` non è ancora un
cursore né garantisce una fotografia transazionale. Android salva la risposta
dopo l'accesso online in SQLite, separata per backend e account. Ogni elemento
clinico è cifrato con AES-GCM usando una chiave distinta in Android Keystore;
SQLite conserva in chiaro solo tipo e posizione delle righe e l'ora del download.
La sostituzione è atomica: un download o una cifratura falliti lasciano la copia
precedente. Lo Storico Android legge piani, sedute e nomi dei pazienti da questa
copia anche offline. Il refresh si può avviare trascinando la schermata Storico
verso il basso. Logout e revoca rilevata eliminano la copia dell'account.
Le altre sezioni continuano a usare le cache precedenti; le scritture restano
online. La coda di scrittura richiederà cursore server e idempotenza.

Per il collaudo sul database di test, riavvia il backend aggiornato e verifica
prima che `GET /api/offline/snapshot` senza credenziali risponda 401. Poi usa
un account terapista; `curl -u marco` chiede la password senza scriverla nel
comando. Per controllare soltanto le quantità, senza salvare i dati clinici in
chiaro su disco:

```sh
curl -fsS -u marco http://127.0.0.1:8081/api/offline/snapshot \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print({k: len(d[k]) for k in ("patients","anamneses","appointments","treatmentPlans","treatmentSessions","waitlist")})'
```

Confronta in particolare `treatmentSessions` con i dati del DB e prova un
secondo terapista: le due risposte devono contenere solo le rispettive schede.

## Passi successivi

1. Completare la lettura locale della fotografia su Android e desktop per le
   altre sezioni; poi aggiornamenti incrementali e gestione delle eliminazioni.
2. Coda di scrittura con invio automatico e deduplicazione sul backend;
   applicazione della regola «ultima modifica inviata vince» e stato di
   sincronizzazione visibile su entrambi i client.

Ogni passo va collaudato manualmente prima di iniziare il successivo.
